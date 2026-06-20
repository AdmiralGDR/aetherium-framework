/*
 * Aetherium Framework — SIMD bulk-math facade (Vector API accelerated, scalar fallback).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.simd;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Bulk vector math with hardware SIMD acceleration via the Java Vector API, and a correct scalar
 * fallback when the incubator module is absent.
 *
 * <p>EN: This is the stable, zero-boilerplate facade. Callers never see {@code jdk.incubator.vector};
 * they call {@link #mulAddInPlace(MemorySegment, MemorySegment, float, long)} (particle integration:
 * {@code pos += vel*dt}), {@link #scaleInPlace}, {@link #sum}, the {@code float[]}/{@code double[]}
 * FMA overloads, etc. When {@link #isVectorApiAvailable()} the calls dispatch to {@link VectorKernels}
 * (256/512-bit wide, reading/writing off-heap {@link MemorySegment}s with no copy — ideal for
 * {@code StructArena}/{@link VectorLane} stores); otherwise an identical scalar implementation runs.
 * Every accelerated call is wrapped so that even a runtime linkage surprise degrades to scalar rather
 * than failing — availability over fragility.
 *
 * <p>RU: Стабильный фасад без шаблонного кода. Вызывающие никогда не видят {@code jdk.incubator.vector};
 * они вызывают {@link #mulAddInPlace(MemorySegment, MemorySegment, float, long)} (интегрирование
 * частиц: {@code pos += vel*dt}), {@link #scaleInPlace}, {@link #sum}, FMA-перегрузки для
 * {@code float[]}/{@code double[]} и т.д. При {@link #isVectorApiAvailable()} вызовы уходят в
 * {@link VectorKernels} (полосы 256/512 бит, чтение/запись off-heap {@link MemorySegment} без
 * копирования); иначе выполняется идентичная скалярная реализация. Каждый ускоренный вызов обёрнут так,
 * что даже сюрприз линковки в рантайме деградирует в скаляр, а не падает.
 */
public final class SimdMath {

    private static final boolean VECTOR_API_PRESENT = detectVectorApi();

    private SimdMath() {
    }

    /** True if {@code jdk.incubator.vector} is on the module path (the SIMD acceleration hook). */
    public static boolean isVectorApiAvailable() {
        return VECTOR_API_PRESENT;
    }

    /** Bit width of the SIMD float lane actually in use (e.g. 256 or 512); 0 if scalar fallback. */
    public static int simdFloatBits() {
        if (VECTOR_API_PRESENT) {
            try {
                return VectorKernels.preferredFloatBits();
            } catch (Throwable degraded) {
                return 0;
            }
        }
        return 0;
    }

    /** Number of float lanes processed per SIMD op (e.g. 8 / 16); 1 if scalar fallback. */
    public static int simdFloatLanes() {
        if (VECTOR_API_PRESENT) {
            try {
                return VectorKernels.floatLaneCount();
            } catch (Throwable degraded) {
                return 1;
            }
        }
        return 1;
    }

    /** A short human-readable description of the active backend (for diagnostics/CLI). */
    public static String backend() {
        return VECTOR_API_PRESENT
                ? "Vector API (jdk.incubator.vector), " + simdFloatBits() + "-bit lanes (" + simdFloatLanes() + " floats/op)"
                : "scalar (jdk.incubator.vector not on module path)";
    }

    // --- off-heap float kernels (the particle / StructArena hot path) ---------------------------

    /**
     * {@code dst[i] += src[i] * scale} for {@code count} contiguous floats in two off-heap segments —
     * the SIMD particle-integration primitive ({@code position += velocity * dt}).
     */
    public static void mulAddInPlace(MemorySegment dst, MemorySegment src, float scale, long count) {
        if (VECTOR_API_PRESENT) {
            try {
                VectorKernels.mulAddInPlace(dst, src, scale, count);
                return;
            } catch (Throwable degraded) {
                // fall through to scalar
            }
        }
        for (long i = 0; i < count; i++) {
            long off = i * Float.BYTES;
            float d = dst.get(ValueLayout.JAVA_FLOAT, off);
            float s = src.get(ValueLayout.JAVA_FLOAT, off);
            dst.set(ValueLayout.JAVA_FLOAT, off, Math.fma(s, scale, d));
        }
    }

    /** {@code data[i] *= scale} for {@code count} contiguous off-heap floats. */
    public static void scaleInPlace(MemorySegment data, float scale, long count) {
        if (VECTOR_API_PRESENT) {
            try {
                VectorKernels.scaleInPlace(data, scale, count);
                return;
            } catch (Throwable degraded) {
                // fall through to scalar
            }
        }
        for (long i = 0; i < count; i++) {
            long off = i * Float.BYTES;
            data.set(ValueLayout.JAVA_FLOAT, off, data.get(ValueLayout.JAVA_FLOAT, off) * scale);
        }
    }

    /** Horizontal sum of {@code count} contiguous off-heap floats. */
    public static float sum(MemorySegment data, long count) {
        if (VECTOR_API_PRESENT) {
            try {
                return VectorKernels.sum(data, count);
            } catch (Throwable degraded) {
                // fall through to scalar
            }
        }
        float total = 0f;
        for (long i = 0; i < count; i++) {
            total += data.get(ValueLayout.JAVA_FLOAT, i * Float.BYTES);
        }
        return total;
    }

    // --- heap array kernels ---------------------------------------------------------------------

    /** {@code out[i] = a[i]*scale + b[i]} over equal-length float arrays. */
    public static void mulAdd(float[] a, float[] b, float scale, float[] out) {
        checkSameLength(a.length, b.length, out.length);
        if (VECTOR_API_PRESENT) {
            try {
                VectorKernels.mulAdd(a, b, scale, out);
                return;
            } catch (Throwable degraded) {
                // fall through to scalar
            }
        }
        for (int i = 0; i < a.length; i++) {
            out[i] = Math.fma(a[i], scale, b[i]);
        }
    }

    /** {@code out[i] = a[i]*scale + b[i]} (fused multiply-add) over double arrays. Scalar. */
    public static void mulAdd(double[] a, double[] b, double scale, double[] out) {
        int n = checkSameLength(a.length, b.length, out.length);
        for (int i = 0; i < n; i++) {
            out[i] = Math.fma(a[i], scale, b[i]);
        }
    }

    /** {@code data[i] *= scale} in place over a double array. Scalar. */
    public static void scaleInPlace(double[] data, double scale) {
        for (int i = 0; i < data.length; i++) {
            data[i] *= scale;
        }
    }

    /** Dot product of two equal-length double vectors. Scalar. */
    public static double dot(double[] a, double[] b) {
        int n = checkSameLength(a.length, b.length, a.length);
        double s = 0;
        for (int i = 0; i < n; i++) {
            s = Math.fma(a[i], b[i], s);
        }
        return s;
    }

    private static int checkSameLength(int a, int b, int c) {
        if (a != b || a != c) {
            throw new IllegalArgumentException("array lengths differ");
        }
        return a;
    }

    private static boolean detectVectorApi() {
        try {
            Class.forName("jdk.incubator.vector.FloatVector");
            return true;
        } catch (Throwable notPresent) {
            return false;
        }
    }
}
