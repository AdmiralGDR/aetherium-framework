/*
 * Aetherium Framework — data-oriented struct arena.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.compute;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * A contiguous, off-heap array of structs — the cache-friendly entity store.
 *
 * <p>EN: Allocates {@code count} elements of a {@link StructLayout} back-to-back in a single
 * off-heap {@link Arena} block. Field access is {@code segment.get(layout, index*stride + offset)}:
 * one bounds-checked memory op, {@code O(1)}, no allocation, no GC. Because elements are contiguous
 * and disjoint slices can be updated independently, thousands of entities can be advanced in
 * parallel on virtual threads with no locks and no {@code ConcurrentModificationException}. The
 * whole store frees deterministically on {@link #close()}. Zero boilerplate for the modder:
 * {@code StructArena.allocate(layout, 10_000)} then {@code setDouble(i, field, v)}.
 *
 * <p>RU: Размещает {@code count} элементов {@link StructLayout} подряд в одном off-heap блоке
 * {@link Arena}. Доступ к полю — {@code segment.get(layout, index*stride + offset)}: одна операция
 * с проверкой границ, {@code O(1)}, без аллокаций и GC. Поскольку элементы непрерывны, а
 * непересекающиеся срезы можно обновлять независимо, тысячи сущностей можно продвигать параллельно
 * на виртуальных потоках без блокировок и {@code ConcurrentModificationException}. Всё хранилище
 * освобождается детерминированно при {@link #close()}.
 */
public final class StructArena implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment segment;
    private final StructLayout layout;
    private final long count;
    private final long stride;

    private StructArena(Arena arena, MemorySegment segment, StructLayout layout, long count) {
        this.arena = arena;
        this.segment = segment;
        this.layout = layout;
        this.count = count;
        this.stride = layout.stride();
    }

    /** Allocate {@code count} contiguous elements of {@code layout} in a fresh shared arena. */
    public static StructArena allocate(StructLayout layout, long count) {
        Objects.requireNonNull(layout, "layout");
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0: " + count);
        }
        Arena arena = Arena.ofShared();
        try {
            MemorySegment segment = arena.allocate(layout.stride() * count, layout.maxAlignment());
            return new StructArena(arena, segment, layout, count);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    public long count() {
        return count;
    }

    public StructLayout layout() {
        return layout;
    }

    /** Live segment view (advanced use, e.g. SIMD bulk passes). */
    public MemorySegment segment() {
        return segment;
    }

    private long byteOffset(long index, StructField field) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("index " + index + " out of bounds for count " + count);
        }
        return index * stride + field.offset();
    }

    // --- typed, bounds-checked accessors (the hot path) ----------------------------------------

    public int getInt(long index, StructField field) {
        return segment.get(ValueLayout.JAVA_INT, byteOffset(index, field));
    }

    public void setInt(long index, StructField field, int value) {
        segment.set(ValueLayout.JAVA_INT, byteOffset(index, field), value);
    }

    public long getLong(long index, StructField field) {
        return segment.get(ValueLayout.JAVA_LONG, byteOffset(index, field));
    }

    public void setLong(long index, StructField field, long value) {
        segment.set(ValueLayout.JAVA_LONG, byteOffset(index, field), value);
    }

    public float getFloat(long index, StructField field) {
        return segment.get(ValueLayout.JAVA_FLOAT, byteOffset(index, field));
    }

    public void setFloat(long index, StructField field, float value) {
        segment.set(ValueLayout.JAVA_FLOAT, byteOffset(index, field), value);
    }

    public double getDouble(long index, StructField field) {
        return segment.get(ValueLayout.JAVA_DOUBLE, byteOffset(index, field));
    }

    public void setDouble(long index, StructField field, double value) {
        segment.set(ValueLayout.JAVA_DOUBLE, byteOffset(index, field), value);
    }

    /** Total off-heap bytes backing this arena. */
    public long byteSize() {
        return stride * count;
    }

    @Override
    public void close() {
        arena.close();
    }
}
