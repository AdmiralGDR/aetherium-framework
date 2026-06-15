/*
 * Aetherium Framework — NeoForge PAL event wiring.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bridges NeoForge's game event bus into {@link NeoForgePlatformBridge}'s loader-agnostic PAL hooks.
 *
 * <p>EN: Registered on {@code NeoForge.EVENT_BUS} by the entrypoint. It captures the live server, fans
 * server-tick-end and entity-load events out to the {@code aetherium-edge} hook lists, and clears the
 * server on stop. Keeping these subscriptions here (not in the edge SPI) preserves the PAL's purity.
 *
 * <p>RU: Регистрируется на {@code NeoForge.EVENT_BUS} точкой входа. Захватывает активный сервер,
 * рассылает события конца тика и загрузки сущностей в списки хуков {@code aetherium-edge} и сбрасывает
 * сервер при остановке. Размещение подписок здесь (а не в SPI edge) сохраняет чистоту PAL.
 */
public final class NeoForgePlatformEvents {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        NeoForgePlatformBridge.setServer(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        NeoForgePlatformBridge.clearServer();
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        NeoForgePlatformBridge.dispatchTickEnd();
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            NeoForgePlatformBridge.dispatchEntityLoad(event.getEntity());
        }
    }
}
