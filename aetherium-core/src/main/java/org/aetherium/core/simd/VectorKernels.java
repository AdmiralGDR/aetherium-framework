/*
 * Aetherium Framework — Java Vector API (SIMD) kernels, isolated for lazy loading.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * The hardware-accelerated SIMD kernels — the <em>only</em> class that references
 * {@code jdk.incubator.vector}.
 *
 * <p>EN: The Vector API is an incubator module, so a hard dependency would force every consumer onto
 * {@code --add-modules jdk.incubator.vector}. This class is therefore <strong>physically isolated</strong>
 * and only touched by {@link SimdMath} after it has confirmed the module is present; if it is absent the
 * class is never loaded and {@code SimdMath} runs its scalar fallback — so the framework never throws a
 * {@code NoClassDefFoundError} for the incubator module. Operations use {@link FloatVector#SPECIES_PREFERRED}
 * so the JIT picks the widest lane the CPU supports (256-bit AVX2 → 8 floats, 512-bit AVX-512 → 16),
 * and read/write off-heap {@link MemorySegment}s directly (the {@code StructArena}/{@code VectorLane}
 * backing store) with no copy.
 *
 * <p>RU: Vector API — инкубаторный модуль, поэтому жёсткая зависимость навязала бы всем потребителям
 * {@code --add-modules jdk.incubator.vector}. Этот класс <strong>физически изолирован</strong> и
 * затрагивается {@link SimdMath} только после подтверждения наличия модуля; если его нет, класс не
 * загружается, и {@code SimdMath} выполняет скалярный откат — фреймворк никогда не бросает
 * {@code NoClassDefFoundError}. Операции используют {@link FloatVector#SPECIES_PREFERRED}, чтобы JIT
 * выбрал самую широкую полосу CPU (256-бит AVX2 → 8 float, 512-бит AVX-512 → 16), и читают/пишут
 * off-heap {@link MemorySegment} напрямую без копирования.
 */
final class VectorKernels {

    private static final VectorSpecies<Float> F = FloatVector.SPECIES_PREFERRED;
    private static final ByteOrder ORDER = ByteOrder.nativeOrder();

    private VectorKernels() {
    }

    /** Bit width of the preferred float lane (e.g. 256 or 512) — the SIMD width actually used. */
    static int preferredFloatBits() {
        return F.vectorBitSize();
    }

    /** Number of float lanes per vector op (e.g. 8 for AVX2, 16 for AVX-512). */
    static int floatLaneCount() {
        return F.length();
    }

    /** {@code dst[i] += src[i] * scale} over {@code count} contiguous floats in two off-heap segments. */
    static void mulAddInPlace(MemorySegment dst, MemorySegment src, float scale, long count) {
        long i = 0;
        long upper = count - (count % F.length());
        for (; i < upper; i += F.length()) {
            long off = i * Float.BYTES;
            FloatVector vd = FloatVector.fromMemorySegment(F, dst, off, ORDER);
            FloatVector vs = FloatVector.fromMemorySegment(F, src, off, ORDER);
            vd.add(vs.mul(scale)).intoMemorySegment(dst, off, ORDER);
        }
        for (; i < count; i++) {
            long off = i * Float.BYTES;
            float d = dst.get(ValueLayout.JAVA_FLOAT, off);
            float s = src.get(ValueLayout.JAVA_FLOAT, off);
            dst.set(ValueLayout.JAVA_FLOAT, off, Math.fma(s, scale, d));
        }
    }

    /** {@code data[i] *= scale} over {@code count} contiguous floats in an off-heap segment. */
    static void scaleInPlace(MemorySegment data, float scale, long count) {
        long i = 0;
        long upper = count - (count % F.length());
        for (; i < upper; i += F.length()) {
            long off = i * Float.BYTES;
            FloatVector.fromMemorySegment(F, data, off, ORDER).mul(scale).intoMemorySegment(data, off, ORDER);
        }
        for (; i < count; i++) {
            long off = i * Float.BYTES;
            data.set(ValueLayout.JAVA_FLOAT, off, data.get(ValueLayout.JAVA_FLOAT, off) * scale);
        }
    }

    /** Horizontal sum of {@code count} contiguous floats in an off-heap segment. */
    static float sum(MemorySegment data, long count) {
        FloatVector acc = FloatVector.zero(F);
        long i = 0;
        long upper = count - (count % F.length());
        for (; i < upper; i += F.length()) {
            acc = acc.add(FloatVector.fromMemorySegment(F, data, i * Float.BYTES, ORDER));
        }
        float total = acc.reduceLanes(VectorOperators.ADD);
        for (; i < count; i++) {
            total += data.get(ValueLayout.JAVA_FLOAT, i * Float.BYTES);
        }
        return total;
    }

    /** {@code out[i] = a[i]*scale + b[i]} over float arrays (heap variant of the FMA kernel). */
    static void mulAdd(float[] a, float[] b, float scale, float[] out) {
        int n = a.length;
        int i = 0;
        int upper = n - (n % F.length());
        for (; i < upper; i += F.length()) {
            FloatVector va = FloatVector.fromArray(F, a, i);
            FloatVector vb = FloatVector.fromArray(F, b, i);
            va.mul(scale).add(vb).intoArray(out, i);
        }
        for (; i < n; i++) {
            out[i] = Math.fma(a[i], scale, b[i]);
        }
    }
}
