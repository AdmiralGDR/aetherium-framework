/*
 * Aetherium Framework — delta-sync networking tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN: Verifies delta-sync reconstructs the client exactly while sending only the changed rows, and that
 * the dirty bitmap coalesces contiguous runs.
 * RU: Проверяет, что delta-sync точно восстанавливает клиента, отправляя только изменённые строки, и что
 * битовая карта объединяет непрерывные пробеги.
 */
class DeltaSyncTest {

    @Test
    void selfTestPasses() {
        DeltaSyncSelfTest.Result r = DeltaSyncSelfTest.run();
        assertTrue(r.passed(), () -> "delta-sync self-test failed: " + r.notes());
        assertTrue(r.deltaBytes() < r.fullBytes(), "delta must send fewer bytes than a full sync");
        assertTrue(r.firstSyncMatched() && r.deltaSyncMatched(), "client must match server after sync");
    }

    @Test
    void dirtyBitmapCoalescesContiguousRuns() {
        DirtyBitmap b = new DirtyBitmap(100);
        b.mark(10);
        b.mark(11);
        b.mark(12);
        b.mark(50);
        assertEquals(4, b.cardinality());

        int[] runCount = {0};
        long[] markedRows = {0};
        b.forEachRun((start, count) -> {
            runCount[0]++;
            markedRows[0] += count;
        });
        assertEquals(2, runCount[0], "rows 10-12 coalesce into one run, 50 is another");
        assertEquals(4, markedRows[0]);
    }

    @Test
    void markAllSetsEveryRow() {
        DirtyBitmap b = new DirtyBitmap(130); // spans 3 words, last partial
        b.markAll();
        assertEquals(130, b.cardinality());
        assertTrue(b.isDirty(0) && b.isDirty(129));
    }
}
