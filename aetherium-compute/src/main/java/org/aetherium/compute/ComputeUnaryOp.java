/*
 * Aetherium Framework — compute kernel unary math operation (java.lang.Math → GLSL.std.450).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

/**
 * The element-wise transcendental/math a kernel applies: {@code dst[i] = fn(a[i])}.
 *
 * <p>EN: Each constant maps a recognised {@code java.lang.Math} call to its
 * <strong>GLSL.std.450</strong> extended-instruction number, emitted as an {@code OpExtInst} against the
 * imported {@code GLSL.std.450} set. The {@link JavaToSpirvCompiler} recognises the {@code INVOKESTATIC
 * java/lang/Math.<name>} in the kernel's bytecode and selects the op here. These are floating-point
 * intrinsics, so a unary-math kernel is {@code float[]}-only (the JVM's {@code double} math is lowered to
 * a 32-bit float intrinsic for the GPU).
 * RU: Каждая константа отображает распознанный вызов {@code java.lang.Math} на номер расширенной
 * инструкции <strong>GLSL.std.450</strong>, эмитируемой как {@code OpExtInst} над импортированным набором
 * {@code GLSL.std.450}. {@link JavaToSpirvCompiler} распознаёт {@code INVOKESTATIC java/lang/Math.<name>}
 * в байт-коде и выбирает операцию. Это интринсики с плавающей точкой, поэтому unary-math ядро только
 * {@code float[]}.
 */
public enum ComputeUnaryOp {
    SIN("sin", Spirv.GLSL_SIN),
    COS("cos", Spirv.GLSL_COS),
    TAN("tan", Spirv.GLSL_TAN),
    SQRT("sqrt", Spirv.GLSL_SQRT),
    EXP("exp", Spirv.GLSL_EXP),
    LOG("log", Spirv.GLSL_LOG),
    ABS("abs", Spirv.GLSL_FABS),
    FLOOR("floor", Spirv.GLSL_FLOOR);

    private final String mathMethod;
    private final int glslExtInstruction;

    ComputeUnaryOp(String mathMethod, int glslExtInstruction) {
        this.mathMethod = mathMethod;
        this.glslExtInstruction = glslExtInstruction;
    }

    /** The {@code java.lang.Math} method name this op recognises (e.g. {@code "sin"}). */
    public String mathMethod() {
        return mathMethod;
    }

    /** The GLSL.std.450 extended-instruction number emitted in the {@code OpExtInst}. */
    public int glslExtInstruction() {
        return glslExtInstruction;
    }

    /** Resolve a {@code java.lang.Math} method name to its op, or {@code null} if unsupported. */
    public static ComputeUnaryOp forMathMethod(String name) {
        for (ComputeUnaryOp op : values()) {
            if (op.mathMethod.equals(name)) {
                return op;
            }
        }
        return null;
    }
}
