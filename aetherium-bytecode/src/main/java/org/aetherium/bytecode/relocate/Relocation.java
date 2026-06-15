/*
 * Aetherium Framework — package relocation rule.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.bytecode.relocate;

import java.util.Objects;

/**
 * A namespace relocation rule: rewrite type references under {@code fromPrefix} to {@code toPrefix}.
 *
 * <p>EN: Prefixes are JVM internal names (slashes), e.g. {@code com/google/common} →
 * {@code org/aetherium/shadow/guava}. Applied by {@link ClassRelocator} to shade a bundled library
 * into a private namespace so two mods embedding different versions of the same library cannot
 * clash (Library Shadowing).
 *
 * <p>RU: Префиксы — внутренние имена JVM (со слэшами), напр. {@code com/google/common} →
 * {@code org/aetherium/shadow/guava}. Применяется {@link ClassRelocator}, чтобы «затенить»
 * встроенную библиотеку в приватное пространство имён, чтобы два мода со встроенными разными
 * версиями одной библиотеки не конфликтовали (Library Shadowing).
 *
 * @param fromPrefix source internal-name prefix (e.g. {@code "com/google/common"})
 * @param toPrefix   destination internal-name prefix (e.g. {@code "org/aetherium/shadow/guava"})
 */
public record Relocation(String fromPrefix, String toPrefix) {

    public Relocation {
        Objects.requireNonNull(fromPrefix, "fromPrefix");
        Objects.requireNonNull(toPrefix, "toPrefix");
        if (fromPrefix.isBlank()) {
            throw new IllegalArgumentException("fromPrefix must not be blank");
        }
    }

    /** Convenience: build from dotted package names. */
    public static Relocation ofPackages(String fromPackage, String toPackage) {
        return new Relocation(fromPackage.replace('.', '/'), toPackage.replace('.', '/'));
    }

    boolean matches(String internalName) {
        return internalName.startsWith(fromPrefix);
    }

    String apply(String internalName) {
        return toPrefix + internalName.substring(fromPrefix.length());
    }
}
