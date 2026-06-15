/*
 * Aetherium Framework — network registry.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loader-agnostic registry of channels. Mods register a {@link PayloadCodec} + a
 * {@link ClientPayloadHandler}; the loader reads {@link #entries()} during its payload-registration
 * phase and wires each into the platform's packet system (e.g. NeoForge's {@code PayloadRegistrar}).
 *
 * <p>No game types here — the registry is pure data so registration order/state is testable off-platform.
 */
public final class NetworkRegistry {

    private NetworkRegistry() {}

    /** One registered channel: its codec and its receive handler. */
    public record Entry<T extends NetworkPayload>(PayloadCodec<T> codec, ClientPayloadHandler<T> handler) {
        public String channelId() {
            return codec.channelId();
        }
    }

    private static final List<Entry<?>> ENTRIES = new CopyOnWriteArrayList<>();

    public static <T extends NetworkPayload> void register(PayloadCodec<T> codec, ClientPayloadHandler<T> handler) {
        ENTRIES.add(new Entry<>(codec, handler));
    }

    /** Snapshot of registered channels, for the loader to bridge. */
    public static List<Entry<?>> entries() {
        return List.copyOf(ENTRIES);
    }

    public static int size() {
        return ENTRIES.size();
    }
}
