/*
 * Aetherium Framework — network bridge (SPI → NeoForge PayloadRegistrar), both directions.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.aetherium.edge.Network;
import org.aetherium.edge.PayloadTransport;
import org.aetherium.edge.PlayerHandle;
import org.aetherium.network.ClientPayloadHandler;
import org.aetherium.network.NetworkPayload;
import org.aetherium.network.NetworkRegistry;
import org.aetherium.network.PayloadCodec;
import org.aetherium.network.PayloadSink;
import org.aetherium.network.PayloadSource;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Bridges the pure Aetherium network SPI to NeoForge's {@code PayloadRegistrar}, in <strong>both
 * directions</strong> ().
 *
 * <p>EN: Clientbound (server→client) comes from {@code NetworkRegistry} and is wired with {@code playToClient}
 * as before. Serverbound (client→server) comes from {@link Network#serverboundEntries()} and is wired with
 * {@code playToServer}; its handler takes the sender's {@link PlayerHandle} from the connection (never the
 * payload) and routes through {@link Network#deliver} so the per-sender rate limit applies, after a size cap
 * rejects an oversized payload before it is decoded. The bridge also installs a {@link PayloadTransport} so
 * {@code Network.sendToServer/sendToClient/sendToAllClients} reach NeoForge's {@code PacketDistributor}. The
 * {@code writeSegment}/{@code readSegment} adapters copy off-heap FFM memory straight to/from the Netty buffer.
 *
 * <p>RU: Клиентское направление (сервер→клиент) — из {@code NetworkRegistry} через {@code playToClient};
 * серверное (клиент→сервер) — из {@link Network#serverboundEntries()} через {@code playToServer}, с
 * {@link PlayerHandle} отправителя из соединения, лимитом размера до декодирования и лимитом частоты через
 * {@link Network#deliver}. Мост также устанавливает {@link PayloadTransport} для отправки через
 * {@code PacketDistributor}.
 */
public final class AetheriumNetworkBridge {

    private AetheriumNetworkBridge() {}

    /** channelId → factory that wraps a payload into the clientbound {@code CustomPacketPayload} for send. */
    private static final ConcurrentHashMap<String, Function<NetworkPayload, CustomPacketPayload>> CLIENTBOUND =
            new ConcurrentHashMap<>();
    /** channelId → factory that wraps a payload into the serverbound {@code CustomPacketPayload} for send. */
    private static final ConcurrentHashMap<String, Function<NetworkPayload, CustomPacketPayload>> SERVERBOUND =
            new ConcurrentHashMap<>();

