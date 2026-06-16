/*
 * Aetherium Framework — injection E2E target (stand-in for a vanilla class).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

/**
 * A plain class that stands in for a vanilla {@code net.minecraft} method the mod wants to intercept.
 *
 * <p>EN: Using a mock (instead of a real game class) keeps {@code aetherium-testmod} pure and lets the
 * injection be proven headlessly — but the mechanism is identical: {@link TestmodInjectionProvider}
 * targets {@link #vanillaCompute()} by name + descriptor and injects an Aetherium hook before its
 * return, with no edit to this source.
 *
 * <p>RU: Использование мока (вместо реального игрового класса) сохраняет чистоту
 * {@code aetherium-testmod} и позволяет доказать инъекцию без запуска игры — механизм идентичен:
 * {@link TestmodInjectionProvider} нацеливается на {@link #vanillaCompute()} по имени + дескриптору и
 * внедряет хук Aetherium перед возвратом, без правки этого исходника.
 */
public final class MockInterceptTarget {

    private MockInterceptTarget() {
    }

    /** A "vanilla" computation the injector will intercept (returns 21 unchanged after interception). */
    public static int vanillaCompute() {
        return 21;
    }
}
