/*
 * Aetherium Framework — hot-swap engine tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap;

import org.aetherium.injector.LiveHookGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN: Verifies the hot-swap self-test passes (live redefine when possible, graceful degrade otherwise)
 * and that the live DAG reconciliation is deterministic and constraint-honoring.
 * RU: Проверяет, что самотест hot-swap проходит (живое переопределение при возможности, иначе мягкая
 * деградация) и что согласование живого DAG детерминировано и соблюдает ограничения.
 */
class HotSwapEngineTest {

    @Test
    void selfTestPasses() {
        HotSwapSelfTest.Result r = HotSwapSelfTest.run();
        assertTrue(r.passed(), () -> "hot-swap self-test failed: " + r.notes());
        assertTrue(r.valueBeforeOk(), "v1 currentValue() should be 1");
        assertTrue(r.dagReconciled(), "live DAG reconciliation should hold");
    }

    @Test
    void liveHookGraphReResolvesDeterministically() {
        LiveHookGraph graph = new LiveHookGraph();
        graph.register("a", ctx -> { })
                .register("b", ctx -> { }).runAfter("b", "a")
                .register("c", ctx -> { }).runBefore("c", "b");
        List<String> first = graph.resolve();
        // 'a' before 'b'; 'c' before 'b'.
        assertTrue(first.indexOf("a") < first.indexOf("b"));
        assertTrue(first.indexOf("c") < first.indexOf("b"));

        // Re-resolving without changes is identical (deterministic).
        assertEquals(first, graph.resolve());

        // Live mutation re-resolves into a new order including the added hook.
        graph.register("d", ctx -> { }).runAfter("d", "b");
        List<String> second = graph.resolve();
        assertEquals(4, second.size());
        assertTrue(second.indexOf("b") < second.indexOf("d"));
    }
}
