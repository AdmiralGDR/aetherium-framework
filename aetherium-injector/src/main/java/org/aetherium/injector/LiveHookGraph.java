/*
 * Aetherium Framework — mutable, live-reconcilable hook ordering graph.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mutable hook graph whose deterministic order can be <em>re-resolved at runtime</em> — the seam the
 * live hot-swap engine uses so injected hooks can be added, removed, or re-ordered without a restart.
 *
 * <p>EN: {@link HookDag} sorts a fixed group once at weave time. {@code LiveHookGraph} wraps the same
 * algorithm behind a mutable registry: a modder's redefined class can {@link #register} a new hook or
 * {@link #remove} an old one, and {@link #resolve()} re-runs the deterministic topological sort over the
 * current set. {@code aetherium-hotswap} calls {@code resolve()} after every successful
 * {@code redefineClasses}, so the running game always executes hooks in the freshly reconciled order —
 * the DAG and the live class image stay in lock-step. Insertion order is preserved, so the sort's
 * declaration-index tie-break remains stable and reproducible.
 *
 * <p>RU: {@link HookDag} сортирует фиксированную группу один раз во время вплетения.
 * {@code LiveHookGraph} оборачивает тот же алгоритм изменяемым реестром: переопределённый класс мода
 * может {@link #register} новый хук или {@link #remove} старый, а {@link #resolve()} заново запускает
 * детерминированную топосортировку по текущему множеству. {@code aetherium-hotswap} вызывает
 * {@code resolve()} после каждого успешного {@code redefineClasses}, поэтому работающая игра всегда
 * исполняет хуки в свежем согласованном порядке — DAG и живой образ класса синхронны. Порядок вставки
 * сохраняется, поэтому tie-break по индексу объявления стабилен и воспроизводим.
 */
public final class LiveHookGraph {

    private final Map<String, HookNode> nodes = new LinkedHashMap<>();

    /** Register (or replace) a hook by id. Returns {@code this} for fluent chaining. */
    public synchronized LiveHookGraph register(String id, ContextualHook hook) {
        nodes.put(id, new HookNode(id, hook));
        return this;
    }

    /** Constrain {@code id} to run before the named hooks (edges {@code id -> other}). */
    public synchronized LiveHookGraph runBefore(String id, String... others) {
        requireNode(id).addRunBefore(others);
        return this;
    }

    /** Constrain {@code id} to run after the named hooks (edges {@code other -> id}). */
    public synchronized LiveHookGraph runAfter(String id, String... others) {
        requireNode(id).addRunAfter(others);
        return this;
    }

    /** Remove a hook by id; returns true if it was present. */
    public synchronized boolean remove(String id) {
        return nodes.remove(id) != null;
    }

    public synchronized int size() {
        return nodes.size();
    }

    /**
     * EN: Re-resolve the current graph into a deterministic execution order of hook ids.
     * RU: Заново разрешить текущий граф в детерминированный порядок исполнения id хуков.
     *
     * @throws HookCycleException if the live constraints are not acyclic
     */
    public synchronized List<String> resolve() {
        List<HookNode> ordered = HookDag.sort(new ArrayList<>(nodes.values()));
        List<String> ids = new ArrayList<>(ordered.size());
        for (HookNode node : ordered) {
            ids.add(node.id());
        }
        return ids;
    }

    /** Re-resolve and return the ordered {@link HookNode}s (for callers that need the hooks themselves). */
    public synchronized List<HookNode> resolveNodes() {
        return HookDag.sort(new ArrayList<>(nodes.values()));
    }

    private HookNode requireNode(String id) {
        HookNode node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("no hook registered with id '" + id + "'");
        }
        return node;
    }
}
