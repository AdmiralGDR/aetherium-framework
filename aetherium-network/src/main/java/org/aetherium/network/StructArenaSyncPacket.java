/*
 * Aetherium Framework — StructArena sync packet.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.aetherium.core.compute.StructArena;

import java.lang.foreign.MemorySegment;

/**
 * A zero-GC payload that ships {@code rowCount} contiguous rows of an off-heap {@link StructArena}.
 *
 * <p>EN: The packet holds a reference to the arena, not a copy — encoding bulk-copies the off-heap
 * bytes straight into the network buffer ({@link PayloadSink#writeSegment}), and decoding reads them
 * straight into the client arena's off-heap memory ({@link PayloadSource#readSegment}). No
 * intermediate {@code byte[]}, no per-row objects, no boxing: the server computes off-heap and the
 * client receives off-heap, end to end. See {@link StructArenaSyncCodec}.
 *
 * <p>RU: Пакет хранит ссылку на арену, а не копию — кодирование копирует off-heap байты напрямую в
 * сетевой буфер, декодирование — напрямую в off-heap память клиентской арены. Без промежуточного
 * {@code byte[]}, без объектов на строку и без боксинга.
 */
public final class StructArenaSyncPacket implements NetworkPayload {

    public static final String CHANNEL = "aetherium:struct_arena_sync";

    private final StructArena arena;
    private final int rowCount;

    public StructArenaSyncPacket(StructArena arena, int rowCount) {
        this.arena = arena;
        this.rowCount = rowCount;
    }

    @Override
    public String channelId() {
        return CHANNEL;
    }

    public StructArena arena() {
        return arena;
    }

    public int rowCount() {
        return rowCount;
    }

    /** Number of off-heap bytes this packet transmits ({@code rowCount × stride}). */
    public long payloadBytes() {
        return (long) rowCount * arena.layout().stride();
    }

    /** The off-heap memory backing the rows — copied directly to/from the wire (zero-GC). */
    public MemorySegment segment() {
        return arena.segment();
    }
}
