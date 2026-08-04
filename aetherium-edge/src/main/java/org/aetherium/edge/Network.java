/*
 * Aetherium Framework — the directional network facade (send + serverbound receive), sender-aware.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import org.aetherium.network.Channels;
import org.aetherium.network.NetworkPayload;
import org.aetherium.network.PayloadCodec;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The directional network facade — the piece and the sidedness ask needed. The pure
 * {@code aetherium-network} module already carries the clientbound <em>receive</em> path
 * ({@code NetworkRegistry.register}); this adds, without a module cycle (it lives in {@code aetherium-edge} so
 * it can name {@link PlayerHandle}):
 *
 * <ul>
 *   <li><strong>serverbound receive</strong> — {@link #registerServerbound} with a {@link ServerPayloadHandler}
 *       that is handed the sender's {@link PlayerHandle} (from the connection, unspoofable);</li>
 *   <li><strong>the send side</strong> — {@link #sendToServer} (client→server), {@link #sendToClient} /
 *       {@link #sendToAllClients} (server→client(s)), and {@link #relayToClient} (a server-side helper for
 *       client↔client, i.e. forwarding one client's message to another). Sends route through an installed
 *       {@link PayloadTransport}; off-platform they are no-ops.</li>
 * </ul>
 *
 * <p>Every serverbound packet is guarded before the mod handler runs: a per-entry <strong>size cap</strong>
 * ({@link #withinSizeLimit}, enforced by the loader before decode) and a per-sender <strong>rate limit</strong>
 * ({@link ServerboundGuard}, applied by {@link #deliver}), so a flooded or oversized admin packet is dropped
 * safely — the channel is secure by default.
 *
 * <p>RU: Направленная сеть (+ сторонность). Чистый {@code aetherium-network} несёт клиентский приём; здесь
 * добавлены серверный приём с {@link PlayerHandle} отправителя, отправка ({@link #sendToServer},
 * {@link #sendToClient}, {@link #sendToAllClients}, {@link #relayToClient}) через устанавливаемый
 * {@link PayloadTransport} (вне игры — no-op) и защита каждого серверного пакета: лимит размера и лимит частоты
 * на отправителя — то есть канал безопасен по умолчанию.
 */
public final class Network {

    /** Default inbound size cap for a serverbound channel (bytes) — a hostile length field can't exceed it. */
    public static final int DEFAULT_MAX_BYTES = 32 * 1024;

    private Network() {
    }

    /**
     * One registered serverbound channel: its codec, its sender-aware handler, and the inbound size cap.
     */
    public record Serverbound<T extends NetworkPayload>(PayloadCodec<T> codec, ServerPayloadHandler<T> handler,
                                                        int maxBytes) {
        public String channelId() {
            return codec.channelId();
        }
    }

    private static final List<Serverbound<?>> SERVERBOUND = new CopyOnWriteArrayList<>();
    /** Claims a serverbound channelId atomically so a duplicate registration is detected under concurrency. */
    private static final ConcurrentHashMap<String, Boolean> CLAIMED = new ConcurrentHashMap<>();
    private static final ServerboundGuard GUARD = new ServerboundGuard();
    private static volatile PayloadTransport transport = PayloadTransport.NOOP;

    // --- serverbound receive (client → server) --------------------------------------------------

    /** Register a serverbound channel with the default size cap ({@link #DEFAULT_MAX_BYTES}). */
    public static <T extends NetworkPayload> void registerServerbound(PayloadCodec<T> codec,
                                                                      ServerPayloadHandler<T> handler) {
        registerServerbound(codec, handler, DEFAULT_MAX_BYTES);
    }

