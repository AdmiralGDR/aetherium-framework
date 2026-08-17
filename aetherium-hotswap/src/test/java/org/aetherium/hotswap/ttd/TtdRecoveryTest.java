/*
 * Aetherium Framework — TTD fault-recovery (restore) tests.
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

final class TtdRecoveryTest {

    @Test
    void restoreRecoversFromAFaultAndResumesCleanly() {
        StructLayout layout = StructLayout.builder().floats("v").build();
        StructField v = layout.field("v");
        try (StructArena arena = StructArena.allocate(layout, 1)) {
            TtdEngine ttd = new TtdEngine(arena, 8);
            ttd.tick((a, t) -> a.setFloat(0, v, 10f));
            ttd.tick((a, t) -> a.setFloat(0, v, 20f));
            assertEquals(20f, arena.getFloat(0, v), 1e-6f);

            // A tick that mutates the arena and THEN throws: the fault is captured, nothing commits, but the
            // live arena is left half-mutated (dirty).
            TtdEngine.TickOutcome faulted = ttd.tick((a, t) -> {
                a.setFloat(0, v, 999f);
                throw new RuntimeException("boom");
            });
            assertFalse(faulted.committed());
            assertTrue(ttd.hasFault());
            assertEquals(999f, arena.getFloat(0, v), 1e-6f, "the live arena is dirty after the fault");

            // Restore: undo the dirty tick back to the last good state and clear the fault.
            ArenaSnapshot restored = ttd.restoreToLatestCommitted();
            assertFalse(ttd.hasFault(), "the fault is cleared after restore");
            assertEquals(20f, arena.getFloat(0, v), 1e-6f, "the live arena is back to the last good state");
            assertEquals(20f, restored.getFloat(0, v), 1e-6f);

            // Resume: the journal is consistent, so a new tick commits correctly.
            TtdEngine.TickOutcome resumed = ttd.tick((a, t) -> a.setFloat(0, v, 30f));
            assertTrue(resumed.committed(), "ticking resumes cleanly after recovery");
            assertEquals(30f, arena.getFloat(0, v), 1e-6f);
        }
    }
}
