/*
 * Aetherium Framework — dependency flattening / deduplication.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import org.aetherium.bytecode.relocate.ClassRelocator;
import org.aetherium.bytecode.relocate.Relocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Resolves "Library Hell": deduplicates embedded mod libraries to one version per artifact.
 *
 * <p>EN: Minecraft mods each shade their own copies of common libraries (Kotlin stdlib, Guava,
 * Jackson, …) at conflicting versions, bloating memory and risking {@code LinkageError}s when two
 * copies of the same class load. This flattener takes the union of every mod's embedded libraries
 * and picks <strong>one winner</strong> per {@code group:artifact} — the highest version by semantic
 * comparison — producing a flattened set plus a conflict log. A class-load filter (consulted by the
 * launch plugin) then admits only the winner's classes, so a single instance of each library exists
 * in the JVM. This component is pure and loader-only (no ModLauncher/Minecraft types) so it is fully
 * unit-testable.
 *
 * <p>RU: Решает «Library Hell»: дедуплицирует встроенные библиотеки модов до одной версии на
 * артефакт. Каждый мод Minecraft встраивает свои копии общих библиотек (Kotlin stdlib, Guava,
 * Jackson, …) конфликтующих версий, раздувая память и рискуя {@code LinkageError} при загрузке двух
 * копий одного класса. Этот flattener берёт объединение встроенных библиотек всех модов и выбирает
 * <strong>один победный</strong> на {@code group:artifact} — наивысшую версию по семантическому
 * сравнению — формируя плоский набор и журнал конфликтов. Фильтр загрузки классов (используемый
 * launch-plugin) затем допускает только классы победителя, поэтому в JVM существует один экземпляр
 * каждой библиотеки. Компонент чист и только в loader (без типов ModLauncher/Minecraft), полностью
 * юнит-тестируем.
 */
public final class DependencyFlattener {

    private DependencyFlattener() {
    }

    /** A library embedded by some mod. */
    public record LibraryRef(String group, String artifact, String version, String sourceMod) {
        public LibraryRef {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(sourceMod, "sourceMod");
        }

        public String coordinate() {
            return group + ":" + artifact;
        }
    }

    /** A resolved conflict: which version won and which were superseded. */
    public record Conflict(String coordinate, String winner, List<String> superseded) {
    }

    /** The flattened outcome. */
    public record FlattenResult(List<LibraryRef> winners, List<Conflict> conflicts, int inputCount) {
        public int dedupedCount() {
            return inputCount - winners.size();
        }
    }

    /**
     * Flatten a collection of embedded libraries to one winner per {@code group:artifact}.
     * Highest version wins; ties keep the first seen.
     */
    public static FlattenResult flatten(List<LibraryRef> libraries) {
        Objects.requireNonNull(libraries, "libraries");

        Map<String, LibraryRef> winners = new LinkedHashMap<>();
        Map<String, List<LibraryRef>> all = new LinkedHashMap<>();

        for (LibraryRef lib : libraries) {
            all.computeIfAbsent(lib.coordinate(), k -> new ArrayList<>()).add(lib);
            LibraryRef current = winners.get(lib.coordinate());
            if (current == null || compareVersions(lib.version(), current.version()) > 0) {
                winners.put(lib.coordinate(), lib);
            }
        }

        List<Conflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<LibraryRef>> entry : all.entrySet()) {
            List<LibraryRef> candidates = entry.getValue();
            if (candidates.size() > 1) {
                LibraryRef winner = winners.get(entry.getKey());
                List<String> superseded = new ArrayList<>();
                for (LibraryRef c : candidates) {
                    if (!c.version().equals(winner.version())) {
                        superseded.add(c.version() + " (from " + c.sourceMod() + ")");
                    }
                }
                if (!superseded.isEmpty()) {
                    conflicts.add(new Conflict(entry.getKey(),
                            winner.version() + " (from " + winner.sourceMod() + ")", superseded));
                }
            }
        }

        return new FlattenResult(List.copyOf(winners.values()), List.copyOf(conflicts), libraries.size());
    }

    private static final Pattern NON_NUMERIC = Pattern.compile("[^0-9]");

    /**
     * Compare two version strings by dotted numeric segments (e.g. {@code 1.9.24} vs {@code 1.8.10}).
     * Non-numeric suffixes are ignored for ordering; longer-but-equal-prefix wins.
     */
    static int compareVersions(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int av = i < as.length ? parseSegment(as[i]) : 0;
            int bv = i < bs.length ? parseSegment(bs[i]) : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

    private static int parseSegment(String segment) {
        String digits = NON_NUMERIC.matcher(segment).replaceAll("");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException overflow) {
            return Integer.MAX_VALUE;
        }
    }

    /** A reusable comparator over {@link LibraryRef} by version (ascending). */
    public static Comparator<LibraryRef> byVersion() {
        return (x, y) -> compareVersions(x.version(), y.version());
    }

    // --- Namespace relocation (Library Shadowing) ------------------------------------------------

    /** Default relocation root for shaded libraries. */
    public static final String SHADOW_ROOT = "org.aetherium.shadow";

    /**
     * Curated relocations for the libraries mods most often shade into conflict. Each moves a
     * library's package under {@link #SHADOW_ROOT} so two mods embedding different versions never
     * clash. ASM-based, applied via {@link #relocate}.
     */
    public static List<Relocation> commonLibraryRelocations() {
        return commonLibraryRelocations(SHADOW_ROOT);
    }

    public static List<Relocation> commonLibraryRelocations(String shadowRootPackage) {
        String root = shadowRootPackage.replace('.', '/');
        return List.of(
                new Relocation("com/google/common", root + "/guava"),
                new Relocation("com/google/gson", root + "/gson"),
                new Relocation("kotlin", root + "/kotlin"),
                new Relocation("com/fasterxml/jackson", root + "/jackson"),
                new Relocation("org/apache/commons", root + "/commons"),
                new Relocation("it/unimi/dsi/fastutil", root + "/fastutil"));
    }

    /**
     * Apply ASM-based namespace relocation to class bytes. Delegates to {@code aetherium-bytecode}'s
     * {@link ClassRelocator} — the loader never touches ASM directly. EN/RU: enforces Library
     * Shadowing so a flattened, relocated library is collision-proof.
     */
    public static byte[] relocate(byte[] classBytes, List<Relocation> relocations) {
        return new ClassRelocator(relocations).relocate(classBytes);
    }
}