    /**
     * Register a serverbound channel. A serverbound channel is namespaced independently of the clientbound
     * one, but the same duplicate discipline applies — two mods registering the same serverbound channelId is
     * rejected with {@code AE-NET-CHANNEL-DUP} so their admin packets can't cross-talk.
     */
    public static <T extends NetworkPayload> void registerServerbound(PayloadCodec<T> codec,
                                                                      ServerPayloadHandler<T> handler,
                                                                      int maxBytes) {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(handler, "handler");
        final String channelId = Channels.validate(codec.channelId());
        if (CLAIMED.putIfAbsent(channelId, Boolean.TRUE) != null) {
            throw new org.aetherium.core.AetheriumException(org.aetherium.core.Diagnostic.error(
                    "AE-NET-CHANNEL-DUP", "Serverbound channel '" + channelId + "' is already registered. "
                            + "Two mods must not share a channel — namespace yours (e.g. \"mymod:admin\")."));
        }
        SERVERBOUND.add(new Serverbound<>(codec, handler, Math.max(1, maxBytes)));
    }

    /** Snapshot of registered serverbound channels, for the loader to bridge. */
    public static List<Serverbound<?>> serverboundEntries() {
        return List.copyOf(SERVERBOUND);
    }

    /**
     * Whether an inbound payload of {@code byteCount} bytes is within {@code maxBytes} — the loader checks this
     * against the readable bytes <em>before</em> decoding, so a hostile length field never allocates.
     */
    public static boolean withinSizeLimit(int byteCount, int maxBytes) {
        return byteCount >= 0 && byteCount <= maxBytes;
    }

    /**
     * Deliver a decoded serverbound {@code payload} from {@code sender} to its handler, applying the per-sender
     * rate limit. Returns {@code false} (and does <strong>not</strong> call the handler) if the sender is over
     * their budget on this channel — a flood is dropped before the mod sees it. The loader calls this on the
     * server main thread after decoding.
     */
    public static <T extends NetworkPayload> boolean deliver(Serverbound<T> entry, PlayerHandle sender, T payload) {
        return deliver(entry, sender, payload, System.currentTimeMillis());
    }

    /** {@link #deliver(Serverbound, PlayerHandle, NetworkPayload)} with an explicit clock (testable). */
    public static <T extends NetworkPayload> boolean deliver(Serverbound<T> entry, PlayerHandle sender,
                                                             T payload, long nowMillis) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(sender, "sender");
        if (!GUARD.allow(entry.channelId(), sender.id(), nowMillis)) {
            return false; // rate limited — drop before the mod handler runs
        }
        entry.handler().handle(sender, payload);
        return true;
    }

    // --- send (through the installed transport) --------------------------------------------------

    /** Install the platform send transport (loader startup). Passing {@code null} restores the no-op. */
    public static void installTransport(PayloadTransport newTransport) {
        transport = newTransport == null ? PayloadTransport.NOOP : newTransport;
    }

    /** Client → server. */
    public static void sendToServer(NetworkPayload payload) {
        transport.sendToServer(Objects.requireNonNull(payload, "payload"));
    }

    /** Server → one client. */
    public static void sendToClient(PlayerHandle target, NetworkPayload payload) {
        transport.sendToClient(Objects.requireNonNull(target, "target"), Objects.requireNonNull(payload, "payload"));
    }

    /** Server → all connected clients. */
    public static void sendToAllClients(NetworkPayload payload) {
        transport.sendToAllClients(Objects.requireNonNull(payload, "payload"));
    }

    /**
     * Client ↔ client, relayed via the server: a server-side helper (call it from a serverbound handler) that
     * forwards one client's message to {@code target}. There is no direct client-to-client hop in a single
     * game instance — a client sends serverbound, and the server routes it here — so this names that intent
     * while reusing {@link #sendToClient}.
     */
    public static void relayToClient(PlayerHandle target, NetworkPayload payload) {
        sendToClient(target, payload);
    }

    /** Test/loader hook: forget serverbound registrations, channel claims, and rate-limit state. */
    public static void reset() {
        SERVERBOUND.clear();
        CLAIMED.clear();
        GUARD.reset();
        transport = PayloadTransport.NOOP;
    }
}
