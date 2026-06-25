/*
 * Aetherium Framework — compute kernel element type.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

/**
 * The scalar element type of a compute kernel's array operands.
 *
 * <p>EN: The strict Java subset supports {@code float[]} and {@code int[]}. The {@code byteSize} feeds
 * the SPIR-V {@code ArrayStride} decoration so the emitted std430 buffer layout matches the JVM array.
 * RU: Строгое подмножество Java поддерживает {@code float[]} и {@code int[]}. {@code byteSize} питает
 * декорацию SPIR-V {@code ArrayStride}, чтобы раскладка std430-буфера совпадала с массивом JVM.
 */
public enum ComputeElementType {
    FLOAT32(4),
    INT32(4);

    private final int byteSize;

    ComputeElementType(int byteSize) {
        this.byteSize = byteSize;
    }

    public int byteSize() {
        return byteSize;
    }

    public boolean isFloat() {
        return this == FLOAT32;
    }
}
