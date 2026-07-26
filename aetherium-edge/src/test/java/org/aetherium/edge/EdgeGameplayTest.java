/*
 * Aetherium Framework — gameplay PAL tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EdgeGameplayTest {

    @Test
    void gameplaySelfTestPasses() {
        EdgeGameplaySelfTest.Result r = EdgeGameplaySelfTest.run();
        assertTrue(r.passed(), () -> "edge gameplay self-test failed: " + r.notes());
        assertTrue(r.inventoryOk());
        assertTrue(r.playerOk());
        assertTrue(r.interactionOk());
        assertTrue(r.lifecycleOk());
        assertTrue(r.commandsOk());
        assertTrue(r.persistenceOk());
    }

    @Test
    void noOpBridgeDefaultsAreSafe() {
        // A bridge that overrides nothing new must still return the empty player access (no NPE).
        PlatformBridge bridge = new PlatformBridge() {
            @Override public String platformName() { return "test"; }
            @Override public EntityAccess entities() { return null; }
            @Override public LevelAccess levels() { return null; }
            @Override public EdgeEvents events() { return null; }
        };
        assertSame(PlayerAccess.EMPTY, bridge.players());
        assertEquals(0, bridge.players().count());
        assertTrue(bridge.players().byName("nobody").isEmpty());
        // : local() defaults to empty off-client / on a no-game bridge (no NPE).
        assertTrue(bridge.players().local().isEmpty());
        // The new command + persistence surfaces must also have safe defaults (no NPE, no game).
        assertSame(EdgeCommands.NONE, bridge.commands());
        assertTrue(bridge.worldStore().read("mod", "key").isEmpty());
    }
}
