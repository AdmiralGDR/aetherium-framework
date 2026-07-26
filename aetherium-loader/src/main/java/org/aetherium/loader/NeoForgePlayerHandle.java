/*
 * Aetherium Framework — NeoForge player handle (PAL implementation).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.aetherium.edge.InventoryAccess;
import org.aetherium.edge.PlayerHandle;

import java.util.UUID;

/**
 * The NeoForge-backed {@link PlayerHandle} — an {@link org.aetherium.edge.EntityHandle} plus name, health,
 * inventory, and chat, wrapping a real {@code ServerPlayer}.
 *
 * <p>EN: Like {@link NeoForgeEntityHandle}, this is loader-side — the edge SPI stays pure. Item ids cross the
 * boundary as namespaced strings ({@code "minecraft:diamond"}); the inventory adapter translates them
 * to/from {@code ItemStack}. No game type reaches the mod.
 * RU: Как и {@link NeoForgeEntityHandle}, живёт на стороне загрузчика — SPI edge остаётся чистым. Идентификаторы
 * предметов пересекают границу строками ({@code "minecraft:diamond"}); адаптер инвентаря переводит их в/из
 * {@code ItemStack}.
 */
final class NeoForgePlayerHandle implements PlayerHandle {

    private final ServerPlayer player;
    private final InventoryAccess inventory;

    NeoForgePlayerHandle(ServerPlayer player) {
        this.player = player;
        this.inventory = new NeoForgeInventoryAccess(player.getInventory());
    }

    @Override
    public UUID id() {
        return player.getUUID();
    }

    @Override
    public double x() {
        return player.getX();
    }

    @Override
    public double y() {
        return player.getY();
    }

    @Override
    public double z() {
        return player.getZ();
    }

    @Override
    public void setPosition(double x, double y, double z) {
        player.setPos(x, y, z);
    }

    @Override
    public void addVelocity(double dx, double dy, double dz) {
        player.setDeltaMovement(player.getDeltaMovement().add(dx, dy, dz));
    }

    @Override
    public String name() {
        return player.getName().getString();
    }

    @Override
    public float health() {
        return player.getHealth();
    }

    @Override
    public void setHealth(float health) {
        player.setHealth(health);
    }

    @Override
    public InventoryAccess inventory() {
        return inventory;
    }

    @Override
    public void sendMessage(String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public boolean hasPermission(int level) {
        return player.hasPermissions(level);
    }
}
