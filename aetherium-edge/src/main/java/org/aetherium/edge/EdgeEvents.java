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
}
