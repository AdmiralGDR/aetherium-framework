/*
 * Aetherium Framework — shield opaque-name generator.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

/**
 * Generates short, meaningless, collision-free identifiers for the renamer.
 *
 * <p>EN: Names are drawn from a base-26 sequence ({@code a, b, …, z, aa, …}) under a fixed opaque package
 * ({@code o/}), so the reconstructed class graph carries no package structure, no descriptive words, and no
 * hint of intent — exactly the semantic scaffolding a human or AI relies on. Purely mechanical and
 * deterministic given call order.
 * RU: Имена берутся из последовательности base-26 в фиксированном непрозрачном пакете ({@code o/}), поэтому
 * восстановленный граф классов не несёт структуры пакетов, описательных слов и намёков на замысел.
 */
final class OpaqueNames {

    private final String packagePrefix;
    private long counter;

    OpaqueNames(String packagePrefix) {
        this.packagePrefix = packagePrefix;
    }

    /** Next opaque internal class name, e.g. {@code o/a}, {@code o/b}, … */
    String nextClass() {
        return packagePrefix + base26(counter++);
    }

    /** Next opaque member (method/field) name, e.g. {@code a}, {@code b}, … */
    String nextMember() {
        return base26(counter++);
    }

    private static String base26(long n) {
        StringBuilder sb = new StringBuilder();
        long v = n;
        do {
            sb.insert(0, (char) ('a' + (int) (v % 26)));
            v = v / 26 - 1;
        } while (v >= 0);
        return sb.toString();
    }
}
