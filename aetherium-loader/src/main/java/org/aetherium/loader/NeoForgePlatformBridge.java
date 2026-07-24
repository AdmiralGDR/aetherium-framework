/*
 * Aetherium Framework — NeoForge PAL implementation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.aetherium.edge.EdgeCommands;
import org.aetherium.edge.EdgeEvents;
import org.aetherium.edge.EntityAccess;
import org.aetherium.edge.EntityHandle;
import org.aetherium.edge.InteractionResult;
import org.aetherium.edge.LevelAccess;
import org.aetherium.edge.LevelContext;
import org.aetherium.edge.PlatformBridge;
import org.aetherium.edge.PlayerAccess;
import org.aetherium.edge.PlayerHandle;
import org.aetherium.edge.WorldStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The NeoForge-backed {@link PlatformBridge} — the loader-side implementation of the Platform
 * Abstraction Layer defined (purely) in {@code aetherium-edge}.
 *
 * <p>EN: Discovered via {@code ServiceLoader} ({@code META-INF/services/org.aetherium.edge.PlatformBridge})
 * so {@code Platform.bridge()} returns it at runtime; outside the game (or before a server starts) the
 * edge module's no-op bridge is used instead. The live {@link MinecraftServer} and the user hook lists
 * are static state fed by {@link NeoForgePlatformEvents}; entity access walks the server's levels using the
 * stable {@code getAllEntities()/getEntity(UUID)} API. Every mod hook is dispatched inside a try/catch so a
 * misbehaving mod can never break a server tick or an event (graceful-degradation rule). This and
 * {@link NeoForgeEntityHandle}/{@link NeoForgePlayerHandle} are the only places that touch Minecraft types —
 * the edge SPI stays pure.
 *
 * <p>RU: Обнаруживается через {@code ServiceLoader}, поэтому {@code Platform.bridge()} возвращает его в
 * рантайме; вне игры используется no-op мост из модуля edge. Текущий {@link MinecraftServer} и списки
 * пользовательских хуков — статическое состояние, наполняемое {@link NeoForgePlatformEvents}. Каждый хук
 * мода вызывается внутри try/catch — сбойный мод не ломает тик и не роняет событие.
 */
public final class NeoForgePlatformBridge implements PlatformBridge {

    private static volatile MinecraftServer server;

    // --- user hook lists, fed by the edge SPI and fired by NeoForgePlatformEvents -------------------
    private static final CopyOnWriteArrayList<Runnable> TICK_END_HOOKS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<EntityHandle>> ENTITY_LOAD_HOOKS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<EdgeEvents.BlockInteractListener> BLOCK_INTERACT = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<EdgeEvents.ItemUseListener> ITEM_USE = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<EdgeEvents.EntityAttackListener> ENTITY_ATTACK = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<EdgeEvents.BlockBreakListener> BLOCK_BREAK = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<EdgeEvents.EntityDeathListener> ENTITY_DEATH = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<EdgeEvents.EntityDamagedListener> ENTITY_DAMAGED = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<EdgeEvents.ChatListener> CHAT = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<PlayerHandle>> PLAYER_JOIN = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<PlayerHandle>> PLAYER_LEAVE = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> SERVER_STARTING = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> SERVER_STOPPING = new CopyOnWriteArrayList<>();

    private final EntityAccess entities = new NeoForgeEntityAccess();
    private final LevelAccess levels = new NeoForgeLevelAccess();
    private final EdgeEvents events = new NeoForgeEdgeEvents();
    private final PlayerAccess players = new NeoForgePlayerAccess();

    /** Public no-arg constructor for {@code ServiceLoader}. */
    public NeoForgePlatformBridge() {
    }

    // --- static lifecycle, driven by NeoForgePlatformEvents (negative-trust: hooks never escape) ---

    static void setServer(MinecraftServer activeServer) {
        server = activeServer;
    }

    static void clearServer() {
        server = null;
    }

