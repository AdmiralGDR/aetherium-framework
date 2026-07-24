/*
 * Aetherium Framework — injection hook dispatch table.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import java.util.Arrays;
import java.util.Objects;

/**
 * The runtime hook table: a flat array of {@link AetheriumHook}s indexed by a <em>process-wide</em> dense
 * hook ID.
 *
 * <p>EN: The injector's analogue of the bytecode engine's {@code DispatchTable}. Every hook a
 * {@link BytecodeCursor} injects becomes an {@code invokedynamic} site carrying a dense hook ID;
 * {@link HookBootstrap} links it <em>once</em> against {@link #hook(int)} and caches a
 * {@code ConstantCallSite}, so after linkage the call is a direct, JIT-inlinable invocation — the
 * {@code O(1)} guarantee.
 *
 * <p><strong>Multi-mod coexistence.</strong> IDs are allocated <em>globally and append-only</em> via
 * {@link #registerVoid}/{@link #registerContext} at hook-declaration time, so two independently built
 * {@link org.aetherium.injector.AetheriumInjector}s (i.e. two different mods) never share or overwrite each
 * other's IDs. This replaces the previous "each injector starts at 0 and {@code install()} replaces the
 * whole array" design, under which a second mod silently clobbered the first mod's hooks (and any ID beyond
 * the second mod's array threw a {@code BootstrapMethodError} inside a vanilla method). The arrays only ever
 * grow, by copy-on-write, and are published through {@code volatile} fields so a newly registered hook is
 * visible to all threads before any lowered call site can reach it.
 *
 * <p>RU: Аналог {@code DispatchTable} движка байт-кода в инжекторе. Каждый внедрённый курсором хук
 * становится точкой {@code invokedynamic} с плотным ID хука; {@link HookBootstrap} линкует её
 * <em>однократно</em> с {@link #hook(int)} — после линковки вызов прямой и встраиваемый JIT (гарантия
 * {@code O(1)}). ID выдаются <em>глобально и только по возрастанию</em> через
 * {@link #registerVoid}/{@link #registerContext} в момент объявления хука, поэтому два независимо
 * построенных инжектора (два разных мода) никогда не пересекаются и не затирают ID друг друга — это и есть
 * исправление совместной работы модов. Массивы только растут (copy-on-write) и публикуются через
 * {@code volatile}-поля.
 */
public final class HookTable {

    /** Guards the append allocators so two threads never claim the same ID. Lookups stay lock-free. */
    private static final Object LOCK = new Object();

    private static volatile AetheriumHook[] hooks = new AetheriumHook[0];
    private static volatile ContextualHook[] contextHooks = new ContextualHook[0];

    private HookTable() {
    }

    /**
     * Register a void hook and return its <em>process-wide unique</em> dense ID. The array grows by one via
     * copy-on-write; the ID is the new slot's index. This is the allocation point baked into the lowered
     * {@code invokedynamic} site, so distinct injectors never collide.
     */
    public static int registerVoid(AetheriumHook hook) {
        Objects.requireNonNull(hook, "hook");
        synchronized (LOCK) {
            AetheriumHook[] current = hooks;
            int id = current.length;
            AetheriumHook[] grown = Arrays.copyOf(current, id + 1);
            grown[id] = hook;
            hooks = grown; // volatile publish — visible before the bytecode carrying `id` can run
            return id;
        }
    }

    /**
     * Register a context-aware hook and return its process-wide unique dense ID (independent ID space from
     * {@link #registerVoid}).
     */
    public static int registerContext(ContextualHook hook) {
        Objects.requireNonNull(hook, "hook");
        synchronized (LOCK) {
            ContextualHook[] current = contextHooks;
            int id = current.length;
            ContextualHook[] grown = Arrays.copyOf(current, id + 1);
            grown[id] = hook;
            contextHooks = grown;
            return id;
        }
    }

    /** {@code O(1)} lookup. Returns {@code null} for an out-of-range or unbound ID. */
    public static AetheriumHook hook(int hookId) {
        AetheriumHook[] snapshot = hooks;
        return (hookId >= 0 && hookId < snapshot.length) ? snapshot[hookId] : null;
    }

    /** {@code O(1)} context-hook lookup. Returns {@code null} for an out-of-range or unbound ID. */
    public static ContextualHook contextHook(int hookId) {
        ContextualHook[] snapshot = contextHooks;
        return (hookId >= 0 && hookId < snapshot.length) ? snapshot[hookId] : null;
    }

    /** Number of installed void hooks. */
    public static int size() {
        return hooks.length;
    }

    /** Number of installed context hooks. */
    public static int contextSize() {
        return contextHooks.length;
    }

    /**
     * @deprecated Array-replacing install. Hazardous across mods — it discards every hook not in
     *     {@code resolved}, so a second mod wipes the first. Superseded by the append-only
     *     {@link #registerVoid}. Retained only for the rare single-owner tool that rebuilds the whole table.
     */
    @Deprecated
    public static void install(AetheriumHook[] resolved) {
        synchronized (LOCK) {
            hooks = resolved.clone();
        }
    }

    /**
     * @deprecated Array-replacing install of the context table. See {@link #install(AetheriumHook[])}.
     */
    @Deprecated
    public static void installContext(ContextualHook[] resolved) {
        synchronized (LOCK) {
            contextHooks = resolved.clone();
        }
    }
}
