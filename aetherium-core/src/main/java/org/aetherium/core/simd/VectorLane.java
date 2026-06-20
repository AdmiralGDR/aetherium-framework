/*
 * Aetherium Framework — off-heap SIMD lane (Structure-of-Arrays column).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.simd;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A contiguous, off-heap column of {@code float}s — the zero-boilerplate SIMD store for one component
 * of a particle/entity system (a Structure-of-Arrays lane).
 *
 * <p>EN: SIMD wants its operands packed back-to-back, but an Array-of-Structs (interleaved fields, like
 * {@code StructArena}) makes a single field strided and un-vectorizable. {@code VectorLane} is the
 * dual: one component (all X positions, or all X velocities) stored contiguously off-heap, so the whole
 * column is a single wide SIMD sweep. A modder writes:
 *
 * <pre>{@code
 * try (VectorLane posX = VectorLane.allocate(100_000);
 *      VectorLane velX = VectorLane.allocate(100_000)) {
 *     ...
 *     posX.mulAddFrom(velX, dt);   // pos += vel*dt across 100k particles, 256/512-bit wide
 * }
 * }</pre>
 *
 * No FFM, no Vector API, no boilerplate — {@link SimdMath} picks the widest hardware lane (or scalar).
 * The lane frees deterministically on {@link #close()}.
 *
 * <p>RU: SIMD требует операнды, упакованные подряд, но Array-of-Structs (чередующиеся поля, как в
 * {@code StructArena}) делает отдельное поле strided и невекторизуемым. {@code VectorLane} — двойник:
 * один компонент (все X-позиции или все X-скорости) хранится непрерывно off-heap, поэтому вся колонка —
 * один широкий SIMD-проход. Без FFM, без Vector API, без шаблонного кода — {@link SimdMath} выбирает
 * самую широкую полосу железа (или скаляр). Полоса освобождается детерминированно при {@link #close()}.
 */
public final class VectorLane implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment segment;
    private final long count;

    private VectorLane(Arena arena, MemorySegment segment, long count) {
        this.arena = arena;
        this.segment = segment;
        this.count = count;
    }

    /** Allocate a fresh off-heap lane of {@code count} floats (zero-initialized). */
    public static VectorLane allocate(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0: " + count);
        }
        Arena arena = Arena.ofShared();
        try {
            MemorySegment seg = arena.allocate(count * Float.BYTES, Float.BYTES);
            return new VectorLane(arena, seg, count);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    public long count() {
        return count;
    }

    /** The backing off-heap segment (advanced use). */
    public MemorySegment segment() {
        return segment;
    }

    public float get(long index) {
        return segment.get(ValueLayout.JAVA_FLOAT, checkedOffset(index));
    }

    public void set(long index, float value) {
        segment.set(ValueLayout.JAVA_FLOAT, checkedOffset(index), value);
    }

    /** Set every element to {@code value}. */
    public void fill(float value) {
        for (long i = 0; i < count; i++) {
            segment.set(ValueLayout.JAVA_FLOAT, i * Float.BYTES, value);
        }
    }

    /** SIMD: {@code this[i] += src[i] * scale} (the particle-integration step). */
    public void mulAddFrom(VectorLane src, float scale) {
        requireSameLength(src);
        SimdMath.mulAddInPlace(segment, src.segment, scale, count);
    }

    /** SIMD: {@code this[i] *= scale}. */
    public void scale(float scale) {
        SimdMath.scaleInPlace(segment, scale, count);
    }

    /** SIMD: horizontal sum of the lane. */
    public float sum() {
        return SimdMath.sum(segment, count);
    }

    private long checkedOffset(long index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("index " + index + " out of bounds for count " + count);
        }
        return index * Float.BYTES;
    }

    private void requireSameLength(VectorLane other) {
        if (other.count != count) {
            throw new IllegalArgumentException("lane length mismatch: " + count + " vs " + other.count);
        }
    }

    @Override
    public void close() {
        arena.close();
    }
}
