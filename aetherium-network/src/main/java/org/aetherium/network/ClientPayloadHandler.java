/*
 * Aetherium Framework — client payload handler.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

/**
 * Handles a decoded payload on the receiving side. The loader invokes it on the correct thread
 * (e.g. the client main thread, after the platform enqueues the work).
 */
@FunctionalInterface
public interface ClientPayloadHandler<T extends NetworkPayload> {

    void handle(T payload);
}
