/*
 * Aetherium Framework — an immutable, reconstructed StructArena state (a "moment in time").
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap.ttd;

import org.aetherium.core.compute.StructField;
import org.aetherium.core.compute.StructLayout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * A read-only view over a byte-exact reconstruction of a {@link org.aetherium.core.compute.StructArena}
 * as it existed at one recorded tick — the inspection surface of the Time-Travel Debugger.
 *
 * <p>EN: The journal reconstructs a past state into a heap {@code byte[]}; this wraps it with the same
 * {@code index * stride + offset} field accessors as the live arena, so a developer can read
 * {@code snapshot.getDouble(entityIndex, xField)} exactly as they would the live store — but frozen at
 * the moment before a crash. Reads use the unaligned FFM layouts because the backing array carries no
 * alignment guarantee. The snapshot is immutable: it never writes back.
 *
 * <p>RU: Журнал реконструирует прошлое состояние в heap-массив {@code byte[]}; этот класс оборачивает
 * его теми же аксессорами {@code index * stride + offset}, что и живая арена, поэтому разработчик читает
 * {@code snapshot.getDouble(entityIndex, xField)} как из живого хранилища — но замороженного в момент
 * перед крахом. Чтение использует невыровненные FFM-раскладки, т.к. массив не гарантирует выравнивание.
 * Снимок неизменяем.
 */
public final class ArenaSnapshot {

    private final StructLayout layout;
    private final long count;
    private final long stride;
    private final byte[] bytes;
    private final MemorySegment view;

    ArenaSnapshot(StructLayout layout, long count, byte[] bytes) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.count = count;
        this.stride = layout.stride();
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.view = MemorySegment.ofArray(bytes);
    }

    public long count() {
        return count;
    }

    public StructLayout layout() {
        return layout;
    }

    /** The raw reconstructed bytes (defensive copy). */
    public byte[] toByteArray() {
        return bytes.clone();
    }

    private long byteOffset(long index, StructField field) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("index " + index + " out of bounds for count " + count);
        }
        return index * stride + field.offset();
    }

    public int getInt(long index, StructField field) {
        return view.get(ValueLayout.JAVA_INT_UNALIGNED, byteOffset(index, field));
    }

    public long getLong(long index, StructField field) {
        return view.get(ValueLayout.JAVA_LONG_UNALIGNED, byteOffset(index, field));
    }

    public float getFloat(long index, StructField field) {
        return view.get(ValueLayout.JAVA_FLOAT_UNALIGNED, byteOffset(index, field));
    }

    public double getDouble(long index, StructField field) {
        return view.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, byteOffset(index, field));
    }
}
