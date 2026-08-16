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

    @Test
    void directionalNetworkAndSideModelSelfTestPasses() {
        // + sidedness: serverbound round-trip with a sender, rate limit, size cap, send facade,
        // and the side gating — all offline.
        NetworkSelfTest.Result r = NetworkSelfTest.run();
        assertTrue(r.passed(), () -> "network self-test failed: " + r.notes());
        assertTrue(r.roundTripOk(), "serverbound round-trip must deliver with the sender present");
        assertTrue(r.rateLimited(), "a flood from one sender must be rate-limited");
        assertTrue(r.sizeCapOk(), "an oversized payload must be rejected");
        assertTrue(r.sendFacadeOk(), "the send facade must route each direction");
        assertTrue(r.sideModelOk(), "a CLIENT feature must be gated off a dedicated server");
    }

    @Test
    void floodGuardReclaimsIdleBucketsWithoutLosingState() {
        // The per-sender flood guard must not itself become a memory-exhaustion vector: once its bucket map
        // grows past the high-water mark, fully-refilled (idle) senders are swept. A fully-refilled bucket is
        // identical to a never-seen one, so this loses no decision — a reclaimed sender is still allowed.
        ServerboundGuard guard = new ServerboundGuard(); // burst 32, refill 16/s
        String channel = "examplemod:admin";
        long t0 = 1_000L;
        int senders = ServerboundGuard.SWEEP_THRESHOLD + 2; // cross the high-water mark

        for (int i = 0; i < senders; i++) {
            assertTrue(guard.allow(channel, new java.util.UUID(0L, i), t0),
                    "a fresh sender's first packet must be allowed");
        }
        assertTrue(guard.trackedBuckets() > ServerboundGuard.SWEEP_THRESHOLD,
                "every distinct sender should hold a bucket before an idle sweep");

        // Advance well past a full refill (32 tokens / 16 per sec = 2 s), then one more packet from a fresh
        // sender crosses the threshold inside allow() and triggers the sweep of the now-idle buckets.
        long later = t0 + 10_000L;
        assertTrue(guard.allow(channel, new java.util.UUID(1L, 0L), later));
        assertTrue(guard.trackedBuckets() < 100,
                () -> "idle (fully refilled) buckets must be reclaimed, but " + guard.trackedBuckets() + " remain");

        // Losslessness: a reclaimed sender that returns is treated as fresh — allowed, never wrongly throttled.
        assertTrue(guard.allow(channel, new java.util.UUID(0L, 0L), later),
                "a reclaimed idle sender must still be allowed (evicting a full bucket loses no state)");
    }

    @Test
    void selectedSlotDefaultsAreSafeOffPlatform() {
        // no selection concept off-platform — selectedSlot() is -1 and heldItemId() is AIR, and a
        // populated inventory still resolves the held item from selectedSlot() once a loader overrides it.
        assertEquals(-1, InventoryAccess.EMPTY.selectedSlot());
        assertEquals(InventoryAccess.AIR, InventoryAccess.EMPTY.heldItemId());

        // A tiny fake inventory that reports slot 1 as selected returns that slot's id from heldItemId().
        InventoryAccess held = new InventoryAccess() {
            private final String[] items = {"minecraft:air", "minecraft:diamond_sword"};

            @Override public int size() { return items.length; }
            @Override public String itemId(int slot) { return items[slot]; }
            @Override public int count(int slot) { return AIR.equals(items[slot]) ? 0 : 1; }
            @Override public void setItem(int slot, String itemId, int count) { items[slot] = itemId; }
            @Override public int selectedSlot() { return 1; }
        };
        assertEquals(1, held.selectedSlot());
        assertEquals("minecraft:diamond_sword", held.heldItemId());
    }

    @Test
    void installForTestingMakesTheLocalPlayerPresentBranchReachable() {
        // without a test hook, players().local() is always empty headless, so a code path that
        // reads the local player is only ever testable in its absent branch. installForTesting presents one.
        assertTrue(Platform.bridge().players().local().isEmpty(), "no local player by default (no game)");

        PlayerHandle fakePlayer = new FakePlayer();
        PlayerAccess withLocal = new PlayerAccess() {
            @Override public java.util.Optional<PlayerHandle> byId(java.util.UUID id) { return java.util.Optional.empty(); }
            @Override public java.util.Optional<PlayerHandle> byName(String name) { return java.util.Optional.empty(); }
            @Override public java.util.List<PlayerHandle> online() { return java.util.List.of(); }
            @Override public java.util.Optional<PlayerHandle> local() {
                return java.util.Optional.of(fakePlayer);
            }
        };
        PlatformBridge fake = new PlatformBridge() {
            @Override public String platformName() { return "test"; }
            @Override public EntityAccess entities() { return null; }
            @Override public LevelAccess levels() { return null; }
            @Override public EdgeEvents events() { return null; }
            @Override public PlayerAccess players() { return withLocal; }
        };
        try {
            Platform.installForTesting(fake);
            assertTrue(Platform.bridge().players().local().isPresent(), "the hook presents a local player");
            assertEquals("Tester", Platform.bridge().players().local().orElseThrow().name());
        } finally {
            Platform.installForTesting(null); // restore the ServiceLoader default for every other test
        }
        assertTrue(Platform.bridge().players().local().isEmpty(), "restored: no local player after teardown");
    }

    /** A minimal fake player for the hook test. */
    private static final class FakePlayer implements PlayerHandle {

        @Override public java.util.UUID id() { return new java.util.UUID(0L, 1L); }
        @Override public double x() { return 0; }
        @Override public double y() { return 0; }
        @Override public double z() { return 0; }
        @Override public void setPosition(double x, double y, double z) { }
        @Override public void addVelocity(double dx, double dy, double dz) { }
        @Override public String name() { return "Tester"; }
        @Override public float health() { return 20f; }
        @Override public void setHealth(float health) { }
        @Override public InventoryAccess inventory() { return InventoryAccess.EMPTY; }
        @Override public void sendMessage(String message) { }
    }
}
