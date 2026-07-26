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

    /**
     * The player at this JVM's client, if any — "who am I".
     *
     * <p>EN: On a client this is {@code Minecraft.getInstance().player}, so a client-side keybind (see
     * {@link org.aetherium.edge.UiAccess#registerKeybind}) can open a screen about the player who pressed it,
     * in single-player <em>and</em> multiplayer alike. On a dedicated server (no client) it is
     * {@link Optional#empty()} — there is no single "local" player — so server code must keep using
     * {@link #byId}/{@link #online()}. Default empty so a no-game or headless bridge stays safe.
     * RU: На клиенте это {@code Minecraft.getInstance().player} — «кто я», чтобы клавиша открыла экран про
     * нажавшего и в одиночной, и в сетевой игре. На выделенном сервере — {@link Optional#empty()} (локального
     * игрока нет), поэтому серверный код использует {@link #byId}/{@link #online()}.
     */
    default Optional<PlayerHandle> local() {
        return Optional.empty();
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
