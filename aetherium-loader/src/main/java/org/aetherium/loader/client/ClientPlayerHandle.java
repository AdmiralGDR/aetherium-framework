/*
 * Aetherium Framework — client-side PlayerHandle over the local player ().
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.aetherium.edge.InventoryAccess;
import org.aetherium.edge.PlayerHandle;
import org.aetherium.loader.NeoForgeInventoryAccess;

import java.util.UUID;

/**
 * The client-side {@link PlayerHandle} — wraps {@code Minecraft.getInstance().player} (a {@code LocalPlayer})
 * so {@link org.aetherium.edge.PlayerAccess#local()} can answer "who am I" on a client ().
 *
 * <p>EN: A near-mirror of the server-side {@code NeoForgePlayerHandle}, but over the client {@code Player}.
 * It lives in the {@code .client} package and is only referenced from {@link ClientLocalPlayer}, which is
 * itself only reached behind a {@code FMLEnvironment.dist.isClient()} guard — so a dedicated server never
 * loads this client type. The inventory reuses the shared {@link NeoForgeInventoryAccess}. {@code sendMessage}
 * shows a system message on the client's own chat; {@code hasPermission} reflects the client's known level.
 * RU: Почти зеркало серверного {@code NeoForgePlayerHandle}, но поверх клиентского {@code Player}. Живёт в
 * пакете {@code .client} и упоминается только из {@link ClientLocalPlayer}, который достижим лишь за проверкой
 * {@code FMLEnvironment.dist.isClient()} — выделенный сервер этот клиентский тип не грузит.
 */
public final class ClientPlayerHandle implements PlayerHandle {

    private final Player player;
    private final InventoryAccess inventory;

    public ClientPlayerHandle(Player player) {
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
