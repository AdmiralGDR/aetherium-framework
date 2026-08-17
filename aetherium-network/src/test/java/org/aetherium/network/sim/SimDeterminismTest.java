/*
 * Aetherium Framework — deterministic sim + desync-detection tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network.sim;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructField;
import org.aetherium.core.compute.StructLayout;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SimDeterminismTest {

    private static StructLayout oneFloat() {
        return StructLayout.builder().floats("v").build();
    }

    @Test
    void sameInputsProduceIdenticalChecksums() {
        StructLayout layout = oneFloat();
        StructField v = layout.field("v");
        LockstepSim.Step step = (state, tick, input) -> state.setFloat(0, v, tick + input);
        long[] inputs = {5, 3, 9, 1};

        long[] run1;
        long[] run2;
        try (StructArena a = StructArena.allocate(layout, 4)) {
            run1 = LockstepSim.run(a, step, inputs);
        }
        try (StructArena b = StructArena.allocate(layout, 4)) {
            run2 = LockstepSim.run(b, step, inputs);
        }
        assertArrayEquals(run1, run2, "a deterministic sim replays bit-identically");
        assertTrue(LockstepSim.inSync(run1, run2));
        assertTrue(LockstepSim.firstDivergence(run1, run2).isEmpty());
    }

    @Test
    void nonDeterministicStepIsDetectedAsDesyncNotSilent() {
        StructLayout layout = oneFloat();
        StructField v = layout.field("v");
        // A step that reads shared mutable state (not derived from state/tick/input) is non-deterministic:
        // the two runs read different counter values, so their state — and checksums — must diverge.
        AtomicInteger external = new AtomicInteger();
        LockstepSim.Step step = (state, tick, input) -> state.setFloat(0, v, external.getAndIncrement());
        long[] inputs = {1, 2, 3};

        long[] run1;
        long[] run2;
        try (StructArena a = StructArena.allocate(layout, 4)) {
            run1 = LockstepSim.run(a, step, inputs);
        }
        try (StructArena b = StructArena.allocate(layout, 4)) {
            run2 = LockstepSim.run(b, step, inputs);
        }
        OptionalLong divergence = LockstepSim.firstDivergence(run1, run2);
        assertTrue(divergence.isPresent(), "a non-deterministic step must be caught as a desync, never silent");
        assertEquals(0L, divergence.getAsLong(), "the divergence is reported from the very first differing tick");
        assertFalse(LockstepSim.inSync(run1, run2));
    }

    @Test
    void firstDivergenceReportsTheExactTick() {
        assertTrue(LockstepSim.firstDivergence(new long[] {1, 2, 3}, new long[] {1, 2, 3}).isEmpty());
        assertEquals(2L, LockstepSim.firstDivergence(new long[] {1, 2, 9}, new long[] {1, 2, 3}).getAsLong());
        assertEquals(3L, LockstepSim.firstDivergence(new long[] {1, 2, 3}, new long[] {1, 2, 3, 4}).getAsLong());
    }

    @Test
    void checksumTracksStateExactly() {
        StructLayout layout = oneFloat();
        StructField v = layout.field("v");
        try (StructArena a = StructArena.allocate(layout, 4);
             StructArena b = StructArena.allocate(layout, 4)) {
            a.setFloat(0, v, 1.5f);
            a.setFloat(1, v, 2.5f);
            b.setFloat(0, v, 1.5f);
            b.setFloat(1, v, 2.5f);
            assertEquals(StateChecksum.of(a), StateChecksum.of(b), "identical state → identical checksum");

            b.setFloat(2, v, 9.0f);
            assertNotEquals(StateChecksum.of(a), StateChecksum.of(b), "a single changed value changes the checksum");
        }
    }
}
