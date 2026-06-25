/*
 * Aetherium Framework — delta-sync codec (bitmap + dirty runs, zero-GC).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.aetherium.core.compute.StructArena;

/**
 * {@link PayloadCodec} for {@link StructArenaDeltaPacket}: {@code [rowCount][bitmapWords][words...][dirty runs]}.
 *
 * <p>EN: Encoding writes the row count and the {@link DirtyBitmap} words, then streams each contiguous run
 * of dirty rows as a single off-heap {@code writeSegment} sliced straight from the server arena — no
 * intermediate {@code byte[]}, no per-row write. Decoding reads the bitmap, reconstructs the identical
 * runs deterministically, and reads each run straight into the matching slice of the pre-allocated client
 * arena — so unchanged rows keep their previous values and only the dirty bytes are touched. Both sides
 * are allocation-free on the Java heap.
 *
 * <p>RU: Кодирование пишет число строк и слова {@link DirtyBitmap}, затем стримит каждый непрерывный
 * пробег грязных строк одним off-heap {@code writeSegment}, нарезанным прямо из серверной арены — без
 * промежуточного {@code byte[]} и построчной записи. Декодирование читает битовую карту, детерминированно
 * восстанавливает те же пробеги и читает каждый прямо в соответствующий срез предвыделенной клиентской
 * арены — поэтому неизменённые строки сохраняют прежние значения, и трогаются только грязные байты.
 */
public final class StructArenaDeltaCodec implements PayloadCodec<StructArenaDeltaPacket> {

    private final StructArena clientTarget;

    /**
     * @param clientTarget pre-allocated client arena that decoded dirty rows are written into; it must
     *                     share the sender's {@code StructLayout} and retains untouched rows between deltas.
     */
    public StructArenaDeltaCodec(StructArena clientTarget) {
        this.clientTarget = clientTarget;
    }

    @Override
    public String channelId() {
        return StructArenaDeltaPacket.CHANNEL;
    }

    @Override
    public void encode(StructArenaDeltaPacket payload, PayloadSink sink) {
        final int rowCount = payload.rowCount();
        final DirtyBitmap dirty = payload.dirty();
        final long stride = payload.arena().layout().stride();

        sink.writeInt(rowCount);
        sink.writeInt(dirty.wordCount());
        for (int i = 0; i < dirty.wordCount(); i++) {
            sink.writeLong(dirty.word(i));
        }
        // Stream each contiguous dirty run as one zero-copy off-heap slice.
        dirty.forEachRun((startRow, runRows) -> {
            long byteOffset = (long) startRow * stride;
            long byteLength = (long) runRows * stride;
            sink.writeSegment(payload.arena().segment().asSlice(byteOffset, byteLength), byteLength);
        });
    }

    @Override
    public StructArenaDeltaPacket decode(PayloadSource source) {
        final int rowCount = source.readInt();
        final long maxRows = clientTarget.count();
        if (rowCount < 0 || rowCount > maxRows) {
            throw new IllegalArgumentException(
                    "StructArenaDelta rowCount " + rowCount + " exceeds client arena capacity " + maxRows);
        }
        final int wordCount = source.readInt();
        long[] words = new long[wordCount];
        for (int i = 0; i < wordCount; i++) {
            words[i] = source.readLong();
        }
        DirtyBitmap dirty = DirtyBitmap.fromWords(words, rowCount);

        final long stride = clientTarget.layout().stride();
        // Read each contiguous run straight into the client arena's matching slice (untouched rows persist).
        dirty.forEachRun((startRow, runRows) -> {
            long byteOffset = (long) startRow * stride;
            long byteLength = (long) runRows * stride;
            source.readSegment(clientTarget.segment().asSlice(byteOffset, byteLength), byteLength);
        });
        return new StructArenaDeltaPacket(clientTarget, rowCount, dirty);
    }
}
