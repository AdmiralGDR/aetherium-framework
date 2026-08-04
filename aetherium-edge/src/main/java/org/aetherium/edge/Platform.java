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
    /** Test-only override (). Null in production; {@link #bridge()} prefers it when set. */
    private static volatile PlatformBridge testOverride;

    private Platform() {
    }

    /** The active platform bridge (never null; a no-op bridge outside the game). */
    public static PlatformBridge bridge() {
        PlatformBridge override = testOverride;
        return override != null ? override : BRIDGE;
    }

    /**
     * Install a bridge for a headless test, or pass {@code null} to restore the {@code ServiceLoader} default.
     *
     * <p>EN: {@link #BRIDGE} is resolved once and immutable, so a headless test could never present a player —
     * {@code players().local()} was always empty and any code that reads the local player was only testable in
     * its <em>absent</em> branch (). This opt-in, reversible hook lets a test stand up a fake bridge
     * (with a player present) for the duration of one test and tear it down after, <strong>without</strong>
     * registering a {@code META-INF/services} entry that would change the bridge for every other test in the
     * JVM — including the ones that assert honest no-game behaviour. Alternatively, a mod that only needs a
     * player can inject a {@code Supplier<PlayerHandle>} defaulting to {@code Platform.bridge().players()::local}
     * and hand it a fake in the test — the lighter pattern when a whole bridge is overkill.
     *
     * <p><strong>Test scope only.</strong> Never call this in shipped mod code; production always uses the
     * {@code ServiceLoader}-resolved bridge. Restore with {@code installForTesting(null)} in a finally/teardown.
     *
     * <p>RU: {@link #BRIDGE} разрешается один раз и неизменяем, поэтому headless-тест не мог предъявить игрока
     * (). Этот опциональный обратимый хук позволяет тесту подставить фейковый мост (с игроком) на время
     * одного теста и убрать его после — без регистрации {@code META-INF/services}, которая изменила бы мост
     * для всех тестов в JVM. Только для тестов; восстановление — {@code installForTesting(null)}.
     */
    public static void installForTesting(PlatformBridge bridge) {
        testOverride = bridge;
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
