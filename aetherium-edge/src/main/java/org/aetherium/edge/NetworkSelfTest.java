/*
 * Aetherium Framework — directional network + side-model self-test (offline, no game).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import org.aetherium.core.mod.Side;
import org.aetherium.network.NetworkPayload;
import org.aetherium.network.PayloadCodec;
import org.aetherium.network.PayloadSink;
import org.aetherium.network.PayloadSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Proves the directional network matrix and side model off-platform — no game, no loader.
 *
 * <p>EN: (1) a serverbound round-trip encodes/decodes and dispatches to a handler with the sender's
 * {@link PlayerHandle} present; (2) the per-sender rate limit drops a flood; (3) the size cap rejects an
 * oversized payload; (4) the send facade routes {@code sendToServer/sendToClient/sendToAllClients} through an
 * installed transport and is a safe no-op off-platform; (5) the side model gates a {@code CLIENT} feature off a
 * server and runs {@code SERVER}/{@code BOTH} wherever they are safe. This is the offline proof that a mod can
 * be written both-side, server-side, or client-side, and can administer a remote server.
 *
 * <p>RU: Доказывает направленную сеть и модель сторон без игры: серверный round-trip с отправителем; лимит
 * частоты; лимит размера; фасад отправки через транспорт (и no-op вне игры); гейтинг стороны.
 */
public final class NetworkSelfTest {

    private static final String CHANNEL = "aetherium:selftest_admin";

    private NetworkSelfTest() {
    }

    /** A tiny admin payload carrying one int (e.g. "set max members"). */
    private record AdminPayload(int value) implements NetworkPayload {
        @Override
        public String channelId() {
            return CHANNEL;
        }
    }

    private static final PayloadCodec<AdminPayload> CODEC = new PayloadCodec<>() {
        @Override
        public String channelId() {
            return CHANNEL;
        }

        @Override
        public void encode(AdminPayload payload, PayloadSink sink) {
            sink.writeInt(payload.value());
        }

        @Override
        public AdminPayload decode(PayloadSource source) {
            return new AdminPayload(source.readInt());
        }
    };

    public static Result run() {
        List<String> notes = new ArrayList<>();
        Network.reset();
        try {
            // 1) Serverbound round-trip: register, encode→decode via a heap buffer, deliver with a sender.
            AtomicReference<String> received = new AtomicReference<>();
            Network.registerServerbound(CODEC, (PlayerHandle sender, AdminPayload p) ->
                    received.set(sender.name() + ":" + p.value()));
            Network.Serverbound<?> entry = Network.serverboundEntries().get(0);

            AdminPayload sent = new AdminPayload(42);
            AdminPayload roundTripped = CODEC.decode(HeapBuffer.encode(CODEC, sent));
            @SuppressWarnings("unchecked")
            Network.Serverbound<AdminPayload> typed = (Network.Serverbound<AdminPayload>) entry;
            PlayerHandle op = new FakeSender("Operator", new UUID(0L, 7L), 4);
            boolean delivered = Network.deliver(typed, op, roundTripped, 1_000L);
            boolean roundTripOk = delivered && "Operator:42".equals(received.get()) && roundTripped.value() == 42;
            notes.add("serverbound round-trip delivered=" + delivered + " saw=" + received.get());

            // 2) Rate limit: the same sender flooding past the burst is eventually dropped (at a fixed clock).
            int accepted = 0;
            for (int i = 0; i < (int) ServerboundGuard.DEFAULT_BURST + 20; i++) {
                if (Network.deliver(typed, op, roundTripped, 1_000L)) {
                    accepted++;
                }
            }
            boolean rateLimited = accepted <= (int) ServerboundGuard.DEFAULT_BURST;
            notes.add("rate limit accepted " + accepted + " of "
                    + ((int) ServerboundGuard.DEFAULT_BURST + 20) + " at a frozen clock (dropped the flood)");

            // 3) Size cap: an oversized payload is rejected before decode.
            boolean sizeOk = !Network.withinSizeLimit(typed.maxBytes() + 1, typed.maxBytes())
                    && Network.withinSizeLimit(8, typed.maxBytes());
            notes.add("size cap rejects oversized=" + sizeOk);

            // 4) Send facade: an installed transport captures each direction; off-platform it is a no-op.
            CapturingTransport transport = new CapturingTransport();
            Network.installTransport(transport);
            Network.sendToServer(new AdminPayload(1));
            Network.sendToClient(op, new AdminPayload(2));
            Network.sendToAllClients(new AdminPayload(3));
            Network.relayToClient(op, new AdminPayload(4));
            boolean sendOk = transport.toServer == 1 && transport.toClient == 2 /* sendToClient + relay */
                    && transport.toAll == 1;
            Network.installTransport(null); // restore no-op
            Network.sendToServer(new AdminPayload(9)); // must be a safe no-op now
            notes.add("send facade routed toServer=" + transport.toServer + " toClient=" + transport.toClient
                    + " toAll=" + transport.toAll + " (then no-op after uninstall)");

            // 5) Side model: CLIENT gated off a server; SERVER/BOTH run on either side.
            boolean sideOk = !Side.CLIENT.activeOn(Side.SERVER)   // client code never on a dedicated server
                    && Side.CLIENT.activeOn(Side.CLIENT)          // client code on a client
                    && Side.SERVER.activeOn(Side.CLIENT)          // server logic on a client's integrated server
                    && Side.SERVER.activeOn(Side.SERVER)
                    && Side.BOTH.activeOn(Side.CLIENT) && Side.BOTH.activeOn(Side.SERVER);
            notes.add("side gating CLIENT@server=" + Side.CLIENT.activeOn(Side.SERVER)
                    + " (must be false); SERVER/BOTH run on both sides");

            boolean passed = roundTripOk && rateLimited && sizeOk && sendOk && sideOk;
            return new Result(roundTripOk, rateLimited, sizeOk, sendOk, sideOk, notes, passed);
        } finally {
            Network.reset();
        }
    }

