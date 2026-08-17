/*
 * Aetherium Framework — FrameClock tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.tick;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FrameClockTest {

    @Test
    void manualClockStartsAtZeroAndAdvancesForward() {
        FrameClock.Manual clock = new FrameClock.Manual();
        assertEquals(0L, clock.nanos());
        clock.advance(1_500_000L);
        assertEquals(1_500_000L, clock.nanos());
        assertEquals(1L, clock.millis(), "millis is integer nanos/1e6");
        clock.advanceMillis(2L);
        assertEquals(3_500_000L, clock.nanos());
    }

    @Test
    void manualClockHonoursAStartOffset() {
        FrameClock.Manual clock = new FrameClock.Manual(1_000L);
        assertEquals(1_000L, clock.nanos());
    }

    @Test
    void manualClockIsMonotonic() {
        FrameClock.Manual clock = new FrameClock.Manual();
        clock.advance(10L);
        assertThrows(IllegalArgumentException.class, () -> clock.advance(-1L),
                "the clock is monotonic — it must reject moving backwards");
        assertEquals(10L, clock.nanos(), "a rejected advance leaves time unchanged");
    }

    @Test
    void systemClockIsMonotonicNonDecreasing() {
        FrameClock clock = FrameClock.system();
        long a = clock.nanos();
        long b = clock.nanos();
        assertTrue(b >= a, "System.nanoTime is monotonic non-decreasing");
    }
}
