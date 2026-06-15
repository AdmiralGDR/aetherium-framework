/*
 * Aetherium Framework — StructArena sync codec.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.aetherium.core.compute.StructArena;

/**
 * {@link PayloadCodec} for {@link StructArenaSyncPacket}: a fixed wire format of
 * {@code [int rowCount][rowCount × stride bytes]}, transferred to/from off-heap memory with no GC.
 *
 * <p>EN: Decoding reuses a caller-supplied <em>client</em> {@link StructArena} (sized once at startup)
 * so receiving a packet never allocates — bytes land directly in the client's off-heap rows. The
 * decoded {@link StructArenaSyncPacket} wraps that same client arena, so the handler reads the freshly
 * synced data in place.
 *
 * <p>RU: Декодирование переиспользует переданную клиентскую {@link StructArena} (выделенную один раз
 * при старте), поэтому приём пакета не аллоцирует — байты попадают прямо в off-heap строки клиента.
 */
public final class StructArenaSyncCodec implements PayloadCodec<StructArenaSyncPacket> {

    private final StructArena clientTarget;

    /**
     * @param clientTarget the pre-allocated client-side arena that decoded rows are written into.
     *                     Must share the {@code StructLayout} used by the sender.
     */
    public StructArenaSyncCodec(StructArena clientTarget) {
        this.clientTarget = clientTarget;
    }

    @Override
    public String channelId() {
        return StructArenaSyncPacket.CHANNEL;
    }

    @Override
    public void encode(StructArenaSyncPacket payload, PayloadSink sink) {
        sink.writeInt(payload.rowCount());
        sink.writeSegment(payload.segment(), payload.payloadBytes());
    }

    @Override
    public StructArenaSyncPacket decode(PayloadSource source) {
        final int rowCount = source.readInt();
        final long maxRows = clientTarget.count();
        if (rowCount < 0 || rowCount > maxRows) {
            throw new IllegalArgumentException(
                    "StructArenaSync rowCount " + rowCount + " exceeds client arena capacity " + maxRows);
        }
        final long bytes = (long) rowCount * clientTarget.layout().stride();
        source.readSegment(clientTarget.segment(), bytes);
        return new StructArenaSyncPacket(clientTarget, rowCount);
    }
}
