/*
 * Aetherium Framework — context-aware injection hook callback.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

/**
 * A hook that receives a {@link HookContext} — the context-aware sibling of {@link AetheriumHook}.
 *
 * <p>EN: Where {@link AetheriumHook} is a fire-and-forget {@code void ()} callback (the zero-allocation
 * hot path), a {@code ContextualHook} is handed the intercepted method's {@code this} and arguments and
 * can <strong>cancel</strong> the original method via {@link HookContext#cancel()} /
 * {@link HookContext#cancel(Object)}. The cursor still lowers the call to an {@code O(1)}
 * {@code invokedynamic} site (bound once to the {@link HookTable}); the only addition is the
 * {@link HookContext} argument and the frame-correct cancellation check the cursor emits after the call.
 *
 * <p>RU: Если {@link AetheriumHook} — это бесконтекстный колбэк {@code void ()} (горячий путь без
 * аллокаций), то {@code ContextualHook} получает {@code this} и аргументы перехваченного метода и может
 * <strong>отменить</strong> исходный метод через {@link HookContext#cancel()} /
 * {@link HookContext#cancel(Object)}. Курсор по-прежнему понижает вызов до точки {@code invokedynamic}
 * ({@code O(1)}, привязанной однократно к {@link HookTable}); добавляются лишь аргумент
 * {@link HookContext} и корректная по фреймам проверка отмены, эмитируемая курсором после вызова.
 */
@FunctionalInterface
public interface ContextualHook {

    /** Run the hook against {@code ctx}; call {@link HookContext#cancel()} to skip the vanilla body. */
    void invoke(HookContext ctx);
}
