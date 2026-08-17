/*
 * Aetherium Framework — fail-loud GPU dispatch facade tests (hardware-agnostic).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import org.aetherium.core.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GpuKernelsTest {

    @Test
    void dispatchIsFailLoudAndDegradesToCpu() {
        SpirvModule module = SpirvKernelBuilder.buildUnary(ComputeUnaryOp.ABS, 64);
        int n = 256;
        float[] input = new float[n];
        for (int i = 0; i < n; i++) {
            input[i] = (i % 2 == 0 ? -1f : 1f) * (i + 0.5f);
        }

        Outcome<float[]> outcome = GpuKernels.dispatchUnary(module, input);

        // Whatever the hardware: never a silent failure of a VALID kernel. Either it ran on the GPU, or it
        // REPORTED a skip with a clear reason — never null, never a hidden CPU swap.
        assertFalse(outcome.failed(), () -> "a valid kernel must not fail: " + outcome.reason());
        if (outcome.skipped()) {
            assertTrue(outcome.reason().isPresent(), "a skip must carry a reason");
            assertTrue(outcome.reason().orElseThrow().code().startsWith("AE-GPU-"),
                    () -> "the reason must name the GPU degrade: " + outcome.reason().orElseThrow().code());
        }

        // The caller degrades EXPLICITLY to the CPU tier; the final result is always correct (ABS).
        float[] result = outcome.orElseGet(() -> cpuAbs(input));
        assertEquals(n, result.length);
        for (int i = 0; i < n; i++) {
            assertEquals(Math.abs(input[i]), result[i], 1e-5f, "element " + i);
        }
    }

    @Test
    void malformedModuleFailsLoudly() {
        // A structurally-invalid module must FAIL (not skip): dispatching it would be a bug, not a degrade.
        SpirvModule empty = SpirvModule.wrap(new byte[0]);
        Outcome<float[]> outcome = GpuKernels.dispatchUnary(empty, new float[] {1f, -2f});
        assertTrue(outcome.failed(), () -> "an invalid SPIR-V module must fail loudly, got: " + outcome);
        assertEquals("AE-GPU-BADSPIRV", outcome.reason().orElseThrow().code());
    }

    private static float[] cpuAbs(float[] in) {
        float[] out = new float[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = Math.abs(in[i]);
        }
        return out;
    }
}
