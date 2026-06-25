/*
 * Aetherium Framework — example compute kernel (array addition).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute.sample;

import org.aetherium.compute.AetheriumComputeShader;

/**
 * Plain-Java compute kernels a modder might write — compiled to SPIR-V by the framework.
 *
 * <p>EN: These are ordinary methods over primitive arrays. No JNI, no FFM, no shader language: the
 * {@link org.aetherium.compute.JavaToSpirvCompiler} reads the bytecode and emits the GPU binary.
 * RU: Это обычные методы над примитивными массивами. Ни JNI, ни FFM, ни языка шейдеров:
 * {@link org.aetherium.compute.JavaToSpirvCompiler} читает байт-код и выпускает GPU-бинарь.
 */
public final class ArrayAddKernel {

    private ArrayAddKernel() {
    }

    /** EN/RU: {@code c[i] = a[i] + b[i]} — the canonical float array-addition kernel. */
    @AetheriumComputeShader(localSizeX = 64)
    public static void add(float[] a, float[] b, float[] c, int n) {
        for (int i = 0; i < n; i++) {
            c[i] = a[i] + b[i];
        }
    }

    /** EN/RU: {@code c[i] = a[i] * b[i]} — integer element-wise multiply, to show op/type generality. */
    @AetheriumComputeShader
    public static void multiplyInts(int[] a, int[] b, int[] c, int n) {
        for (int i = 0; i < n; i++) {
            c[i] = a[i] * b[i];
        }
    }

    /**
     * EN: {@code out[i] = sin(in[i])} — a transcendental kernel. The {@code Math.sin} call is lowered to
     * a GLSL.std.450 {@code OpExtInst Sin}, proving the SPIR-V math polyfills.
     * RU: {@code out[i] = sin(in[i])} — трансцендентное ядро. Вызов {@code Math.sin} понижается в
     * GLSL.std.450 {@code OpExtInst Sin}, доказывая SPIR-V math-полифилы.
     */
    @AetheriumComputeShader(localSizeX = 128)
    public static void sineWave(float[] in, float[] out, int n) {
        for (int i = 0; i < n; i++) {
            out[i] = (float) Math.sin(in[i]);
        }
    }
}
