/*
 * Aetherium Framework — injection hook callback.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

/**
 * The callback a {@link BytecodeCursor} injects into a target method.
 *
 * <p>EN: A plain {@code void ()} functional interface — typically a method reference such as
 * {@code MyMod::asyncTick} that routes the intercepted control flow into a high-performance Aetherium
 * API (virtual-thread tick, Vulkan compute, off-heap sync, …). It is <strong>never</strong> wired in
 * as a brittle static call: the cursor lowers it to an {@code invokedynamic} site bound, once, to the
 * {@link HookTable} entry the injector assigned — the same {@code O(1)} dispatch mechanism the
 * bytecode engine uses for API lowering.
 *
 * <p>RU: Простой функциональный интерфейс {@code void ()} — обычно ссылка на метод вроде
 * {@code MyMod::asyncTick}, направляющая перехваченный поток управления в высокопроизводительный API
 * Aetherium (тик на виртуальных потоках, Vulkan-вычисления, off-heap синхронизация, …). Он
 * <strong>никогда</strong> не подключается как хрупкий статический вызов: курсор понижает его в точку
 * {@code invokedynamic}, привязанную однократно к записи {@link HookTable}, назначенной инжектором —
 * тот же механизм диспетчеризации {@code O(1)}, что движок байт-кода применяет для понижения API.
 */
@FunctionalInterface
public interface AetheriumHook {

    /** Run the hook. Implementations should be fast and must contain their own failures. */
    void invoke();
}
