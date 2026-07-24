/*
 * Aetherium Framework — in-place directory protection + CLI/Gradle entry point.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Applies the {@link Shield} to every {@code .class} in a compiled-output directory — the engine behind both
 * the CLI {@code protect} command and the Gradle {@code aetheriumShield} task, and its own {@link #main}.
 *
 * <p>EN: Classes named in {@code META-INF/services} files are auto-kept so {@code ServiceLoader} entrypoints
 * keep their names. Renaming is opt-in; off (the safe default) protects in place with stable paths. The
 * directory itself is the frame-computation classpath so control-flow obfuscation can recompute frames; a
 * class whose frames cannot be recomputed (e.g. it references Minecraft types not on the classpath) simply
 * reverts to its original bytes and ships un-obfuscated — the build never breaks.
 * RU: Классы из {@code META-INF/services} автоматически сохраняются. Переименование — по желанию; при
 * выключенном (безопасно по умолчанию) защита на месте со стабильными путями. Класс, чьи кадры нельзя
 * пересчитать, откатывается к оригиналу — сборка не ломается.
 */
public final class ShieldDirectory {

    private ShieldDirectory() {
    }

    /** {@code main} for the Gradle {@code aetheriumShield} JavaExec task: {@code <dir> [author] [--rename]}. */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: ShieldDirectory <classesDir> [author] [--rename]");
            System.exit(2);
            return;
        }
        Path dir = Path.of(args[0]);
        String author = args.length >= 2 && !args[1].startsWith("--") ? args[1] : "";
        boolean rename = false;
        for (String a : args) {
            if ("--rename".equals(a)) {
                rename = true;
            }
        }
        protect(dir, author, rename);
    }

    /**
     * Protect every {@code .class} under {@code dir} in place.
     *
     * @return a short human-readable summary
     */
    public static Summary protect(Path dir, String author, boolean rename) throws IOException {
        if (!Files.isDirectory(dir)) {
            System.out.println("  shield: '" + dir + "' is not a directory — skipping.");
            return new Summary(0, 0, 0, dir);
        }
        Map<String, byte[]> byBinary = new LinkedHashMap<>();
        Map<String, Path> pathOf = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".class"))::iterator) {
                byte[] bytes = Files.readAllBytes(p);
                String binary = new ClassReader(bytes).getClassName().replace('/', '.');
                byBinary.put(binary, bytes);
                pathOf.put(binary, p);
            }
        }
        if (byBinary.isEmpty()) {
            System.out.println("  shield: no .class files under " + dir + " — nothing to protect.");
            return new Summary(0, 0, 0, dir);
        }

        KeepList keep = new KeepList();
        List<String> keptServices = collectServiceImpls(dir);
        keptServices.forEach(keep::keepService);

        ShieldOptions options = new ShieldOptions(true, true, true, rename, rename, true, author);

        try (URLClassLoader verifyLoader = new URLClassLoader(new URL[]{dir.toUri().toURL()},
                ShieldDirectory.class.getClassLoader())) {
            Shield.Result result = Shield.protect(byBinary, options, keep, verifyLoader);

            int written = 0;
            for (Map.Entry<String, byte[]> e : result.protectedClasses().entrySet()) {
                Path target = rename ? dir.resolve(e.getKey().replace('.', '/') + ".class") : pathOf.get(e.getKey());
                if (target == null) {
                    target = dir.resolve(e.getKey().replace('.', '/') + ".class");
                }
                Files.createDirectories(target.getParent());
                Files.write(target, e.getValue());
                written++;
            }
            if (rename) {
                for (Map.Entry<String, String> ren : result.classRenames().entrySet()) {
                    if (!ren.getKey().equals(ren.getValue())) {
                        Files.deleteIfExists(pathOf.get(ren.getKey()));
                    }
                }
            }

            Path manifest = dir.resolve("META-INF/aetherium/shield-integrity.txt");
            Files.createDirectories(manifest.getParent());
            Files.writeString(manifest, result.integrity().serialize(), StandardCharsets.UTF_8);

            System.out.printf("  aetherium-shield: protected %d class(es); %d kept by name; %d reverted (shipped as-is).%n",
                    written, keptServices.size(), result.revertedClasses());
            System.out.printf("  aetherium-shield: integrity manifest %s (%d entries)%s%n",
                    manifest.getFileName(), result.integrity().size(),
                    author.isBlank() ? "" : "; watermark author=" + author);
            return new Summary(written, keptServices.size(), result.revertedClasses(), dir);
        }
    }

    private static List<String> collectServiceImpls(Path dir) {
        Path services = dir.resolve("META-INF/services");
        List<String> impls = new ArrayList<>();
        if (!Files.isDirectory(services)) {
            return impls;
        }
        try (Stream<Path> files = Files.list(services)) {
            for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) {
                        impls.add(t);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return impls;
    }

    /** A short protection summary. */
    public record Summary(int protectedClasses, int keptByName, int reverted, Path dir) {
    }
}
