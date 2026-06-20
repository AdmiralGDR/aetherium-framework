/*
 * Aetherium Framework — deterministic topological sort of the hook ordering DAG.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Resolves a set of {@link HookNode}s into a deterministic execution order from their
 * {@code runBefore}/{@code runAfter} constraints — Kahn's algorithm over the hook DAG.
 *
 * <p>EN: This is the "no dumb priorities" engine. Each node declares relationships; this builds the
 * edge set ({@code runBefore("x")} ⇒ {@code this → x}; {@code runAfter("y")} ⇒ {@code y → this}),
 * computes in-degrees, and repeatedly emits the ready node with the smallest <em>declaration index</em>
 * (so the order is stable and reproducible across launches, never hash-dependent). Constraints that name
 * an id outside this group are ignored as soft (they refer to hooks another mod may or may not have
 * registered) rather than failing. A genuine cycle throws {@link HookCycleException}.
 *
 * <p>RU: Движок «без тупых приоритетов». Каждый узел объявляет отношения; строится множество рёбер
 * ({@code runBefore("x")} ⇒ {@code this → x}; {@code runAfter("y")} ⇒ {@code y → this}), считаются
 * входящие степени, и многократно выдаётся готовый узел с наименьшим <em>индексом объявления</em> (чтобы
 * порядок был стабильным и воспроизводимым между запусками, не завися от хеша). Ограничения, называющие
 * id вне группы, игнорируются как мягкие. Настоящий цикл бросает {@link HookCycleException}.
 */
final class HookDag {

    private HookDag() {
    }

    /**
     * @return the input nodes in a stable topological order honoring every in-group constraint
     * @throws HookCycleException if the constraints are not acyclic
     */
    static List<HookNode> sort(List<HookNode> nodes) {
        int n = nodes.size();
        Map<String, Integer> indexOf = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            HookNode node = nodes.get(i);
            if (indexOf.putIfAbsent(node.id(), i) != null) {
                throw new HookCycleException("duplicate hook id '" + node.id() + "' in the same merged group");
            }
        }

        // adjacency (declaration-index based) + in-degree
        Map<Integer, List<Integer>> edges = new LinkedHashMap<>();
        int[] indegree = new int[n];
        for (int i = 0; i < n; i++) {
            edges.put(i, new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            HookNode node = nodes.get(i);
            for (String before : node.runBefore()) {           // this -> before
                Integer j = indexOf.get(before);
                if (j != null && j != i) {
                    edges.get(i).add(j);
                    indegree[j]++;
                }
            }
            for (String after : node.runAfter()) {             // after -> this
                Integer j = indexOf.get(after);
                if (j != null && j != i) {
                    edges.get(j).add(i);
                    indegree[i]++;
                }
            }
        }

        // Kahn with smallest-declaration-index tie-break (deterministic, reproducible).
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) {
                ready.add(i);
            }
        }
        List<HookNode> ordered = new ArrayList<>(n);
        while (!ready.isEmpty()) {
            int i = ready.poll();
            ordered.add(nodes.get(i));
            for (int j : edges.get(i)) {
                if (--indegree[j] == 0) {
                    ready.add(j);
                }
            }
        }

        if (ordered.size() != n) {
            throw new HookCycleException("cyclic runBefore/runAfter constraints among hooks "
                    + nodes.stream().map(HookNode::id).toList());
        }
        return ordered;
    }
}
