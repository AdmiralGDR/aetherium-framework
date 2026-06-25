/*
 * Aetherium Framework — network payload sink.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import java.lang.foreign.MemorySegment;

/**
 * Loader-agnostic write side of a packet buffer.
 *
 * <p>EN: A pure abstraction the loader adapts over the platform's network buffer (e.g. NeoForge's
 * {@code RegistryFriendlyByteBuf}). The {@link #writeSegment} primitive is the zero-GC path: it copies
 * an off-heap FFM {@link MemorySegment} straight into the outbound buffer with no intermediate
 * {@code byte[]} or boxing — the contract that makes {@code StructArena} sync allocation-free.
 *
 * <p>RU: Чистая абстракция записи, которую загрузчик адаптирует над сетевым буфером платформы.
 * {@link #writeSegment} — путь без аллокаций: копирует off-heap {@link MemorySegment} прямо в
 * исходящий буфер без промежуточного {@code byte[]} и боксинга.
 */
public interface PayloadSink {

    void writeInt(int value);

    void writeLong(long value);

    /** Bulk-copy {@code length} bytes from an off-heap segment into the buffer (zero-GC). */
    void writeSegment(MemorySegment source, long length);

    /**
     * EN: Length-prefixed byte block, written via the segment primitive (used by {@link TreeCodec} for
     * strings/blobs). A length of 0 writes only the prefix. Built on the existing primitives so no loader
     * adapter change is required.
     * RU: Длина-префиксованный блок байт через сегментный примитив (для строк/блобов в {@link TreeCodec}).
     */
    default void writeBytes(byte[] bytes) {
        writeInt(bytes.length);
        if (bytes.length > 0) {
            writeSegment(MemorySegment.ofArray(bytes), bytes.length);
        }
    }
}
