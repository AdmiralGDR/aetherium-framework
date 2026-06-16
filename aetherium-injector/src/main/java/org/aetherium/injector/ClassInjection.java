/*
 * Aetherium Framework — fluent per-class injection target.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import java.util.Objects;

/**
 * The fluent step that selects a target method within a target class.
 *
 * <p>EN: Obtained from {@link AetheriumInjector#inClass(String)}. {@link #method(String, String)}
 * names the method by its real name + JVM descriptor — strongly typed, never a string pattern like
 * Mixin's {@code @At} — and hands off to a {@link MethodInjection} for the actual cursor work.
 *
 * <p>RU: Получается из {@link AetheriumInjector#inClass(String)}. {@link #method(String, String)}
 * называет метод по реальному имени + JVM-дескриптору — строго типизированно, без строкового шаблона
 * вроде {@code @At} из Mixin — и передаёт работу {@link MethodInjection}.
 */
public final class ClassInjection {

    private final AetheriumInjector injector;
    private final String classInternalName;

    ClassInjection(AetheriumInjector injector, String classInternalName) {
        this.injector = injector;
        this.classInternalName = Objects.requireNonNull(classInternalName, "classInternalName");
    }

    /**
     * Target a method by name and JVM descriptor (e.g. {@code method("tick", "()V")}).
     */
    public MethodInjection method(String name, String descriptor) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        return new MethodInjection(injector, classInternalName, name, descriptor);
    }
}
