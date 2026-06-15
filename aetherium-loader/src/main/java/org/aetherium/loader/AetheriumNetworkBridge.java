/*
 * Aetherium Framework — network bridge (SPI → NeoForge PayloadRegistrar).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.aetherium.network.ClientPayloadHandler;
import org.aetherium.network.NetworkPayload;
import org.aetherium.network.NetworkRegistry;
import org.aetherium.network.PayloadCodec;
import org.aetherium.network.PayloadSink;
import org.aetherium.network.PayloadSource;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * Bridges the pure {@link NetworkRegistry} to NeoForge's {@code PayloadRegistrar}.
 *
 * <p>EN: For each registered channel it builds a {@code CustomPacketPayload.Type}, a {@code StreamCodec}
 * that delegates to the mod's pure {@link PayloadCodec} through {@link PayloadSink}/{@link PayloadSource}
 * adapters over {@code RegistryFriendlyByteBuf}, and a handler that runs the mod's
 * {@link ClientPayloadHandler} on the main thread. The {@code writeSegment}/{@code readSegment}
 * adapters copy off-heap FFM memory straight to/from the Netty buffer — the zero-GC contract.
 *
 * <p>RU: Для каждого канала строит {@code CustomPacketPayload.Type}, {@code StreamCodec},
 * делегирующий чистому {@link PayloadCodec} через адаптеры {@link PayloadSink}/{@link PayloadSource}
 * над {@code RegistryFriendlyByteBuf}, и обработчик, запускающий {@link ClientPayloadHandler} на
 * главном потоке. Адаптеры {@code writeSegment}/{@code readSegment} копируют off-heap память FFM
 * напрямую в/из Netty-буфера — контракт без аллокаций.
 */
public final class AetheriumNetworkBridge {

    private AetheriumNetworkBridge() {}

    /** Mod-bus listener: wire every registered Aetherium channel into the platform. */
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();
        for (NetworkRegistry.Entry<?> entry : NetworkRegistry.entries()) {
            bind(registrar, entry);
        }
    }

    private static <T extends NetworkPayload> void bind(PayloadRegistrar registrar, NetworkRegistry.Entry<T> entry) {
        final PayloadCodec<T> codec = entry.codec();
        final ClientPayloadHandler<T> handler = entry.handler();
        final ResourceLocation id = ResourceLocation.parse(codec.channelId());
        final CustomPacketPayload.Type<Wrapper<T>> type = new CustomPacketPayload.Type<>(id);

        final StreamCodec<RegistryFriendlyByteBuf, Wrapper<T>> stream = StreamCodec.of(
                (buf, wrapper) -> codec.encode(wrapper.payload(), new BufSink(buf)),
                buf -> new Wrapper<>(type, codec.decode(new BufSource(buf))));

        registrar.playToClient(type, stream,
                (wrapper, context) -> context.enqueueWork(() -> handler.handle(wrapper.payload())));
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
