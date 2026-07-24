/*
 * Aetherium Framework — JUnit coverage for ACID Atomicity of mod hooks.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.txn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN: Locks in the plan's Verification requirement — a mod with 3 hooks whose 3rd fails must roll back
 * hooks 1 and 2, while a healthy sibling mod still commits.
 * RU: Фиксирует требование верификации из плана — мод с 3 хуками, третий из которых падает, обязан
 * откатить хуки 1 и 2, тогда как здоровый соседний мод всё равно коммитится.
 */
final class TransactionalInjectorTest {

    @Test
    void thirdHookFailureRollsBackFirstTwo() throws Exception {
        TransactionalInjectorSelfTest.Result r = TransactionalInjectorSelfTest.run();

        // Atomicity: the whole mod is rolled back, not just the failing class.
        assertTrue(r.gravityRolledBack(), "the 3-hook mod must roll back as a unit");
        assertEquals(2, r.appliedBeforeAbort(), "hooks 1 and 2 verified before hook 3 failed");
        assertTrue(r.failedClass().endsWith("MockC"), "hook 3 (MockC) is the failing hook");
        assertTrue(r.gravityPublishedNothing(),
                "no class of the rolled-back mod may be published (no partial application)");
        assertTrue(r.rolledBackHooksInert(),
                "loading the rolled-back classes must run vanilla — hooks 1 and 2 fire nothing");
    }

    @Test
    void healthyModStillCommitsDespiteNeighbourFailure() throws Exception {
        TransactionalInjectorSelfTest.Result r = TransactionalInjectorSelfTest.run();

        // Availability: a neighbour's rollback never blocks a healthy mod.
        assertTrue(r.speedCommitted(), "the healthy mod must commit despite the broken mod failing");
        assertTrue(r.healthyModRuns(), "the committed mod's hook must actually fire at runtime");
        assertEquals(21, r.healthyValue());
    }

    @Test
    void selfTestPassesEndToEnd() throws Exception {
        TransactionalInjectorSelfTest.Result r = TransactionalInjectorSelfTest.run();
        assertTrue(r.passed(), "transactional injector self-test must pass end-to-end");
        assertFalse(r.diagnostics().isEmpty(),
                "the failing hook must have produced a contained diagnostic (not a crash)");
    }
}
