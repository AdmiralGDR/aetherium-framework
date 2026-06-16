/*
 * Aetherium Framework — testmod programmatic injection (the fluent cursor in action).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.injector.AetheriumInjector;
import org.aetherium.injector.InjectionProvider;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Declares this mod's bytecode injection using the fluent {@link AetheriumInjector} — no Mixin, no
 * annotations, no string-based {@code @At}.
 *
 * <p>EN: Discovered by the loader via {@code META-INF/services/org.aetherium.injector.InjectionProvider}.
 * It navigates {@link MockInterceptTarget#vanillaCompute()} with the typed cursor — find the return,
 * insert a hook before it — and the hook is lowered to the {@code O(1)} {@code invokedynamic} dispatch
 * path. The whole thing runs inside the engine's verification sandbox, so even a wrong target reverts
 * safely. This class imports no NeoForge/Minecraft type; it targets the class by JVM internal name.
 *
 * <p>RU: Обнаруживается загрузчиком через {@code META-INF/services/...InjectionProvider}. Навигирует
 * {@link MockInterceptTarget#vanillaCompute()} типизированным курсором — найти возврат, вставить хук
 * перед ним — и хук понижается до пути диспетчеризации {@code invokedynamic} ({@code O(1)}). Всё
 * выполняется в верификационной песочнице движка. Класс не импортирует типы NeoForge/Minecraft;
 * цель задаётся по JVM-имени.
 */
public final class TestmodInjectionProvider implements InjectionProvider {

    /** Observable proof that the injected hook ran (incremented on every interception). */
    public static final AtomicInteger INTERCEPTIONS = new AtomicInteger();

    @Override
    public void configure(AetheriumInjector injector) {
        injector.inClass("org/aetherium/testmod/MockInterceptTarget")
                .method("vanillaCompute", "()I")
                    .findReturn()
                    .insertHookBefore(TestmodInjectionProvider::onIntercept)
                .commit();
    }

    /** The Aetherium hook — in a real mod this would route into async tick / Vulkan compute / etc. */
    public static void onIntercept() {
        INTERCEPTIONS.incrementAndGet();
    }
}
