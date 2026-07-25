/*
 * Aetherium Framework — PAL player handle (extends the entity handle with player concepts).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

/**
 * A loader-agnostic handle to a player — an {@link EntityHandle} plus name, health, inventory, and chat.
 *
 * <p>EN: Gameplay mods (factions, RPG skills, economies) need the player as a first-class concept without
 * importing {@code net.minecraft.world.entity.player.Player}. The loader implements this over the real
 * player. {@link #inventory()} returns the loader-agnostic {@link InventoryAccess}.
 * RU: Геймплейным модам нужен игрок как полноценная сущность без импорта
 * {@code net.minecraft.world.entity.player.Player}. Загрузчик реализует это поверх реального игрока.
 */
public interface PlayerHandle extends EntityHandle {

    /** The player's display name. */
    String name();

    /** Current health. */
    float health();

    /** Set health (clamped by the platform to the valid range). */
    void setHealth(float health);

    /** The player's inventory. */
    InventoryAccess inventory();

    /** Send a system/chat message to this player. */
    void sendMessage(String message);

    /**
     * Whether this player holds at least the given permission level (vanilla op levels: 0 = everyone,
     * 2 = commands, 4 = full op). lets a mod gate mixed-privilege sub-commands inside a single
     * command instead of splitting into two. Default {@code false} so a fake/test handle stays safe; the
     * loader overrides it with the real level.
     */
    default boolean hasPermission(int level) {
        return level <= 0;
    }
}
