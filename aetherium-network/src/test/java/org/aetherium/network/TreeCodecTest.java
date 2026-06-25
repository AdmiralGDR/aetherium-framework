/*
 * Aetherium Framework — TreeCodec tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TreeCodecTest {

    @Test
    void hierarchicalRoundTripAndHardening() {
        TreeSyncSelfTest.Result r = TreeSyncSelfTest.run();
        assertTrue(r.passed(), () -> "tree-sync self-test failed: " + r.notes());
        assertTrue(r.roundTripOk(), "faction tree must round-trip byte-exact");
        assertTrue(r.accessorsOk(), "typed accessors must read decoded values");
        assertTrue(r.depthGuarded(), "an over-deep tree must be rejected");
    }
}
