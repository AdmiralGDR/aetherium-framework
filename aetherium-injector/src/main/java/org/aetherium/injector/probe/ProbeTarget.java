/*
 * Aetherium Framework — an ephemeral probe target (class#method).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.probe;

import java.util.Objects;

/**
 * Identifies a method to (un)weave a JFR timing probe into — typed, not a string selector.
 *
 * <p>EN: {@code methodDesc} may be {@code null} to match every overload of {@code methodName}.
 *
 * <p>RU: {@code methodDesc} может быть {@code null}, чтобы охватить все перегрузки {@code methodName}.
 *
 * @param classInternalName JVM internal name, e.g. {@code net/minecraft/server/level/ServerLevel}
 * @param methodName        target method name
 * @param methodDesc        target descriptor, or {@code null} to match any overload
 */
public record ProbeTarget(String classInternalName, String methodName, String methodDesc) {

    public ProbeTarget {
        Objects.requireNonNull(classInternalName, "classInternalName");
        Objects.requireNonNull(methodName, "methodName");
    }

    /** Match every overload of a method. */
    public static ProbeTarget of(String classInternalName, String methodName) {
        return new ProbeTarget(classInternalName, methodName, null);
    }

    boolean matchesMethod(String name, String desc) {
        return methodName.equals(name) && (methodDesc == null || methodDesc.equals(desc));
    }
}
