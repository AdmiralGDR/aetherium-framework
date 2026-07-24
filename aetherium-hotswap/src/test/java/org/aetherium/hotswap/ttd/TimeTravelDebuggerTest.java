/*
 * Aetherium Framework — JUnit coverage for the Time-Travel Debugger (Durability).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap.ttd;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructField;
import org.aetherium.core.compute.StructLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN: Locks in the Durability pillar — a strictly bounded delta journal, byte-exact rewind, and a
 * captured-but-contained tick fault.
 * RU: Фиксирует столп долговечности — строго ограниченный журнал дельт, байт-точную перемотку и
 * пойманный, но локализованный сбой тика.
 */
final class TimeTravelDebuggerTest {

    @Test
    void selfTestPassesEndToEnd() {
        TimeTravelSelfTest.Result r = TimeTravelSelfTest.run();
        assertTrue(r.passed(), "time-travel self-test must pass end-to-end");
    }

    @Test
    void footprintStaysBoundedAcrossManyTicks() {
        TimeTravelSelfTest.Result r = TimeTravelSelfTest.run();
        assertEquals(TimeTravelSelfTest.CAPACITY, r.retainedFrames(),
                "the ring buffer retains exactly its capacity of frames");
        assertTrue(r.journalBytes() <= r.journalMaxBytes(),
                "footprint must stay under its fixed ceiling");
        assertTrue(r.journalMaxBytes() < 64 * 1024,
                "the ceiling is a small constant regardless of the " + r.ticksRun() + " ticks run");
    }

    @Test
    void faultIsCapturedButHistoryIsIntact() {
        TimeTravelSelfTest.Result r = TimeTravelSelfTest.run();
        assertTrue(r.faultCaptured(), "the crash scene must be frozen for inspection");
        assertTrue(r.historyIntactAfterFault(),
                "the faulted tick must not commit — the last-good state remains rewindable");
    }

    @Test
    void rewindReconstructsExactPastState() {
        // A direct, minimal reconstruction check independent of the self-test harness.
        StructLayout layout = StructLayout.builder().longs("v").build();
        StructField v = layout.field("v");
        try (StructArena arena = StructArena.allocate(layout, 1)) {
            arena.setLong(0, v, 0);
            TtdEngine engine = new TtdEngine(arena, 8);
            for (int t = 1; t <= 5; t++) {
                final long value = t * 10L;
                engine.tick((a, tick) -> a.setLong(0, v, value));
            }
            assertEquals(50L, engine.rewind(0).getLong(0, v), "latest state");
            assertEquals(40L, engine.rewind(1).getLong(0, v), "one tick back");
            assertEquals(10L, engine.rewind(4).getLong(0, v), "four ticks back");
            assertFalse(engine.hasFault(), "no fault occurred on the clean run");
        }
    }
}
