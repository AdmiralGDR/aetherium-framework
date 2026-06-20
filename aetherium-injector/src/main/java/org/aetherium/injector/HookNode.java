/*
 * Aetherium Framework — a single node in the hook-ordering DAG.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One hook in a merged group, plus its ordering constraints — a vertex in the hook DAG.
 *
 * <p>EN: Eradicates dumb integer "priorities". Instead of guessing a number, a mod declares
 * <em>relationships</em>: {@code runBefore("mod_x")} / {@code runAfter("mod_y")}. {@link HookDag}
 * turns these edges into a deterministic topological order. Every hook is a {@link ContextualHook}, so
 * any node can read {@link HookContext} and request cancellation; the {@link MergedHookRule} then runs
 * the whole sorted group against a single shared context (see the Semantic Merger).
 *
 * <p>RU: Устраняет тупые целочисленные «приоритеты». Вместо угадывания числа мод объявляет
 * <em>отношения</em>: {@code runBefore("mod_x")} / {@code runAfter("mod_y")}. {@link HookDag}
 * превращает эти рёбра в детерминированный топологический порядок. Каждый хук — {@link ContextualHook},
 * поэтому любой узел может читать {@link HookContext} и запрашивать отмену; {@link MergedHookRule}
 * затем прогоняет всю отсортированную группу против единого общего контекста.
 */
public final class HookNode {

    private final String id;
    private final ContextualHook hook;
    private final Set<String> runBefore = new LinkedHashSet<>();
    private final Set<String> runAfter = new LinkedHashSet<>();

    HookNode(String id, ContextualHook hook) {
        this.id = Objects.requireNonNull(id, "id");
        this.hook = Objects.requireNonNull(hook, "hook");
    }

    /** Stable identity used by other nodes' {@code runBefore}/{@code runAfter} constraints. */
    public String id() {
        return id;
    }

    public ContextualHook hook() {
        return hook;
    }

    /** IDs this node must run <em>before</em> (edges {@code this -> other}). */
    public Set<String> runBefore() {
        return runBefore;
    }

    /** IDs this node must run <em>after</em> (edges {@code other -> this}). */
    public Set<String> runAfter() {
        return runAfter;
    }

    void addRunBefore(String... ids) {
        for (String i : ids) {
            runBefore.add(Objects.requireNonNull(i, "runBefore id"));
        }
    }

    void addRunAfter(String... ids) {
        for (String i : ids) {
            runAfter.add(Objects.requireNonNull(i, "runAfter id"));
        }
    }
}
