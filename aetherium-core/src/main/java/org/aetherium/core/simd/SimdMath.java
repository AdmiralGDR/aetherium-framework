/*
 * Aetherium Framework — SIMD bulk-math bridge (placeholder + scalar fallback).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.simd;

/**
 * Bulk vector math, with a scalar fallback today and a hook for the Java Vector API (SIMD).
 *
 * <p>EN: The Vector API ({@code jdk.incubator.vector}) is an <em>incubator</em> module, so the
 * framework does not hard-depend on it (that would force {@code --add-modules} on every consumer).
 * Instead this class exposes the bulk operations the engine needs — fused multiply-add over arrays,
 * scaling, dot products — with a correct scalar implementation now, and {@link #isVectorApiAvailable()}
 * detects the incubator module at runtime so an accelerated path can be slotted in later without any
 * API change for callers. Operate over off-heap {@code StructArena} fields for cache-friendly bulk math.
 *
 * <p>RU: Vector API ({@code jdk.incubator.vector}) — <em>инкубаторный</em> модуль, поэтому фреймворк
 * не зависит от него жёстко (иначе пришлось бы навязывать {@code --add-modules} всем потребителям).
 * Этот класс предоставляет нужные движку массовые операции — FMA по массивам, масштабирование,
 * скалярное произведение — с корректной скалярной реализацией сейчас, а {@link #isVectorApiAvailable()}
 * определяет инкубаторный модуль во время выполнения, чтобы позже вставить ускоренный путь без
 * изменения API для вызывающих.
 */
public final class SimdMath {

    private static final boolean VECTOR_API_PRESENT = detectVectorApi();

    private SimdMath() {
    }

    /** True if {@code jdk.incubator.vector} is on the module path (the SIMD acceleration hook). */
    public static boolean isVectorApiAvailable() {
        return VECTOR_API_PRESENT;
    }

    /** {@code out[i] = a[i]*scale + b[i]} (fused multiply-add). Scalar today; SIMD-ready. */
    public static void mulAdd(double[] a, double[] b, double scale, double[] out) {
        int n = checkSameLength(a, b, out);
        for (int i = 0; i < n; i++) {
            out[i] = Math.fma(a[i], scale, b[i]);
        }
    }

    /** {@code data[i] *= scale} in place. */
    public static void scaleInPlace(double[] data, double scale) {
        for (int i = 0; i < data.length; i++) {
            data[i] *= scale;
        }
    }

    /** Dot product of two equal-length vectors. */
    public static double dot(double[] a, double[] b) {
        int n = checkSameLength(a, b, a);
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum = Math.fma(a[i], b[i], sum);
        }
        return sum;
    }

    private static int checkSameLength(double[] a, double[] b, double[] c) {
        if (a.length != b.length || a.length != c.length) {
            throw new IllegalArgumentException("array lengths differ");
        }
        return a.length;
    }

    private static boolean detectVectorApi() {
        try {
            Class.forName("jdk.incubator.vector.DoubleVector");
            return true;
        } catch (Throwable notPresent) {
            return false;
        }
    }
}
