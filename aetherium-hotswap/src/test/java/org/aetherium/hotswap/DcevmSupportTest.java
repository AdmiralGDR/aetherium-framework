/*
 * Aetherium Framework — DCEVM/HotswapAgent detection tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class DcevmSupportTest {

    @Test
    void detectionIsTotalAndConsistent() {
        // On the stock CI JVM (no DCEVM/HotswapAgent, no override), structural redefine is unavailable —
        // and the call must never throw, whatever the host.
        assertNotNull(DcevmSupport.describe());
        if (!DcevmSupport.isForced() && !DcevmSupport.isDcevmPresent() && !DcevmSupport.isHotswapAgentPresent()) {
            assertFalse(DcevmSupport.structuralRedefineAvailable());
            assertFalse(new HotSwapEngine().structuralRedefineSupported());
        }
        // The engine's capability accessor mirrors DcevmSupport exactly.
        assertEquals(DcevmSupport.structuralRedefineAvailable(),
                new HotSwapEngine().structuralRedefineSupported());
    }
}