    static MinecraftServer server() {
        return server;
    }

    private static <T> void safe(Iterable<T> hooks, Consumer<T> call) {
        for (T hook : hooks) {
            try {
                call.accept(hook);
            } catch (Throwable ignored) {
                // A misbehaving mod hook must never break the server tick or event.
            }
        }
    }

    static void dispatchTickEnd() {
        safe(TICK_END_HOOKS, Runnable::run);
    }

    static void dispatchEntityLoad(Entity entity) {
        if (ENTITY_LOAD_HOOKS.isEmpty() || entity == null) {
            return;
        }
        final EntityHandle handle = new NeoForgeEntityHandle(entity);
        safe(ENTITY_LOAD_HOOKS, hook -> hook.accept(handle));
    }

    /** Fire block-interact hooks; returns true if any listener vetoed (CANCEL). */
    static boolean dispatchBlockInteract(Player player, BlockPos pos) {
        if (BLOCK_INTERACT.isEmpty() || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        final PlayerHandle handle = new NeoForgePlayerHandle(sp);
        final org.aetherium.edge.BlockPos p = new org.aetherium.edge.BlockPos(pos.getX(), pos.getY(), pos.getZ());
        return anyCancel(BLOCK_INTERACT, l -> l.onBlockInteract(handle, p));
    }

    static boolean dispatchItemUse(Player player, String itemId) {
        if (ITEM_USE.isEmpty() || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        final PlayerHandle handle = new NeoForgePlayerHandle(sp);
        return anyCancel(ITEM_USE, l -> l.onItemUse(handle, itemId));
    }

    static boolean dispatchEntityAttack(Player attacker, Entity target) {
        if (ENTITY_ATTACK.isEmpty() || !(attacker instanceof ServerPlayer sp) || target == null) {
            return false;
        }
        final PlayerHandle handle = new NeoForgePlayerHandle(sp);
        final EntityHandle t = new NeoForgeEntityHandle(target);
        return anyCancel(ENTITY_ATTACK, l -> l.onEntityAttack(handle, t));
    }

    static boolean dispatchBlockBreak(Player player, BlockPos pos, String blockId) {
        if (BLOCK_BREAK.isEmpty() || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        final PlayerHandle handle = new NeoForgePlayerHandle(sp);
        final org.aetherium.edge.BlockPos p = new org.aetherium.edge.BlockPos(pos.getX(), pos.getY(), pos.getZ());
        // Vanilla does not track "was this block player-placed"; a mod that needs it must track placements
        // itself (via onBlockInteract). We report false rather than guess.
        return anyCancel(BLOCK_BREAK, l -> l.onBlockBreak(handle, p, blockId, false));
    }

    static void dispatchEntityDeath(LivingEntity victim, Entity killer) {
        if (ENTITY_DEATH.isEmpty() || victim == null) {
            return;
        }
        final EntityHandle v = new NeoForgeEntityHandle(victim);
        final EntityHandle k = killer == null ? null : new NeoForgeEntityHandle(killer);
        safe(ENTITY_DEATH, l -> l.onEntityDeath(v, k));
    }

    static boolean dispatchEntityDamaged(LivingEntity victim, Entity attacker, float amount) {
        if (ENTITY_DAMAGED.isEmpty() || victim == null) {
            return false;
        }
        final EntityHandle v = new NeoForgeEntityHandle(victim);
        final EntityHandle a = attacker == null ? null : new NeoForgeEntityHandle(attacker);
        return anyCancel(ENTITY_DAMAGED, l -> l.onEntityDamaged(v, a, amount));
    }

    static boolean dispatchChat(ServerPlayer player, String message) {
        if (CHAT.isEmpty() || player == null) {
            return false;
        }
        final PlayerHandle handle = new NeoForgePlayerHandle(player);
        return anyCancel(CHAT, l -> l.onChatMessage(handle, message));
    }

    static void dispatchPlayerJoin(ServerPlayer player) {
        if (PLAYER_JOIN.isEmpty() || player == null) {
            return;
        }
        final PlayerHandle handle = new NeoForgePlayerHandle(player);
        safe(PLAYER_JOIN, hook -> hook.accept(handle));
    }

    static void dispatchPlayerLeave(ServerPlayer player) {
        if (PLAYER_LEAVE.isEmpty() || player == null) {
            return;
        }
        final PlayerHandle handle = new NeoForgePlayerHandle(player);
        safe(PLAYER_LEAVE, hook -> hook.accept(handle));
    }

    static void dispatchServerStarting() {
        safe(SERVER_STARTING, Runnable::run);
    }

    static void dispatchServerStopping() {
        safe(SERVER_STOPPING, Runnable::run);
    }

    /** Run every cancellable listener; return true if any returned {@link InteractionResult#CANCEL}. */
    private static <T> boolean anyCancel(Iterable<T> listeners, java.util.function.Function<T, InteractionResult> call) {
        boolean cancel = false;
        for (T l : listeners) {
            try {
                if (call.apply(l) == InteractionResult.CANCEL) {
                    cancel = true;
                }
            } catch (Throwable ignored) {
                // Contain listener failures — a broken mod never vetoes by accident nor crashes the event.
            }
        }
        return cancel;
    }

    @Override
    public String platformName() {
        return "neoforge";
    }

    @Override
    public boolean isGameAvailable() {
        return server != null;
    }

    @Override
    public EntityAccess entities() {
        return entities;
    }

    @Override
    public LevelAccess levels() {
        return levels;
    }

    @Override
    public EdgeEvents events() {
        return events;
    }

    @Override
    public PlayerAccess players() {
        return players;
    }

    @Override
    public EdgeCommands commands() {
        return NeoForgeCommandBridge.commands();
    }

    @Override
    public WorldStore worldStore() {
        NeoForgeWorldStore store = NeoForgeWorldStore.forServer(server);
        return store != null ? store : WorldStore.inMemory();
    }

    /** Entity access over every loaded level of the active dedicated/integrated server. */
    private static final class NeoForgeEntityAccess implements EntityAccess {

        @Override
        public Optional<EntityHandle> byId(UUID id) {
            final MinecraftServer s = server;
            if (s == null || id == null) {
                return Optional.empty();
            }
            for (ServerLevel level : s.getAllLevels()) {
                final Entity e = level.getEntity(id);
                if (e != null) {
                    return Optional.of(new NeoForgeEntityHandle(e));
                }
            }
            return Optional.empty();
        }

        @Override
        public void forEach(Consumer<EntityHandle> action) {
            final MinecraftServer s = server;
            if (s == null || action == null) {
                return;
            }
            for (ServerLevel level : s.getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    action.accept(new NeoForgeEntityHandle(e));
                }
            }
        }

        @Override
        public int count() {
            final MinecraftServer s = server;
            if (s == null) {
                return 0;
            }
            int n = 0;
            for (ServerLevel level : s.getAllLevels()) {
                for (Entity ignored : level.getAllEntities()) {
                    n++;
                }
            }
            return n;
        }
    }

    /** Block PAL access over every loaded level of the active server (the world-side of {@link EntityAccess}). */
    private static final class NeoForgeLevelAccess implements LevelAccess {

        @Override
        public Optional<LevelContext> primary() {
            final MinecraftServer s = server;
            return s == null ? Optional.empty() : Optional.of(new NeoForgeLevelContext(s.overworld()));
        }

        @Override
        public Optional<LevelContext> byDimension(String dimensionId) {
            final MinecraftServer s = server;
            if (s == null || dimensionId == null) {
                return Optional.empty();
            }
            for (ServerLevel level : s.getAllLevels()) {
                if (level.dimension().location().toString().equals(dimensionId)) {
                    return Optional.of(new NeoForgeLevelContext(level));
                }
            }
            return Optional.empty();
        }

        @Override
        public void forEach(Consumer<LevelContext> action) {
            final MinecraftServer s = server;
            if (s == null || action == null) {
                return;
            }
            for (ServerLevel level : s.getAllLevels()) {
                action.accept(new NeoForgeLevelContext(level));
            }
        }

        @Override
        public int count() {
            final MinecraftServer s = server;
            if (s == null) {
                return 0;
            }
            int n = 0;
            for (ServerLevel ignored : s.getAllLevels()) {
                n++;
            }
            return n;
        }
    }

    /** Player access over the active server's player list. */
    private static final class NeoForgePlayerAccess implements PlayerAccess {

        @Override
        public Optional<PlayerHandle> byId(UUID id) {
            final MinecraftServer s = server;
            if (s == null || id == null) {
                return Optional.empty();
            }
            ServerPlayer p = s.getPlayerList().getPlayer(id);
            return p == null ? Optional.empty() : Optional.of(new NeoForgePlayerHandle(p));
        }

        @Override
        public Optional<PlayerHandle> byName(String name) {
            final MinecraftServer s = server;
            if (s == null || name == null) {
                return Optional.empty();
            }
            ServerPlayer p = s.getPlayerList().getPlayerByName(name);
            return p == null ? Optional.empty() : Optional.of(new NeoForgePlayerHandle(p));
        }

        @Override
        public List<PlayerHandle> online() {
            final MinecraftServer s = server;
            if (s == null) {
                return List.of();
            }
            List<PlayerHandle> out = new ArrayList<>();
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                out.add(new NeoForgePlayerHandle(p));
            }
            return List.copyOf(out);
        }
    }

    /** Edge event registration backed by NeoForge's game event bus (see {@link NeoForgePlatformEvents}). */
    private static final class NeoForgeEdgeEvents implements EdgeEvents {

        @Override
        public void onServerTickEnd(Runnable hook) {
            if (hook != null) {
                TICK_END_HOOKS.add(hook);
            }
        }

        @Override
        public void onEntityLoad(Consumer<EntityHandle> hook) {
            if (hook != null) {
                ENTITY_LOAD_HOOKS.add(hook);
            }
        }

        @Override
        public void onBlockInteract(BlockInteractListener listener) {
            if (listener != null) {
                BLOCK_INTERACT.add(listener);
            }
        }

        @Override
        public void onItemUse(ItemUseListener listener) {
            if (listener != null) {
                ITEM_USE.add(listener);
            }
        }

        @Override
        public void onEntityAttack(EntityAttackListener listener) {
            if (listener != null) {
                ENTITY_ATTACK.add(listener);
            }
        }

        @Override
        public void onBlockBreak(BlockBreakListener listener) {
            if (listener != null) {
                BLOCK_BREAK.add(listener);
            }
        }

        @Override
        public void onEntityDeath(EntityDeathListener listener) {
            if (listener != null) {
                ENTITY_DEATH.add(listener);
            }
        }

        @Override
        public void onEntityDamaged(EntityDamagedListener listener) {
            if (listener != null) {
                ENTITY_DAMAGED.add(listener);
            }
        }

        @Override
        public void onChatMessage(ChatListener listener) {
            if (listener != null) {
                CHAT.add(listener);
            }
        }

        @Override
        public void onPlayerJoin(Consumer<PlayerHandle> hook) {
            if (hook != null) {
                PLAYER_JOIN.add(hook);
            }
        }

        @Override
        public void onPlayerLeave(Consumer<PlayerHandle> hook) {
            if (hook != null) {
                PLAYER_LEAVE.add(hook);
            }
        }

        @Override
        public void onServerStarting(Runnable hook) {
            if (hook != null) {
                SERVER_STARTING.add(hook);
            }
        }

        @Override
        public void onServerStopping(Runnable hook) {
            if (hook != null) {
                SERVER_STOPPING.add(hook);
            }
        }
    }
}
