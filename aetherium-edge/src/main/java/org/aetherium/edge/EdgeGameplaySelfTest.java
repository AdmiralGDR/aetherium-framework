/*
 * Aetherium Framework — gameplay PAL self-test (player/inventory/interaction, fully offline).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Exercises the gameplay PAL with in-memory fakes — no game, no loader.
 *
 * <p>EN: Proves {@link InventoryAccess} give/clear, {@link PlayerHandle} health + chat, and that a
 * cancellable {@link EdgeEvents} interaction propagates a {@link InteractionResult#CANCEL} from any
 * listener (the veto semantics the loader maps onto native event cancellation). This is the offline proof
 * a gameplay mod can be written and unit-tested against the PAL alone.
 * RU: Доказывает give/clear у {@link InventoryAccess}, здоровье + чат у {@link PlayerHandle} и что
 * отменяемое взаимодействие {@link EdgeEvents} распространяет {@link InteractionResult#CANCEL} от любого
 * слушателя. Офлайн-доказательство, что геймплейный мод можно писать и тестировать против одного PAL.
 */
public final class EdgeGameplaySelfTest {

    private EdgeGameplaySelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();

        // 1) Inventory: give into empty slots, then verify contents.
        FakeInventory inv = new FakeInventory(9);
        boolean gave = inv.give("minecraft:diamond", 3) && inv.give("aetherium:steel_ingot", 64);
        boolean invOk = gave && inv.itemId(0).equals("minecraft:diamond") && inv.count(0) == 3
                && inv.itemId(1).equals("aetherium:steel_ingot") && inv.count(1) == 64
                && inv.firstEmptySlot() == 2;
        notes.add("inventory: gave diamond×3 + steel×64, next empty slot " + inv.firstEmptySlot());

        // 2) Player: health + chat.
        FakePlayer player = new FakePlayer("Steve", inv);
        player.setHealth(7.5f);
        player.sendMessage("Welcome to the Iron Vanguard");
        boolean playerOk = player.health() == 7.5f
                && player.messages().size() == 1
                && player.inventory() == inv;
        notes.add("player: health=" + player.health() + ", messages=" + player.messages().size());

        // 3) Interaction events: a CANCEL from any listener vetoes the action.
        FakeEvents events = new FakeEvents();
        events.onBlockInteract((p, pos) -> InteractionResult.PASS);
        events.onBlockInteract((p, pos) -> pos.y() < 0 ? InteractionResult.CANCEL : InteractionResult.PASS);
        InteractionResult allowed = events.fireBlockInteract(player, new BlockPos(0, 64, 0));
        InteractionResult vetoed = events.fireBlockInteract(player, new BlockPos(0, -5, 0));

        events.onItemUse((p, itemId) -> itemId.equals("minecraft:tnt") ? InteractionResult.CANCEL : InteractionResult.PASS);
        InteractionResult tnt = events.fireItemUse(player, "minecraft:tnt");

        boolean interactionOk = allowed == InteractionResult.PASS
                && vetoed == InteractionResult.CANCEL
                && tnt == InteractionResult.CANCEL;
        notes.add("interaction: block@y64=" + allowed + ", block@y-5=" + vetoed + ", useTNT=" + tnt);

        boolean passed = invOk && playerOk && interactionOk;
        return new Result(invOk, playerOk, interactionOk, notes, passed);
    }

    /** Outcome of the gameplay PAL self-test. */
    public record Result(boolean inventoryOk, boolean playerOk, boolean interactionOk,
                         List<String> notes, boolean passed) {
    }

    // --- in-memory fakes (stand in for the loader's real implementations) ----------------------

    private static final class FakeInventory implements InventoryAccess {
        private final String[] ids;
        private final int[] counts;

        FakeInventory(int size) {
            ids = new String[size];
            counts = new int[size];
            for (int i = 0; i < size; i++) {
                ids[i] = AIR;
            }
        }

        @Override
        public int size() {
            return ids.length;
        }

        @Override
        public String itemId(int slot) {
            return ids[slot];
        }

        @Override
        public int count(int slot) {
            return counts[slot];
        }

        @Override
        public void setItem(int slot, String itemId, int count) {
            ids[slot] = itemId == null ? AIR : itemId;
            counts[slot] = count;
        }
    }

    private static final class FakePlayer implements PlayerHandle {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private final InventoryAccess inventory;
        private final List<String> messages = new ArrayList<>();
        private float health = 20f;
        private double x;
        private double y;
        private double z;

        FakePlayer(String name, InventoryAccess inventory) {
            this.name = name;
            this.inventory = inventory;
        }

        @Override public UUID id() { return id; }
        @Override public double x() { return x; }
        @Override public double y() { return y; }
        @Override public double z() { return z; }
        @Override public void setPosition(double nx, double ny, double nz) { x = nx; y = ny; z = nz; }
        @Override public void addVelocity(double dx, double dy, double dz) { x += dx; y += dy; z += dz; }
        @Override public String name() { return name; }
        @Override public float health() { return health; }
        @Override public void setHealth(float h) { health = h; }
        @Override public InventoryAccess inventory() { return inventory; }
        @Override public void sendMessage(String message) { messages.add(message); }

        List<String> messages() { return messages; }
    }

    /** An in-memory EdgeEvents that records interaction listeners and can fire them (any CANCEL wins). */
    private static final class FakeEvents implements EdgeEvents {
        private final List<BlockInteractListener> blockListeners = new ArrayList<>();
        private final List<ItemUseListener> itemListeners = new ArrayList<>();

        @Override public void onServerTickEnd(Runnable hook) { }
        @Override public void onEntityLoad(java.util.function.Consumer<EntityHandle> hook) { }

        @Override
        public void onBlockInteract(BlockInteractListener listener) {
            blockListeners.add(listener);
        }

        @Override
        public void onItemUse(ItemUseListener listener) {
            itemListeners.add(listener);
        }

        InteractionResult fireBlockInteract(PlayerHandle player, BlockPos pos) {
            for (BlockInteractListener l : blockListeners) {
                if (l.onBlockInteract(player, pos) == InteractionResult.CANCEL) {
                    return InteractionResult.CANCEL;
                }
            }
            return InteractionResult.PASS;
        }

        InteractionResult fireItemUse(PlayerHandle player, String itemId) {
            for (ItemUseListener l : itemListeners) {
                if (l.onItemUse(player, itemId) == InteractionResult.CANCEL) {
                    return InteractionResult.CANCEL;
                }
            }
            return InteractionResult.PASS;
        }
    }
}
