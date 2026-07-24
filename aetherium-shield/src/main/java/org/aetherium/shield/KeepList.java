/*
 * Aetherium Framework — shield keep-list (what must NOT be renamed).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import java.util.HashSet;
import java.util.Set;

/**
 * Decides which classes must keep their name so the framework can still find them by name at runtime.
 *
 * <p>EN: Renaming is the strongest anti-analysis pass but the most dangerous: anything reached
 * <em>reflectively or by name</em> — {@code ServiceLoader} implementations, the {@code @AetheriumInit}
 * generated entrypoint, {@code @AetheriumBlock}/{@code @AetheriumItem} content classes, and the framework's
 * own runtime — must be preserved verbatim or the mod won't load. This keep-list captures those exceptions;
 * everything else is fair game. By default it keeps the whole {@code org/aetherium/} runtime and any class
 * named in a {@code META-INF/services/} file (added via {@link #keepService}).
 * RU: Переименование — самая сильная защита от анализа, но и самая опасная: всё, что достигается
 * <em>рефлексией или по имени</em> (реализации {@code ServiceLoader}, сгенерированная точка входа
 * {@code @AetheriumInit}, классы контента, сам рантайм фреймворка), должно сохранить имя, иначе мод не
 * загрузится. Этот keep-list фиксирует исключения; остальное можно переименовывать.
 */
public final class KeepList {

    private final Set<String> keptInternalNames = new HashSet<>();
    private final Set<String> keptPrefixes = new HashSet<>();

    public KeepList() {
        // The framework runtime is embedded and resolved by name/service — never rename it.
        keptPrefixes.add("org/aetherium/");
        // The auto-wiring entrypoint the annotation processor generates is loaded by ServiceLoader by name.
        keptPrefixes.add("org/aetherium/generated/");
    }

    /** Keep an exact class (by JVM internal name, e.g. {@code com/example/MyMod}). */
    public KeepList keep(String internalName) {
        keptInternalNames.add(internalName);
        return this;
    }

    /** Keep every class under a package prefix (JVM internal form, e.g. {@code com/example/api/}). */
    public KeepList keepPrefix(String internalPrefix) {
        keptPrefixes.add(internalPrefix);
        return this;
    }

    /** Keep a class named in a {@code META-INF/services} file — it is discovered by name at runtime. */
    public KeepList keepService(String binaryName) {
        keptInternalNames.add(binaryName.replace('.', '/'));
        return this;
    }

    /** Whether {@code internalName} must keep its name. */
    public boolean isKept(String internalName) {
        if (keptInternalNames.contains(internalName)) {
            return true;
        }
        for (String prefix : keptPrefixes) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