    /** Outcome of the network self-test. */
    public record Result(boolean roundTripOk, boolean rateLimited, boolean sizeCapOk, boolean sendFacadeOk,
                         boolean sideModelOk, List<String> notes, boolean passed) {
    }

    /** A transport that counts each direction it is asked to send. */
    private static final class CapturingTransport implements PayloadTransport {
        int toServer;
        int toClient;
        int toAll;

        @Override public void sendToServer(NetworkPayload payload) {
            toServer++;
        }

        @Override public void sendToClient(PlayerHandle target, NetworkPayload payload) {
            toClient++;
        }

        @Override public void sendToAllClients(NetworkPayload payload) {
            toAll++;
        }
    }

    /** A minimal off-platform player used as a packet sender. */
    private record FakeSender(String name, UUID uuid, int permission) implements PlayerHandle {
        @Override public UUID id() {
            return uuid;
        }

        @Override public double x() { return 0; }
        @Override public double y() { return 0; }
        @Override public double z() { return 0; }
        @Override public void setPosition(double x, double y, double z) { }
        @Override public void addVelocity(double dx, double dy, double dz) { }
        @Override public float health() { return 20f; }
        @Override public void setHealth(float health) { }
        @Override public InventoryAccess inventory() { return InventoryAccess.EMPTY; }
        @Override public void sendMessage(String message) { }

        @Override public boolean hasPermission(int level) {
            return level <= permission;
        }
    }

    /** A trivial heap-backed buffer so the codec can be exercised without a Netty buffer. */
    private static final class HeapBuffer implements PayloadSink, PayloadSource {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        private java.io.DataInputStream in;

        static <T extends NetworkPayload> PayloadSource encode(PayloadCodec<T> codec, T payload) {
            HeapBuffer b = new HeapBuffer();
            codec.encode(payload, b);
            b.in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(b.out.toByteArray()));
            return b;
        }

        @Override public void writeInt(int value) {
            write4(value);
        }

        @Override public void writeLong(long value) {
            write4((int) (value >>> 32));
            write4((int) value);
        }

        @Override public void writeSegment(java.lang.foreign.MemorySegment source, long length) {
            for (long i = 0; i < length; i++) {
                out.write(source.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i));
            }
        }

        @Override public int readInt() {
            try {
                return in.readInt();
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override public long readLong() {
            return ((long) readInt() << 32) | (readInt() & 0xFFFFFFFFL);
        }

        @Override public void readSegment(java.lang.foreign.MemorySegment destination, long length) {
            for (long i = 0; i < length; i++) {
                try {
                    destination.set(java.lang.foreign.ValueLayout.JAVA_BYTE, i, in.readByte());
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        private void write4(int value) {
            out.write((value >>> 24) & 0xFF);
            out.write((value >>> 16) & 0xFF);
            out.write((value >>> 8) & 0xFF);
            out.write(value & 0xFF);
        }
    }
}
