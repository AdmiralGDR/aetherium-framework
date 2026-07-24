/*
 * Aetherium Framework — JUnit coverage for the FFM zero-leak audit (capital debugging).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import org.aetherium.core.compute.ArenaAuditor;
import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN: Locks in the zero-leak guarantee — the ledger balances byte-for-byte over a million-entity
 * churn on virtual threads, and NMT (when enabled) shows no native residue.
 * RU: Фиксирует гарантию нулевых утечек — реестр сходится байт-в-байт на прогоне миллиона сущностей
 * на виртуальных потоках, а NMT (когда включён) не показывает нативного осадка.
 */
final class FfmLeakTest {

    @Test
    void millionEntityChurnLeaksNothing() {
        FfmLeakHarness.Report r = FfmLeakHarness.run(1_000_000L);
        assertEquals(0, r.failures(), "no arena task may fail");
        assertTrue(r.ledgerBalanced(), "every arena opened in the window must be closed, byte for byte");
        assertTrue(r.ledgerExact(), "allocated == freed == arenas x arenaBytes, exactly: " + r.ledgerDelta());
        assertTrue(r.passed(), "the audit's own verdict must be PASS: " + r.notes());
        if (r.nmtAvailable()) {
            assertTrue(Math.abs(r.nmtOtherDeltaKb()) <= FfmLeakHarness.NMT_TOLERANCE_KB,
                    "NMT 'Other' (FFM) category must return to baseline: delta " + r.nmtOtherDeltaKb() + " KB");
        }
    }

    @Test
    void ledgerRecordsCloseExactlyOnceEvenOnDoubleClose() {
        StructLayout layout = StructLayout.builder().longs("v").build();
        ArenaAuditor.Snapshot before = ArenaAuditor.snapshot();
        StructArena arena = StructArena.allocate(layout, 8);
        arena.close();
        try {
            arena.close(); // shared arenas throw on double-close; the ledger must not double-count
        } catch (IllegalStateException expected) {
            // contained — the point is the ledger below
        }
        ArenaAuditor.Snapshot delta = ArenaAuditor.snapshot().since(before);
        assertEquals(1, delta.arenasOpened());
        assertEquals(1, delta.arenasClosed());
        assertTrue(delta.balanced(), "double-close must not unbalance the ledger");
    }
}
