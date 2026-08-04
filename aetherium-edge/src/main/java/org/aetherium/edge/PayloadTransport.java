/*
 * Aetherium Framework — the send side of the network (installed by the loader; no-op off-platform).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import org.aetherium.network.NetworkPayload;

/**
 * The loader-agnostic <em>send</em> side of the network — the SPI the loader installs so {@link Network} can
 * actually dispatch packets. Off-platform (tests, CLI, headless tools) the {@link #NOOP} transport is used, so
 * a mod calling {@code Network.sendToServer(...)} never NPEs or throws when no game is present.
 *
 * <p>EN: Mirrors the {@code Platform.bridge()} install pattern. The loader maps each call to the platform's
 * packet distributor (e.g. NeoForge's {@code PacketDistributor}). Directions:
 * {@link #sendToServer} is client→server; {@link #sendToClient}/{@link #sendToAllClients} are server→client(s).
 *
 * <p>RU: Отправляющая сторона сети — SPI, который устанавливает загрузчик, чтобы {@link Network} мог реально
 * слать пакеты. Вне игры используется {@link #NOOP}, поэтому вызовы отправки безопасны. Направления:
 * {@link #sendToServer} — клиент→сервер; {@link #sendToClient}/{@link #sendToAllClients} — сервер→клиент(ы).
 */
public interface PayloadTransport {

    /** Client → server: send {@code payload} to the server this client is connected to. */
    void sendToServer(NetworkPayload payload);

    /** Server → one client: send {@code payload} to {@code target}. */
    void sendToClient(PlayerHandle target, NetworkPayload payload);

    /** Server → all clients: broadcast {@code payload} to every connected player. */
    void sendToAllClients(NetworkPayload payload);

    /** The safe default when no game is present — every send is a no-op. */
    PayloadTransport NOOP = new PayloadTransport() {
        @Override
        public void sendToServer(NetworkPayload payload) {
            // no game to send to
        }

        @Override
        public void sendToClient(PlayerHandle target, NetworkPayload payload) {
            // no game to send to
        }

        @Override
        public void sendToAllClients(NetworkPayload payload) {
            // no game to send to
        }
    };
}
