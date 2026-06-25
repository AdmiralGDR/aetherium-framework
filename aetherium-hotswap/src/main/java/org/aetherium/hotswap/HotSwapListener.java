/*
 * Aetherium Framework — hot-swap reconciliation callback.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap;

/**
 * Notified after a class is successfully redefined, so dependent state can reconcile live.
 *
 * <p>EN: The primary subscriber is the injector's hook layer: when a redefined class changes the hooks
 * it contributes, the listener re-resolves the {@link org.aetherium.injector.LiveHookGraph} so the
 * running game executes the new, deterministically ordered hook set immediately. Listeners must not
 * throw — the engine isolates them so one bad reconciler never aborts a hot-swap.
 * RU: Главный подписчик — слой хуков инжектора: когда переопределённый класс меняет вносимые им хуки,
 * слушатель заново разрешает {@link org.aetherium.injector.LiveHookGraph}, чтобы игра немедленно
 * исполняла новый детерминированно упорядоченный набор хуков. Слушатели не должны бросать исключения —
 * движок изолирует их, чтобы один плохой реконсилятор не прервал hot-swap.
 */
@FunctionalInterface
public interface HotSwapListener {

    /** Called on the swap thread after {@code binaryClassName} was redefined in the live JVM. */
    void onClassRedefined(String binaryClassName);
}
