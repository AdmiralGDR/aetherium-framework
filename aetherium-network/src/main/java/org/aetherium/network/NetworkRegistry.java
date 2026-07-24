/*
 * Aetherium Framework — network registry.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.aetherium.core.AetheriumException;
import org.aetherium.core.Diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loader-agnostic registry of channels. Mods register a {@link PayloadCodec} + a
 * {@link ClientPayloadHandler}; the loader reads {@link #entries()} during its payload-registration
 * phase and wires each into the platform's packet system (e.g. NeoForge's {@code PayloadRegistrar}).
 *
 * <p>No game types here — the registry is pure data so registration order/state is testable off-platform.
 *
 * <p><strong>Duplicate channels are rejected.</strong> Two mods that register the same
 * {@code channelId} would otherwise cross-talk silently (each decoding the other's bytes as its own
 * state). {@link #register} now throws an {@link AetheriumException} on a duplicate — the modder is told at
 * startup to namespace the channel ({@code "mymod:state"}) rather than debugging silent corruption. This is
 * the multi-mod stability fix; pair it with the per-mod {@code channelId} constructor on
 * {@link TreeSyncCodec}/{@link TreeSyncPacket}.
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
    /** Claims a channelId atomically so a duplicate registration is detected under concurrency. */
    private static final ConcurrentHashMap<String, Boolean> CLAIMED = new ConcurrentHashMap<>();

    public static <T extends NetworkPayload> void register(PayloadCodec<T> codec, ClientPayloadHandler<T> handler) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(handler, "handler");
        final String channelId = Channels.validate(codec.channelId());
        if (CLAIMED.putIfAbsent(channelId, Boolean.TRUE) != null) {
            throw new AetheriumException(Diagnostic.error("AE-NET-CHANNEL-DUP",
                    "Network channel '" + channelId + "' is already registered. Two mods must not share a "
                            + "channel — namespace yours (e.g. \"mymod:state\") so packets don't cross-talk."));
        }
        ENTRIES.add(new Entry<>(codec, handler));
    }

    /** Snapshot of registered channels, for the loader to bridge. */
    public static List<Entry<?>> entries() {
        return List.copyOf(ENTRIES);
    }

    public static int size() {
        return ENTRIES.size();
    }

    /**
     * Test/loader hook: forget all registrations (and channel claims). Not used in production — the registry
     * is populated once at load — but lets an off-platform self-test register the same channel across runs.
     */
    public static void reset() {
        ENTRIES.clear();
        CLAIMED.clear();
    }
}
