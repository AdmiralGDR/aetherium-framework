/*
 * Aetherium Framework — static bytecode analyzer.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.bytecode.analyze;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Statically verifies mod bytecode against target-loader constraints — backs {@code aetherium-cli analyze}.
 *
 * <p>EN: Reads a {@code .class} file or a {@code .jar} (scanning every class entry) and, per class,
 * records its name, class-file major version, whether that version exceeds the target, and whether
 * ASM's verifier accepts it. Public types expose only primitives/strings so the CLI need not depend
 * on ASM. Pure read-only analysis — it never executes or defines a class.
 *
 * <p>RU: Читает файл {@code .class} или {@code .jar} (просматривая каждый класс) и по каждому классу
 * фиксирует имя, мажорную версию class-файла, превышает ли она целевую, и принимает ли его
 * верификатор ASM. Публичные типы используют только примитивы/строки, чтобы CLI не зависел от ASM.
 * Анализ только для чтения — класс не исполняется и не определяется.
 */
public final class BytecodeAnalyzer {

    /** Java 21 == class-file major version 65 (the framework baseline). */
    public static final int DEFAULT_TARGET_MAJOR = 65;

    private BytecodeAnalyzer() {
    }

    /** Per-class analysis result. {@code verifyError} is blank when the class verifies cleanly. */
    public record ClassResult(String className, int majorVersion, boolean versionOk, boolean verifyOk,
                              String verifyError) {
    }

    /** Whole-input report. */
    public record AnalysisReport(String source, int targetMajor, List<ClassResult> classes,
                                int okCount, int problemCount) {
        public boolean clean() {
            return problemCount == 0;
        }
    }

    /** Analyze a path (a {@code .class}, a {@code .jar}, or a directory of classes) at the default target. */
    public static AnalysisReport analyze(Path path) throws IOException {
        return analyze(path, DEFAULT_TARGET_MAJOR);
    }

    /** Analyze a path against an explicit target major version (no extra verification classpath). */
    public static AnalysisReport analyze(Path path, int targetMajor) throws IOException {
        return analyze(path, targetMajor, null);
    }

    /**
     * Analyze a path, resolving referenced types through {@code verifyLoader} during verification.
     *
     * <p>EN: Passing the game classpath (e.g. via {@code aetherium-cli analyze --classpath …}) lets
     * the verifier resolve vanilla {@code net.minecraft} types, eliminating <strong>false
     * positives</strong> on classes that legitimately reference game types. With {@code null}, the
     * structural-only checks still run but unresolved types are not flagged as errors.
     */
    public static AnalysisReport analyze(Path path, int targetMajor, ClassLoader verifyLoader) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            throw new IOException("Path does not exist: " + path);
        }

        List<ClassResult> results = new ArrayList<>();
        String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();

        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                for (Path p : (Iterable<Path>) stream.filter(f -> f.toString().endsWith(".class"))::iterator) {
                    results.add(analyzeClassBytes(Files.readAllBytes(p), targetMajor, verifyLoader));
                }
            }
        } else if (name.endsWith(".jar") || name.endsWith(".zip")) {
            analyzeArchive(path, targetMajor, results, verifyLoader);
        } else if (name.endsWith(".class")) {
            results.add(analyzeClassBytes(Files.readAllBytes(path), targetMajor, verifyLoader));
        } else {
            throw new IOException("Unsupported input (expected .class, .jar, or directory): " + path);
        }

        int problems = (int) results.stream().filter(r -> !r.versionOk() || !r.verifyOk()).count();
        return new AnalysisReport(path.toString(), targetMajor, List.copyOf(results),
                results.size() - problems, problems);
    }

    private static void analyzeArchive(Path jar, int targetMajor, List<ClassResult> results,
                                       ClassLoader verifyLoader) throws IOException {
        try (InputStream fileIn = Files.newInputStream(jar);
             ZipInputStream zip = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                results.add(analyzeClassBytes(zip.readAllBytes(), targetMajor, verifyLoader));
            }
        }
    }

    private static ClassResult analyzeClassBytes(byte[] bytes, int targetMajor, ClassLoader verifyLoader) {
        // ClassReader can throw on malformed input; report it as a problem rather than propagating.
        final String className;
        final int major;
        try {
            ClassReader reader = new ClassReader(bytes);
            className = reader.getClassName();
            // Bytes 6-7 are the class-file major version (big-endian), after magic(4) + minor(2).
            major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        } catch (RuntimeException malformed) {
            return new ClassResult("<unreadable>", -1, false, false,
                    "malformed class: " + malformed.getClass().getSimpleName());
        }

        boolean versionOk = major <= targetMajor;

        String verifyError = "";
        boolean verifyOk;
        StringWriter buffer = new StringWriter();
        try (PrintWriter out = new PrintWriter(buffer)) {
            if (verifyLoader != null) {
                // Resolve referenced types (incl. vanilla game classes) against the provided
                // classpath, so legitimate references don't show up as false positives.
                CheckClassAdapter.verify(new ClassReader(bytes), verifyLoader, false, out);
            } else {
                CheckClassAdapter.verify(new ClassReader(bytes), false, out);
            }
            String text = buffer.toString();
            verifyOk = text.isBlank();
            if (!verifyOk) {
                verifyError = firstLine(text);
            }
        } catch (Throwable verifierError) {
            verifyOk = false;
            verifyError = "verifier error: " + verifierError.getClass().getSimpleName();
        }

        return new ClassResult(className, major, versionOk, verifyOk, verifyError);
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        String line = (nl >= 0 ? text.substring(0, nl) : text).strip();
        return line.length() > 160 ? line.substring(0, 160) + "…" : line;
    }
}
