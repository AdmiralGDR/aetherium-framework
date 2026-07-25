/*
 * Aetherium Framework — GPU compute dispatch test (WS-6).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ComputeGpuSelfTestTest {

    @Test
    void gpuDispatchMatchesCpuOrDegradesCleanly() {
        // Passes either way: if a Vulkan device is present the GPU result must equal the CPU one to the bit
        // (ABS is exact); if not, the dispatch returns null and the framework degrades to CPU — also a pass.
        ComputeGpuSelfTest.Result r = ComputeGpuSelfTest.run();
        assertTrue(r.ran(), "the kernel must at least be assembled + dispatched: " + r.note());
        assertTrue(r.passed(), () -> "GPU/CPU compute mismatch: " + r.note());
        if (r.gpuUsed()) {
            assertTrue(r.maxDiff() < 1e-5, () -> "GPU diverged from CPU: maxDiff=" + r.maxDiff());
        }
    }
}
