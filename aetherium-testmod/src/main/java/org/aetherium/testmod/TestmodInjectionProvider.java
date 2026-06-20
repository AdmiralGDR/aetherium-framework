/*
 * Aetherium Framework — testmod programmatic injection (the fluent cursor in action).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.injector.AetheriumInjector;
import org.aetherium.injector.HookContext;
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

    /** The last damage amount the cancelling hook observed (proof that arguments reach the hook). */
    public static final AtomicInteger LAST_DAMAGE_SEEN = new AtomicInteger(Integer.MIN_VALUE);

    @Override
    public void configure(AetheriumInjector injector) {
        // (a) Plain void hook: observe vanillaCompute()'s return without changing it.
        injector.inClass("org/aetherium/testmod/MockInterceptTarget")
                .method("vanillaCompute", "()I")
                    .findReturn()
                    .insertHookBefore(TestmodInjectionProvider::onIntercept)
                .commit();

        // (b) Context hook with argument capture + cancellation: read the damage argument and CANCEL
        //     the vanilla method, returning 0 (an "invulnerability" hook). This is the Mixin-killer's
        //     final capability — read `this`/args and bypass the original body entirely.
        injector.inClass("org/aetherium/testmod/MockInterceptTarget")
                .method("vanillaDamage", "(I)I")
                    .toStart()
                    .insertContextHookBefore(TestmodInjectionProvider::onDamage, true)
                .commit();
    }

    /** The Aetherium hook — in a real mod this would route into async tick / Vulkan compute / etc. */
    public static void onIntercept() {
        INTERCEPTIONS.incrementAndGet();
    }

    /** Reads the intercepted damage argument and cancels the method, returning 0 (no damage taken). */
    public static void onDamage(HookContext ctx) {
        int amount = (Integer) ctx.arg(0);
        LAST_DAMAGE_SEEN.set(amount);
        ctx.cancel(0);
    }
}
