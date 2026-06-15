/*
 * Aetherium Framework — NeoForge PAL implementation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.aetherium.edge.EdgeEvents;
import org.aetherium.edge.EntityAccess;
import org.aetherium.edge.EntityHandle;
import org.aetherium.edge.PlatformBridge;

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
 * are static state fed by {@link NeoForgePlatformEvents}; entity access walks the server's levels using
 * the stable {@code getAllEntities()/getEntity(UUID)} API. This is the only place (besides
 * {@link NeoForgeEntityHandle}) that touches Minecraft types — the edge SPI stays pure and the rule
 * "edge defines the interface, loader provides the implementation" holds.
 *
 * <p>RU: Обнаруживается через {@code ServiceLoader}, поэтому {@code Platform.bridge()} возвращает его в
 * рантайме; вне игры (или до старта сервера) используется no-op мост из модуля edge. Текущий
 * {@link MinecraftServer} и списки пользовательских хуков — статическое состояние, наполняемое
 * {@link NeoForgePlatformEvents}; доступ к сущностям обходит уровни сервера через стабильный API
 * {@code getAllEntities()/getEntity(UUID)}. Это единственное место (кроме {@link NeoForgeEntityHandle}),
 * касающееся типов Minecraft.
 */
public final class NeoForgePlatformBridge implements PlatformBridge {

    private static volatile MinecraftServer server;
    private static final CopyOnWriteArrayList<Runnable> TICK_END_HOOKS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Consumer<EntityHandle>> ENTITY_LOAD_HOOKS = new CopyOnWriteArrayList<>();

    private final EntityAccess entities = new NeoForgeEntityAccess();
    private final EdgeEvents events = new NeoForgeEdgeEvents();

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

    static void dispatchTickEnd() {
        for (Runnable hook : TICK_END_HOOKS) {
            try {
                hook.run();
            } catch (Throwable ignored) {
                // A misbehaving mod hook must never break the server tick.
            }
        }
    }

    static void dispatchEntityLoad(Entity entity) {
        if (ENTITY_LOAD_HOOKS.isEmpty() || entity == null) {
            return;
        }
        final EntityHandle handle = new NeoForgeEntityHandle(entity);
        for (Consumer<EntityHandle> hook : ENTITY_LOAD_HOOKS) {
            try {
                hook.accept(handle);
            } catch (Throwable ignored) {
                // Contain hook failures.
            }
        }
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
    public EdgeEvents events() {
        return events;
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
    }
}
