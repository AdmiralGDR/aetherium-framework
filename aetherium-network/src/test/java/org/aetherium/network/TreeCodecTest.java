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
        assertTrue(r.namespaceGuarded(), "an un-namespaced channel must be rejected");
    }

    @Test
    void duplicateChannelIsRejected() {
        NetworkRegistry.reset();
        TreeSyncCodec codec = new TreeSyncCodec("moda:state");
        NetworkRegistry.register(codec, p -> { });
        // A second mod registering the same channel must fail loudly rather than silently cross-talk.
        try {
            NetworkRegistry.register(new TreeSyncCodec("moda:state"), p -> { });
            throw new AssertionError("duplicate channel registration must be rejected");
        } catch (org.aetherium.core.AetheriumException expected) {
            assertTrue(expected.diagnostic().code().equals("AE-NET-CHANNEL-DUP"));
        } finally {
            NetworkRegistry.reset();
        }
    }
}
