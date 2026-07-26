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

    /**
     * {@code main} for the Gradle {@code aetheriumShield} JavaExec task:
     * {@code <classesDir> [author] [--rename] [--classpath <cp>] [--out <dir>]}. With {@code --out}, the
     * source classes dir is mirrored to a task-owned output dir and protected THERE — the compile output is
     * never mutated, so {@code ./gradlew build} is repeatable and the task is incremental/cacheable
     * (). Without it, protection is in place (the CLI {@code protect} command). {@code --classpath}
     * (the mod's runtime classpath) lets control-flow obfuscation recompute frames so fewer classes revert.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: ShieldDirectory <classesDir> [author] [--rename] [--classpath <cp>] [--out <dir>]");
            System.exit(2);
            return;
        }
        Path dir = Path.of(args[0]);
        String author = args.length >= 2 && !args[1].startsWith("--") ? args[1] : "";
        boolean rename = false;
        List<String> classpath = List.of();
        Path outDir = null;
        for (int i = 0; i < args.length; i++) {
            if ("--rename".equals(args[i])) {
                rename = true;
            } else if ("--classpath".equals(args[i]) && i + 1 < args.length) {
                classpath = List.of(args[++i].split(java.io.File.pathSeparator));
            } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                outDir = Path.of(args[++i]);
            }
        }
        if (outDir != null) {
            protect(dir, outDir, author, rename, classpath);
        } else {
            protect(dir, author, rename, classpath);
        }
    }

    /**
     * Protect the classes under {@code srcDir} into a SEPARATE {@code outDir} (mirroring first), so the source
     * compile output is never mutated — the correct, incremental, cacheable design (). Packaging
     * consumes {@code outDir}.
     */
    public static Summary protect(Path srcDir, Path outDir, String author, boolean rename, List<String> classpath)
            throws IOException {
        if (!outDir.toAbsolutePath().normalize().equals(srcDir.toAbsolutePath().normalize())) {
            mirror(srcDir, outDir);
        }
        return protect(outDir, author, rename, classpath);
    }

    /** Clean {@code outDir} and copy the whole {@code srcDir} tree into it (a fresh, complete mirror). */
    private static void mirror(Path srcDir, Path outDir) throws IOException {
        if (Files.exists(outDir)) {
            try (Stream<Path> walk = Files.walk(outDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort clean
                    }
                });
            }
        }
        Files.createDirectories(outDir);
        try (Stream<Path> walk = Files.walk(srcDir)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path rel = srcDir.relativize(p);
                Path target = outDir.resolve(rel);
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Protect every {@code .class} under {@code dir} in place (no extra verify classpath). */
    public static Summary protect(Path dir, String author, boolean rename) throws IOException {
        return protect(dir, author, rename, List.of());
    }

    /**
     * Protect every {@code .class} under {@code dir} in place, using {@code classpath} (plus {@code dir}) for
     * frame-computation/verification so classes referencing Minecraft/framework types don't revert.
     *
     * @return a short human-readable summary
     */
    public static Summary protect(Path dir, String author, boolean rename, List<String> classpath)
            throws IOException {
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

        ShieldOptions options = new ShieldOptions(true, true, true, true, true, true, rename, rename, true, author);

        try (URLClassLoader verifyLoader = new URLClassLoader(verifyUrls(dir, classpath),
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

            // Rewrite name-based text registries (content.index / behaviors.index / any *.index) through the
            // rename map — otherwise renaming produces a green build with a broken jar (a name-based index
            // still points at the pre-rename class → ClassNotFoundException at registration). See feedback 
            int rewritten = rewriteIndices(dir, result.classRenames());

            Path manifest = dir.resolve("META-INF/aetherium/shield-integrity.txt");
            Files.createDirectories(manifest.getParent());
            Files.writeString(manifest, result.integrity().serialize(), StandardCharsets.UTF_8);

            System.out.printf("  aetherium-shield: protected %d class(es); %d kept by name; %d reverted (shipped as-is).%n",
                    written, keptServices.size(), result.revertedClasses());
            System.out.printf("  aetherium-shield: integrity manifest %s (%d entries)%s%n",
                    manifest.getFileName(), result.integrity().size(),
                    author.isBlank() ? "" : "; watermark author=" + author);
            if (rewritten > 0) {
                System.out.printf("  aetherium-shield: remapped %d name-based index reference(s) after rename.%n",
                        rewritten);
            }
            return new Summary(written, keptServices.size(), result.revertedClasses(), dir);
        }
    }

    /**
     * Rewrite every {@code META-INF/aetherium/*.index} file so any pipe-delimited field naming a renamed
     * class is updated to its new binary name. Schema-agnostic (a field is remapped iff it exactly equals a
     * renamed old class name), so it covers {@code content.index} and {@code behaviors.index} alike.
     *
     * @return the number of individual field references remapped
     * @throws IllegalStateException if, after the rewrite, an index still names a class that was renamed away
     *                               (a loud build failure — never a broken jar with a green build)
     */
    private static int rewriteIndices(Path dir, Map<String, String> classRenames) throws IOException {
        Path indexDir = dir.resolve("META-INF/aetherium");
        if (!Files.isDirectory(indexDir)) {
            return 0;
        }
        int remapped = 0;
        try (Stream<Path> files = Files.list(indexDir)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".index"))::iterator) {
                List<String> in = Files.readAllLines(f, StandardCharsets.UTF_8);
                List<String> out = new ArrayList<>(in.size());
                boolean changed = false;
                for (String line : in) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        out.add(line);
                        continue;
                    }
                    String[] fields = line.split("\\|", -1);
                    for (int i = 0; i < fields.length; i++) {
                        String mapped = classRenames.get(fields[i]);
                        if (mapped != null && !mapped.equals(fields[i])) {
                            fields[i] = mapped;
                            remapped++;
                            changed = true;
                        }
                        // Fail-loud guard: a field that is a renamed-away key must never survive the rewrite.
                        String recheck = classRenames.get(fields[i]);
                        if (recheck != null && !recheck.equals(fields[i])) {
                            throw new IllegalStateException("aetherium-shield: index '" + f.getFileName()
                                    + "' still references renamed class '" + fields[i] + "' after remap — "
                                    + "refusing to ship a broken jar.");
                        }
                    }
                    out.add(String.join("|", fields));
                }
                if (changed) {
                    Files.write(f, out);
                }
            }
        }
        return remapped;
    }

    /** Build the verify class loader URLs: the output dir first, then every runtime-classpath entry. */
    private static URL[] verifyUrls(Path dir, List<String> classpath) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(dir.toUri().toURL());
        for (String entry : classpath) {
            if (entry != null && !entry.isBlank()) {
                urls.add(Path.of(entry).toUri().toURL());
            }
        }
        return urls.toArray(new URL[0]);
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
