/*
 * Aetherium Framework — PAL event hooks.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.function.Consumer;

/**
 * Loader-agnostic lifecycle/event hooks — the cross-loader event surface.
 *
 * <p>EN: A minimal, stable set of hooks an Aetherium mod registers against, instead of NeoForge's
 * {@code @SubscribeEvent} or Fabric's callbacks. The loader maps each hook onto its native event
 * system. {@code onServerTickEnd} is the natural place to commit Aetherium's parallel-tick results
 * back to the game (after the Sync Barrier).
 *
 * <p>RU: Минимальный стабильный набор хуков, на которые подписывается мод Aetherium, вместо
 * {@code @SubscribeEvent} NeoForge или колбэков Fabric. Загрузчик отображает каждый хук на свою
 * нативную систему событий. {@code onServerTickEnd} — естественное место для коммита результатов
 * параллельного тика Aetherium обратно в игру (после Sync-барьера).
 */
public interface EdgeEvents {

    /** Run {@code hook} at the end of every server tick (after Aetherium's Sync Barrier). */
    void onServerTickEnd(Runnable hook);

    /** Run {@code hook} when an entity is loaded into the world. */
    void onEntityLoad(Consumer<EntityHandle> hook);

    // --- gameplay interaction events (cancellable) --------------------------------------------
    // Defaults are no-ops so an existing PlatformBridge keeps compiling; the loader overrides them
    // and maps a CANCEL result onto cancelling the corresponding native event.

    /** Run {@code listener} when a player right-clicks a block; return CANCEL to veto vanilla use. */
    default void onBlockInteract(BlockInteractListener listener) {
        // no-op by default
    }

    /** Run {@code listener} when a player uses an item; return CANCEL to veto vanilla use. */
    default void onItemUse(ItemUseListener listener) {
        // no-op by default
    }

    /** Run {@code listener} when a player attacks an entity; return CANCEL to veto the attack. */
    default void onEntityAttack(EntityAttackListener listener) {
        // no-op by default
    }

    // --- gameplay lifecycle events (the events most gameplay mods are actually built on) --------
    // All default to no-ops so existing bridges keep compiling; the loader overrides each one.

    /**
     * Run {@code listener} when a block is broken; return CANCEL to veto the break. The listener is told
     * whether the block was placed by a player (loader-supplied where the platform tracks it), so a
     * mining-combo mechanic can ignore player-placed blocks.
     */
    default void onBlockBreak(BlockBreakListener listener) {
        // no-op by default
    }

    /**
     * Run {@code listener} when a block enters the world (placed by a player or an entity); return CANCEL to
     * veto the placement. The counterpart to {@link #onBlockBreak} — lets a mod react the moment its own block
     * appears (arm it, register it, spawn its block entity's peers) instead of waiting for a first interaction.
     */
    default void onBlockPlace(BlockPlaceListener listener) {
        // no-op by default
    }

    /** Run {@code listener} when an entity dies (e.g. to award progression on a kill). */
    default void onEntityDeath(EntityDeathListener listener) {
        // no-op by default
    }

    /** Run {@code listener} when an entity takes damage; return CANCEL to veto the damage. */
    default void onEntityDamaged(EntityDamagedListener listener) {
        // no-op by default
    }

    /** Run {@code hook} when a player joins — the natural moment to send them initial state. */
    default void onPlayerJoin(Consumer<PlayerHandle> hook) {
        // no-op by default
    }

    /** Run {@code hook} when a player leaves. */
    default void onPlayerLeave(Consumer<PlayerHandle> hook) {
        // no-op by default
    }

    /** Run {@code listener} on a chat message; return CANCEL to suppress it. */
    default void onChatMessage(ChatListener listener) {
        // no-op by default
    }

    /** Run {@code hook} as the server is starting — load persistent state ({@link WorldStore}) here. */
    default void onServerStarting(Runnable hook) {
        // no-op by default
    }

    /** Run {@code hook} as the server is stopping — SAVE state and free native memory here. */
    default void onServerStopping(Runnable hook) {
        // no-op by default
    }

    /** A player right-clicked a block at {@code pos}. */
    @FunctionalInterface
    interface BlockInteractListener {
        InteractionResult onBlockInteract(PlayerHandle player, BlockPos pos);
    }

    /** A player used the item {@code itemId} (namespaced registry id). */
    @FunctionalInterface
    interface ItemUseListener {
        InteractionResult onItemUse(PlayerHandle player, String itemId);
    }

    /** A player attacked the entity {@code target}. */
    @FunctionalInterface
    interface EntityAttackListener {
        InteractionResult onEntityAttack(PlayerHandle attacker, EntityHandle target);
    }

    /** A block {@code blockId} at {@code pos} is being broken; {@code playerPlaced} if it was player-placed. */
    @FunctionalInterface
    interface BlockBreakListener {
        InteractionResult onBlockBreak(PlayerHandle player, BlockPos pos, String blockId, boolean playerPlaced);
    }

    /**
     * A block {@code blockId} was placed at {@code pos} by {@code player} (the placing player, or {@code null}
     * when an entity/dispenser placed it). Return CANCEL to veto the placement.
     */
    @FunctionalInterface
    interface BlockPlaceListener {
        InteractionResult onBlockPlace(PlayerHandle player, BlockPos pos, String blockId);
    }

    /** The entity {@code victim} died; {@code killer} may be null (e.g. environmental death). */
    @FunctionalInterface
    interface EntityDeathListener {
        void onEntityDeath(EntityHandle victim, EntityHandle killer);
    }

    /** The entity {@code victim} took {@code amount} damage; {@code attacker} may be null. */
    @FunctionalInterface
    interface EntityDamagedListener {
        InteractionResult onEntityDamaged(EntityHandle victim, EntityHandle attacker, float amount);
    }

    /** A player sent chat {@code message}. */
    @FunctionalInterface
    interface ChatListener {
        InteractionResult onChatMessage(PlayerHandle player, String message);
    }
}
