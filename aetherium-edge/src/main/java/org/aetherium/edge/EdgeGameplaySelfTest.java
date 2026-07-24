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

        // 4) New gameplay lifecycle events: block-break (with player-placed flag) + player-join.
        List<String> joined = new ArrayList<>();
        events.onPlayerJoin(p -> joined.add(p.name()));
        events.firePlayerJoin(player);
        events.onBlockBreak((p, pos, blockId, playerPlaced) ->
                playerPlaced ? InteractionResult.PASS : InteractionResult.CANCEL);
        InteractionResult breakNatural = events.fireBlockBreak(player, new BlockPos(0, 12, 0), "minecraft:diamond_ore", false);
        InteractionResult breakPlaced = events.fireBlockBreak(player, new BlockPos(0, 12, 0), "minecraft:cobblestone", true);
        boolean lifecycleOk = joined.equals(List.of("Steve"))
                && breakNatural == InteractionResult.CANCEL && breakPlaced == InteractionResult.PASS;
        notes.add("lifecycle: joined=" + joined + ", break(natural)=" + breakNatural + ", break(placed)=" + breakPlaced);

        // 5) Commands: register a /faction command and run it through a fake command surface.
        FakeCommands commands = new FakeCommands();
        commands.register("faction", EdgeCommands.CommandSpec.of(2, "faction admin", EdgeCommands.ArgType.WORD),
                (sender, args) -> args.equals(List.of("info")) ? InteractionResult.PASS : InteractionResult.CANCEL);
        InteractionResult cmdOk = commands.run("faction", player, List.of("info"));
        InteractionResult cmdBad = commands.run("faction", player, List.of("nope"));
        boolean commandsOk = commands.spec("faction").permissionLevel() == 2
                && cmdOk == InteractionResult.PASS && cmdBad == InteractionResult.CANCEL;
        notes.add("commands: /faction perm=" + commands.spec("faction").permissionLevel()
                + ", run(info)=" + cmdOk + ", run(nope)=" + cmdBad);

        // 6) Persistence: round-trip a faction document through the in-memory WorldStore.
        WorldStore store = WorldStore.inMemory();
        org.aetherium.network.TreeNode doc = org.aetherium.network.Tree.object()
                .put("essence", 42L).put("leader", "Steve").build();
        store.write("examplemod", "faction/iron_vanguard", doc);
        var reloaded = store.read("examplemod", "faction/iron_vanguard");
        boolean persistenceOk = reloaded.isPresent() && reloaded.get().equals(doc)
                && store.read("examplemod", "missing").isEmpty();
        notes.add("persistence: wrote+read faction doc equal=" + (reloaded.isPresent() && reloaded.get().equals(doc)));

        boolean passed = invOk && playerOk && interactionOk && lifecycleOk && commandsOk && persistenceOk;
        return new Result(invOk, playerOk, interactionOk, lifecycleOk, commandsOk, persistenceOk, notes, passed);
    }

    /** Outcome of the gameplay PAL self-test. */
    public record Result(boolean inventoryOk, boolean playerOk, boolean interactionOk,
                         boolean lifecycleOk, boolean commandsOk, boolean persistenceOk,
                         List<String> notes, boolean passed) {
    }

    /** An in-memory EdgeCommands recording registrations and running them by name. */
    private static final class FakeCommands implements EdgeCommands {
        private final java.util.Map<String, CommandSpec> specs = new java.util.LinkedHashMap<>();
        private final java.util.Map<String, CommandHandler> handlers = new java.util.LinkedHashMap<>();

        @Override
        public void register(String name, CommandSpec spec, CommandHandler handler) {
            specs.put(name, spec);
            handlers.put(name, handler);
        }

        CommandSpec spec(String name) {
            return specs.get(name);
        }

        InteractionResult run(String name, PlayerHandle sender, List<String> args) {
            return handlers.get(name).run(sender, args);
        }
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
        private final List<BlockBreakListener> breakListeners = new ArrayList<>();
        private final List<java.util.function.Consumer<PlayerHandle>> joinListeners = new ArrayList<>();

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

        @Override
        public void onBlockBreak(BlockBreakListener listener) {
            breakListeners.add(listener);
        }

        @Override
        public void onPlayerJoin(java.util.function.Consumer<PlayerHandle> hook) {
            joinListeners.add(hook);
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

        InteractionResult fireBlockBreak(PlayerHandle player, BlockPos pos, String blockId, boolean playerPlaced) {
            for (BlockBreakListener l : breakListeners) {
                if (l.onBlockBreak(player, pos, blockId, playerPlaced) == InteractionResult.CANCEL) {
                    return InteractionResult.CANCEL;
                }
            }
            return InteractionResult.PASS;
        }

        void firePlayerJoin(PlayerHandle player) {
            joinListeners.forEach(l -> l.accept(player));
        }
    }
}
