/*
 * Aetherium Framework — rollback-netcode equivalence tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network.sim;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructField;
import org.aetherium.core.compute.StructLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RollbackTest {

    private static StructLayout oneFloat() {
        return StructLayout.builder().floats("v").build();
    }

    @Test
    void rollbackReproducesTheCorrectInputRun() {
        StructLayout layout = oneFloat();
        StructField v = layout.field("v");
        // An accumulator: a wrong input at any tick shifts every later tick, so the present state differs.
        LockstepSim.Step accumulate = (state, tick, input) -> state.setFloat(0, v, state.getFloat(0, v) + input);

        long correctAtTick1 = 100L;
        long wrongAtTick1 = 7L;

        // Reference: a fresh sim that had the CORRECT input at tick 1 all along.
        long referenceChecksum;
        try (StructArena ref = StructArena.allocate(layout, 1)) {
            RollbackSim sim = new RollbackSim(ref, accumulate);
            sim.advance(5L);
            sim.advance(correctAtTick1);
            sim.advance(9L);
            sim.advance(3L);
            referenceChecksum = sim.checksum();
        }

        // Prediction: WRONG input at tick 1, advanced to the present, THEN corrected by rolling back.
        try (StructArena predicted = StructArena.allocate(layout, 1)) {
            RollbackSim sim = new RollbackSim(predicted, accumulate);
            sim.advance(5L);
            sim.advance(wrongAtTick1); // mispredicted
            sim.advance(9L);
            sim.advance(3L);
            assertNotEquals(referenceChecksum, sim.checksum(), "the misprediction must actually differ");

            long corrected = sim.correct(1, correctAtTick1); // rewind to tick 1, fix it, re-simulate forward
            assertEquals(referenceChecksum, corrected,
                    "rollback yields exactly the state of a run that had the correct input all along");
        }
    }

    @Test
    void correctRejectsAnOutOfRangeTick() {
        StructLayout layout = oneFloat();
        LockstepSim.Step noop = (state, tick, input) -> {
        };
        try (StructArena a = StructArena.allocate(layout, 1)) {
            RollbackSim sim = new RollbackSim(a, noop);
            sim.advance(1L);
            assertThrows(IndexOutOfBoundsException.class, () -> sim.correct(5, 0L));
            assertThrows(IndexOutOfBoundsException.class, () -> sim.correct(-1, 0L));
        }
    }
}
