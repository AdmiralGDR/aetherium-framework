/*
 * Aetherium Framework — PAL entry point.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Entry point to the Platform Abstraction Layer: {@code Platform.bridge()}.
 *
 * <p>EN: Resolves the active {@link PlatformBridge} once via {@code ServiceLoader}. If none is
 * present (e.g. unit tests, the CLI, or a headless tool), it returns a safe <strong>no-op</strong>
 * bridge instead of throwing — so mod code calling {@code Platform.bridge().entities()...} never
 * NPEs or crashes outside a running game. In-game, {@code aetherium-loader} registers the NeoForge
 * implementation and it is selected automatically.
 *
 * <p>RU: Разрешает активный {@link PlatformBridge} один раз через {@code ServiceLoader}. Если его
 * нет (напр. юнит-тесты, CLI или headless-инструмент), возвращает безопасный <strong>no-op</strong>
 * мост вместо исключения — поэтому код мода, вызывающий {@code Platform.bridge().entities()...},
 * никогда не падает вне работающей игры. В игре {@code aetherium-loader} регистрирует реализацию
 * NeoForge, и она выбирается автоматически.
 */
public final class Platform {

    private static final PlatformBridge BRIDGE = resolve();

    private Platform() {
    }

    /** The active platform bridge (never null; a no-op bridge outside the game). */
    public static PlatformBridge bridge() {
        return BRIDGE;
    }

    private static PlatformBridge resolve() {
        try {
            Optional<PlatformBridge> found = ServiceLoader.load(PlatformBridge.class).findFirst();
            return found.orElseGet(NoopPlatformBridge::new);
        } catch (Throwable t) {
            return new NoopPlatformBridge();
        }
    }

    /** Safe fallback used when no game platform is registered. */
    private static final class NoopPlatformBridge implements PlatformBridge {
        private final EntityAccess entities = new EntityAccess() {
            @Override
            public Optional<EntityHandle> byId(UUID id) {
                return Optional.empty();
            }

            @Override
            public void forEach(Consumer<EntityHandle> action) {
                // no entities outside a game
            }

            @Override
            public int count() {
                return 0;
            }
        };

        private final LevelAccess levels = new LevelAccess() {
            @Override
            public Optional<LevelContext> primary() {
                return Optional.empty();
            }

            @Override
            public Optional<LevelContext> byDimension(String dimensionId) {
                return Optional.empty();
            }

            @Override
            public void forEach(Consumer<LevelContext> action) {
                // no levels outside a game
            }

            @Override
            public int count() {
                return 0;
            }
        };

        private final EdgeEvents events = new EdgeEvents() {
            @Override
            public void onServerTickEnd(Runnable hook) {
                // no game loop to hook
            }

            @Override
            public void onEntityLoad(Consumer<EntityHandle> hook) {
                // no entities to observe
            }
        };

        @Override
        public String platformName() {
            return "none";
        }

        @Override
        public boolean isGameAvailable() {
            return false;
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
    }
}
