/*
 * Aetherium Framework — injection hook dispatch table.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

/**
 * The runtime hook table: a flat array of {@link AetheriumHook}s indexed by dense hook ID.
 *
 * <p>EN: The injector's analogue of the bytecode engine's {@code DispatchTable}. Every hook a
 * {@link BytecodeCursor} injects becomes an {@code invokedynamic} site carrying a dense hook ID;
 * {@link HookBootstrap} links it <em>once</em> against {@link #hook(int)} and caches a
 * {@code ConstantCallSite}, so after linkage the call is a direct, JIT-inlinable invocation — the
 * {@code O(1)} guarantee. Populated once (per {@link org.aetherium.injector.AetheriumInjector#installHooks()})
 * and then read-only; held in a {@code volatile} field so the install publishes safely to all threads.
 *
 * <p>RU: Аналог {@code DispatchTable} движка байт-кода в инжекторе. Каждый внедрённый курсором хук
 * становится точкой {@code invokedynamic} с плотным ID хука; {@link HookBootstrap} линкует её
 * <em>однократно</em> с {@link #hook(int)} и кэширует {@code ConstantCallSite} — после линковки вызов
 * прямой и встраиваемый JIT (гарантия {@code O(1)}). Заполняется один раз и далее только для чтения;
 * хранится в {@code volatile}-поле для безопасной публикации во все потоки.
 */
public final class HookTable {

    private static volatile AetheriumHook[] hooks = new AetheriumHook[0];

    private HookTable() {
    }

    /** Install the hook table (defensively copied). Intended to be called once at load time. */
    public static void install(AetheriumHook[] resolved) {
        hooks = resolved.clone();
    }

    /** {@code O(1)} lookup. Returns {@code null} for an out-of-range or unbound ID. */
    public static AetheriumHook hook(int hookId) {
        AetheriumHook[] snapshot = hooks;
        return (hookId >= 0 && hookId < snapshot.length) ? snapshot[hookId] : null;
    }

    /** Number of installed hooks. */
    public static int size() {
        return hooks.length;
    }
}
