/*
 * Aetherium Framework — serverbound payload handler (client → server), with the sender's identity.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import org.aetherium.network.NetworkPayload;

/**
 * Handles a payload that arrived <strong>on the server, from a client</strong> — the mirror of the
 * clientbound {@code ClientPayloadHandler}, and the thing asked for so a mod's GUI can administer a
 * dedicated server.
 *
 * <p>EN: The framework passes the <strong>sender's {@link PlayerHandle}</strong>, taken from the connection —
 * never from the payload bytes, so it cannot be spoofed. That is the important part: with it, a handler gates
 * an admin action with {@code sender.hasPermission(level)} instead of every mod inventing (and usually
 * botching) its own inbound authentication. The loader invokes this on the server main thread.
 *
 * <p>RU: Обрабатывает пакет, пришедший <strong>на сервер от клиента</strong> — зеркало клиентского
 * обработчика (). Фреймворк передаёт {@link PlayerHandle} отправителя, взятый из соединения (не из байтов
 * пакета — подделать нельзя), чтобы обработчик проверял право через {@code sender.hasPermission(level)}, а не
 * изобретал свою аутентификацию. Загрузчик вызывает это на главном потоке сервера.
 */
@FunctionalInterface
public interface ServerPayloadHandler<T extends NetworkPayload> {

    /**
     * Handle {@code payload} sent by {@code sender}. The sender's permission level is authoritative
     * ({@link PlayerHandle#hasPermission(int)}); gate privileged actions on it.
     */
    void handle(PlayerHandle sender, T payload);
}
