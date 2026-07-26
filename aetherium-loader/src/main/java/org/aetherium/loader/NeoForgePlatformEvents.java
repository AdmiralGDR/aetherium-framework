/*
 * Aetherium Framework — NeoForge PAL event wiring.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bridges NeoForge's game event bus into {@link NeoForgePlatformBridge}'s loader-agnostic PAL hooks.
 *
 * <p>EN: Registered on {@code NeoForge.EVENT_BUS} by the entrypoint. It captures the live server; fans
 * server-tick-end, entity-load, interaction, block-break, death/damage, chat, and player join/leave events
 * out to the {@code aetherium-edge} hook lists; and clears the server on stop. A {@link org.aetherium.edge.InteractionResult#CANCEL}
 * from any listener is mapped onto {@code event.setCanceled(true)}. Keeping these subscriptions here (not in
 * the edge SPI) preserves the PAL's purity — the edge module still references zero Minecraft types.
 *
 * <p>RU: Регистрируется на {@code NeoForge.EVENT_BUS} точкой входа. Захватывает сервер; рассылает события
 * конца тика, загрузки сущностей, взаимодействия, разрушения блоков, смерти/урона, чата и входа/выхода
 * игроков в списки хуков {@code aetherium-edge}; сбрасывает сервер при остановке. {@code CANCEL} от любого
 * слушателя отображается на {@code event.setCanceled(true)}.
 */
public final class NeoForgePlatformEvents {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        NeoForgePlatformBridge.setServer(event.getServer());
        NeoForgePlatformBridge.dispatchServerStarting();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Fire the mod hooks (save state, free native memory) BEFORE we drop the server reference.
        NeoForgePlatformBridge.dispatchServerStopping();
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

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            NeoForgePlatformBridge.dispatchPlayerJoin(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            NeoForgePlatformBridge.dispatchPlayerLeave(sp);
        }
    }

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (NeoForgePlatformBridge.dispatchBlockInteract(event.getEntity(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();
        if (NeoForgePlatformBridge.dispatchItemUse(event.getEntity(), itemId)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (NeoForgePlatformBridge.dispatchEntityAttack(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        if (NeoForgePlatformBridge.dispatchBlockBreak(event.getPlayer(), event.getPos(), blockId)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        // The placer may be any entity (player, dispenser-less mob); pass the player when it is one, else null.
        Player placer = event.getEntity() instanceof Player p ? p : null;
        String blockId = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString();
        if (NeoForgePlatformBridge.dispatchBlockPlace(placer, event.getPos(), blockId)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity killer = source == null ? null : source.getEntity();
        NeoForgePlatformBridge.dispatchEntityDeath(event.getEntity(), killer);
    }

    @SubscribeEvent
    public void onEntityDamaged(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source == null ? null : source.getEntity();
        if (NeoForgePlatformBridge.dispatchEntityDamaged(event.getEntity(), attacker, event.getAmount())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        if (NeoForgePlatformBridge.dispatchChat(event.getPlayer(), event.getRawText())) {
            event.setCanceled(true);
        }
    }
}
