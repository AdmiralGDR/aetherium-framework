/*
 * Aetherium Framework — network payload marker.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

/**
 * A custom packet payload, identified by a stable {@code namespace:path} channel id. Loader-agnostic:
 * the loader maps each {@link #channelId()} to the platform's payload-type registration.
 */
public interface NetworkPayload {

    /** Stable channel id, e.g. {@code "aetherium:struct_arena_sync"}. */
    String channelId();
}
