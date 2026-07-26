/*
 * Aetherium Framework — build-time boot-layer relocator ().
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.bytecode.relocate;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shades the boot-layer jar's embedded framework copy into a private prefix — the fix for the 
 * crash where {@code aetherium-transformer} (loose {@code org/aetherium/{core,bytecode,injector}}) and
 * {@code aetherium-loader}'s nested Jar-in-Jar copies of the same modules both became modules exporting the
 * same packages (a {@code ResolutionException} before the window).
 *
 * <p>EN: A tiny CLI over {@link ClassRelocator}, run as a forked build step (the same pattern the Shield
 * uses). It reads every {@code .class} from the given inputs (dirs or jars), skips excluded prefixes (the
 * FFM/preview packages that must never enter the boot layer), relocates each per the rules, and writes it to
 * {@code outDir} at its <em>new</em> internal name — so embedded {@code org/aetherium/core/…} lands at
 * {@code org/aetherium/boot/core/…}, while the transformer's own {@code org/aetherium/transformer/…} classes
 * keep their path but get their references rewritten. Deterministic, so the jar stays byte-reproducible.
 * RU: Крошечный CLI над {@link ClassRelocator}, запускаемый форк-шагом сборки (тот же приём, что у Щита).
 * Читает каждый {@code .class} из входов (каталоги/jar), пропускает исключённые префиксы (FFM/preview,
 * которым нельзя в boot-слой), релоцирует по правилам и пишет под <em>новым</em> внутренним именем — так
 * встроенное {@code org/aetherium/core/…} попадает в {@code org/aetherium/boot/core/…}, а собственные классы
 * трансформера сохраняют путь, но их ссылки переписываются. Детерминировано → jar воспроизводим.
 */
public final class BootRelocator {

    private BootRelocator() {
    }

    /** {@code BootRelocator <outDir> <relocationsCsv> <excludeCsv> <input...>} */
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("usage: BootRelocator <outDir> <from:to,...> <excludePrefix,...> <input...>");
            System.exit(2);
            return;
        }
        Path outDir = Path.of(args[0]);
        ClassRelocator relocator = new ClassRelocator(parseRelocations(args[1]));
        List<String> excludes = new ArrayList<>();
        for (String e : args[2].split(",")) {
            String t = e.trim().replace('.', '/');
            if (!t.isEmpty()) {
                excludes.add(t);
            }
        }
        Files.createDirectories(outDir);
        int written = 0;
        for (int i = 3; i < args.length; i++) {
            written += processInput(Path.of(args[i]), relocator, excludes, outDir);
        }
        System.out.println("BootRelocator: relocated " + written + " class(es) into " + outDir);
    }

    private static List<Relocation> parseRelocations(String csv) {
        List<Relocation> list = new ArrayList<>();
        for (String rule : csv.split(",")) {
            String[] parts = rule.split(":");
            if (parts.length == 2) {
                list.add(Relocation.ofPackages(parts[0].trim(), parts[1].trim()));
            }
        }
        return list;
    }

    private static int processInput(Path input, ClassRelocator relocator, List<String> excludes, Path outDir)
            throws IOException {
        if (!Files.exists(input)) {
            return 0;
        }
        int n = 0;
        if (Files.isDirectory(input)) {
            try (Stream<Path> walk = Files.walk(input)) {
                for (Path p : (Iterable<Path>) walk.filter(x -> x.toString().endsWith(".class"))::iterator) {
                    if (writeRelocated(Files.readAllBytes(p), relocator, excludes, outDir)) {
                        n++;
                    }
                }
            }
        } else if (input.toString().endsWith(".jar")) {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(input))) {
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    if (e.getName().endsWith(".class")) {
                        if (writeRelocated(zis.readAllBytes(), relocator, excludes, outDir)) {
                            n++;
                        }
                    }
                }
            }
        }
        return n;
    }

    private static boolean writeRelocated(byte[] original, ClassRelocator relocator, List<String> excludes,
            Path outDir) {
        String originalName = new ClassReader(original).getClassName();
        for (String ex : excludes) {
            if (originalName.startsWith(ex)) {
                return false; // e.g. the FFM/preview packages — never relocate them into the boot layer
            }
        }
        byte[] relocated = relocator.relocate(original);
        String newName = new ClassReader(relocated).getClassName();
        Path target = outDir.resolve(newName + ".class");
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, relocated);
        } catch (IOException io) {
            throw new UncheckedIOException("Failed to write relocated class " + newName, io);
        }
        return true;
    }
}