    /** Mod-bus listener: wire every registered Aetherium channel (both directions) into the platform. */
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();
        for (NetworkRegistry.Entry<?> entry : NetworkRegistry.entries()) {
            bindClientbound(registrar, entry);
        }
        for (Network.Serverbound<?> entry : Network.serverboundEntries()) {
            bindServerbound(registrar, entry);
        }
        // Install the send side so Network.send* reaches NeoForge's PacketDistributor.
        Network.installTransport(NEOFORGE_TRANSPORT);
    }

    // --- clientbound (server → client) ----------------------------------------------------------

    private static <T extends NetworkPayload> void bindClientbound(PayloadRegistrar registrar,
                                                                   NetworkRegistry.Entry<T> entry) {
        final PayloadCodec<T> codec = entry.codec();
        final ClientPayloadHandler<T> handler = entry.handler();
        final ResourceLocation id = ResourceLocation.parse(codec.channelId());
        final CustomPacketPayload.Type<Wrapper<T>> type = new CustomPacketPayload.Type<>(id);

        final StreamCodec<RegistryFriendlyByteBuf, Wrapper<T>> stream = StreamCodec.of(
                (buf, wrapper) -> codec.encode(wrapper.payload(), new BufSink(buf)),
                buf -> new Wrapper<>(type, codec.decode(new BufSource(buf))));

        registrar.playToClient(type, stream,
                (wrapper, context) -> context.enqueueWork(() -> handler.handle(wrapper.payload())));
        CLIENTBOUND.put(codec.channelId(), wrapperFactory(type));
    }

    // --- serverbound (client → server) ----------------------------------------------------------

    private static <T extends NetworkPayload> void bindServerbound(PayloadRegistrar registrar,
                                                                   Network.Serverbound<T> entry) {
        final PayloadCodec<T> codec = entry.codec();
        final ResourceLocation id = ResourceLocation.parse(codec.channelId());
        final CustomPacketPayload.Type<Wrapper<T>> type = new CustomPacketPayload.Type<>(id);

        final StreamCodec<RegistryFriendlyByteBuf, Wrapper<T>> stream = StreamCodec.of(
                (buf, wrapper) -> codec.encode(wrapper.payload(), new BufSink(buf)),
                buf -> {
                    // Protection: reject an oversized inbound admin packet before decoding it, so a hostile
                    // length field never allocates. NeoForge drops a payload whose decode throws.
                    if (!Network.withinSizeLimit(buf.readableBytes(), entry.maxBytes())) {
                        throw new io.netty.handler.codec.DecoderException(
                                "Aetherium serverbound '" + codec.channelId() + "' payload exceeds "
                                        + entry.maxBytes() + " bytes");
                    }
                    return new Wrapper<>(type, codec.decode(new BufSource(buf)));
                });

        registrar.playToServer(type, stream, (wrapper, context) -> {
            final Player p = context.player(); // the sender, taken from the connection — not the payload
            if (!(p instanceof ServerPlayer serverPlayer)) {
                return; // serverbound must arrive with a server player; ignore otherwise
            }
            context.enqueueWork(() -> {
                PlayerHandle sender = new NeoForgePlayerHandle(serverPlayer);
                // Network.deliver applies the per-sender rate limit; a flood is dropped before the mod handler.
                Network.deliver(entry, sender, wrapper.payload());
            });
        });
        SERVERBOUND.put(codec.channelId(), wrapperFactory(type));
    }

    // --- send transport -------------------------------------------------------------------------

    private static final PayloadTransport NEOFORGE_TRANSPORT = new PayloadTransport() {
        @Override
        public void sendToServer(NetworkPayload payload) {
            CustomPacketPayload wrapped = wrap(SERVERBOUND, payload);
            if (wrapped != null) {
                PacketDistributor.sendToServer(wrapped);
            }
        }

        @Override
        public void sendToClient(PlayerHandle target, NetworkPayload payload) {
            ServerPlayer sp = resolve(target.id());
            CustomPacketPayload wrapped = wrap(CLIENTBOUND, payload);
            if (sp != null && wrapped != null) {
                PacketDistributor.sendToPlayer(sp, wrapped);
            }
        }

        @Override
        public void sendToAllClients(NetworkPayload payload) {
            CustomPacketPayload wrapped = wrap(CLIENTBOUND, payload);
            if (wrapped != null) {
                PacketDistributor.sendToAllPlayers(wrapped);
            }
        }
    };

    private static CustomPacketPayload wrap(ConcurrentHashMap<String, Function<NetworkPayload, CustomPacketPayload>> reg,
                                            NetworkPayload payload) {
        Function<NetworkPayload, CustomPacketPayload> factory = reg.get(payload.channelId());
        return factory == null ? null : factory.apply(payload);
    }

    private static ServerPlayer resolve(UUID id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    /** A factory that wraps any payload for {@code type}'s channel — used by the send transport. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Function<NetworkPayload, CustomPacketPayload> wrapperFactory(CustomPacketPayload.Type type) {
        return payload -> new Wrapper(type, payload);
    }

    /** NeoForge payload wrapper carrying our pure {@link NetworkPayload}. */
    private record Wrapper<T extends NetworkPayload>(CustomPacketPayload.Type<Wrapper<T>> typeRef, T payload)
            implements CustomPacketPayload {
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return typeRef;
        }
    }

    /** Zero-GC write adapter over the Netty-backed registry buffer. */
    private record BufSink(RegistryFriendlyByteBuf buf) implements PayloadSink {
        @Override
        public void writeInt(int value) {
            buf.writeInt(value);
        }

        @Override
        public void writeLong(long value) {
            buf.writeLong(value);
        }

        @Override
        public void writeSegment(MemorySegment source, long length) {
            final ByteBuffer view = source.asSlice(0L, length).asByteBuffer();
            buf.writeBytes(view);
        }
    }

    /** Zero-GC read adapter over the Netty-backed registry buffer. */
    private record BufSource(RegistryFriendlyByteBuf buf) implements PayloadSource {
        @Override
        public int readInt() {
            return buf.readInt();
        }

        @Override
        public long readLong() {
            return buf.readLong();
        }

        @Override
        public void readSegment(MemorySegment destination, long length) {
            final ByteBuffer view = destination.asSlice(0L, length).asByteBuffer();
            buf.readBytes(view);
        }
    }
}
