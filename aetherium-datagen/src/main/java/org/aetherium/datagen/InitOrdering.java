/*
 * Aetherium Framework — deterministic topological sort of the @AetheriumInit DAG.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Orders the discovered {@link InitMethod}s into a deterministic initialization sequence — Kahn's
 * algorithm over the init DAG, identical in spirit to the hook DAG so modders learn one ordering model.
 *
 * <p>EN: {@code runBefore("x")} ⇒ edge {@code this → x}; {@code runAfter("y")} ⇒ edge {@code y → this}.
 * In-degrees are computed and the ready node with the smallest <em>declaration index</em> is emitted
 * first, so the order is stable and reproducible across builds (never hash-dependent). Edges naming an id
 * that is not present are ignored as soft (another mod may or may not contribute it). A genuine cycle, or
 * a duplicate id, throws {@link IllegalStateException} so the build fails loudly instead of guessing.
 * RU: {@code runBefore("x")} ⇒ ребро {@code this → x}; {@code runAfter("y")} ⇒ ребро {@code y → this}.
 * Считаются входящие степени, и первым выдаётся готовый узел с наименьшим <em>индексом объявления</em> —
 * порядок стабилен и воспроизводим (не зависит от хеша). Рёбра на отсутствующий id игнорируются как
 * мягкие. Настоящий цикл или дубликат id бросает {@link IllegalStateException}.
 */
public final class InitOrdering {

    private InitOrdering() {
    }

    /** @return the inits in a stable topological order; throws on a duplicate id or a cycle. */
    public static List<InitMethod> order(List<InitMethod> inits) {
        int n = inits.size();
        Map<String, Integer> indexOf = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            if (indexOf.putIfAbsent(inits.get(i).id(), i) != null) {
                throw new IllegalStateException("duplicate @AetheriumInit id '" + inits.get(i).id() + "'");
            }
        }

        Map<Integer, List<Integer>> edges = new LinkedHashMap<>();
        int[] indegree = new int[n];
        for (int i = 0; i < n; i++) {
            edges.put(i, new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            InitMethod node = inits.get(i);
            for (String before : node.runBefore()) {            // this -> before
                Integer j = indexOf.get(before);
                if (j != null && j != i) {
                    edges.get(i).add(j);
                    indegree[j]++;
                }
            }
            for (String after : node.runAfter()) {               // after -> this
                Integer j = indexOf.get(after);
                if (j != null && j != i) {
                    edges.get(j).add(i);
                    indegree[i]++;
                }
            }
        }

        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) {
                ready.add(i);
            }
        }
        List<InitMethod> ordered = new ArrayList<>(n);
        while (!ready.isEmpty()) {
            int i = ready.poll();
            ordered.add(inits.get(i));
            for (int j : edges.get(i)) {
                if (--indegree[j] == 0) {
                    ready.add(j);
                }
            }
        }
        if (ordered.size() != n) {
            throw new IllegalStateException("cyclic @AetheriumInit runBefore/runAfter constraints among "
                    + inits.stream().map(InitMethod::id).toList());
        }
        return ordered;
    }
}
