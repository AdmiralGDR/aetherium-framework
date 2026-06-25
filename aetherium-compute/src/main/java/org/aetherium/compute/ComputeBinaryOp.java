/*
 * Aetherium Framework — compute kernel binary operation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

/**
 * The element-wise arithmetic a kernel applies: {@code dst[i] = a[i] OP b[i]}.
 *
 * <p>EN: Each op maps to the matching SPIR-V opcode for the operand type — floating-point
 * ({@code OpFAdd}/{@code OpFSub}/{@code OpFMul}) or integer ({@code OpIAdd}/{@code OpISub}/{@code OpIMul}).
 * The {@link JavaToSpirvCompiler} recognises the corresponding JVM arithmetic opcode in the kernel's
 * bytecode and selects the op here.
 * RU: Каждая операция отображается на соответствующий опкод SPIR-V для типа операнда — с плавающей
 * точкой ({@code OpFAdd}/{@code OpFSub}/{@code OpFMul}) или целочисленный
 * ({@code OpIAdd}/{@code OpISub}/{@code OpIMul}). {@link JavaToSpirvCompiler} распознаёт
 * соответствующий арифметический опкод JVM в байт-коде ядра и выбирает операцию.
 */
public enum ComputeBinaryOp {
    // (floatOpcode, intOpcode) — SPIR-V core opcodes.
    ADD(Spirv.OP_FADD, Spirv.OP_IADD),
    SUB(Spirv.OP_FSUB, Spirv.OP_ISUB),
    MUL(Spirv.OP_FMUL, Spirv.OP_IMUL);

    private final int floatOpcode;
    private final int intOpcode;

    ComputeBinaryOp(int floatOpcode, int intOpcode) {
        this.floatOpcode = floatOpcode;
        this.intOpcode = intOpcode;
    }

    /** The SPIR-V opcode for this op given the operand element type. */
    public int spirvOpcode(ComputeElementType type) {
        return type.isFloat() ? floatOpcode : intOpcode;
    }
}
