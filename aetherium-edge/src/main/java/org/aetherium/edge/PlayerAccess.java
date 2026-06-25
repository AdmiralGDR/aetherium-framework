/*
 * Aetherium Framework — PAL player access (lookup of online players).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Loader-agnostic lookup of the connected players.
 *
 * <p>EN: Obtained from {@link PlatformBridge#players()}. {@link #EMPTY} is the safe default a no-game
 * bridge returns, so mod code can call {@code players()} unconditionally.
 * RU: Получается из {@link PlatformBridge#players()}. {@link #EMPTY} — безопасное значение по умолчанию.
 */
public interface PlayerAccess {

    Optional<PlayerHandle> byId(UUID id);

    Optional<PlayerHandle> byName(String name);

    List<PlayerHandle> online();

    default int count() {
        return online().size();
    }

    /** An empty access used by the no-op bridge (no players, no game). */
    PlayerAccess EMPTY = new PlayerAccess() {
        @Override
        public Optional<PlayerHandle> byId(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerHandle> byName(String name) {
            return Optional.empty();
        }

        @Override
        public List<PlayerHandle> online() {
            return List.of();
        }
    };
}
