/*
 * Aetherium Framework — SPIR-V binary-format constants.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

/**
 * The slice of the SPIR-V specification this compiler emits — magic word, version, opcodes and enums.
 *
 * <p>EN: SPIR-V is a stream of 32-bit words. The first five form the header
 * ({@link #MAGIC} {@code 0x07230203}, {@link #VERSION_1_0}, generator, id-bound, schema); thereafter
 * each instruction's first word packs {@code (wordCount << 16) | opcode}. Only the opcodes and
 * enumerants used by the array-arithmetic compute kernel are declared here, with their numeric values
 * from the Khronos SPIR-V registry.
 * RU: SPIR-V — поток 32-битных слов. Первые пять образуют заголовок
 * ({@link #MAGIC} {@code 0x07230203}, {@link #VERSION_1_0}, генератор, граница id, схема); далее первое
 * слово каждой инструкции упаковывает {@code (wordCount << 16) | opcode}. Здесь объявлены только
 * опкоды и перечислимые, используемые ядром поэлементной арифметики, со значениями из реестра
 * Khronos SPIR-V.
 */
final class Spirv {

    private Spirv() {
    }

    // --- header ---------------------------------------------------------------------------------
    /** SPIR-V magic number — the canonical proof-of-format word. */
    static final int MAGIC = 0x07230203;
    /** SPIR-V 1.0 version word (major 1, minor 0). */
    static final int VERSION_1_0 = 0x00010000;
    /** Generator magic; 0 is the reserved "Khronos / unspecified tool" value, valid in a header. */
    static final int GENERATOR = 0;
    /** Reserved; must be 0 in SPIR-V 1.x. */
    static final int SCHEMA = 0;

    // --- opcodes (Khronos registry numeric values) ----------------------------------------------
    static final int OP_SOURCE = 3;
    static final int OP_NAME = 5;
    static final int OP_MEMBER_NAME = 6;
    static final int OP_EXT_INST_IMPORT = 11;   // import an extended instruction set (e.g. GLSL.std.450)
    static final int OP_EXT_INST = 12;          // call one instruction from an imported set
    static final int OP_ENTRY_POINT = 15;
    static final int OP_EXECUTION_MODE = 16;
    static final int OP_CAPABILITY = 17;
    static final int OP_MEMORY_MODEL = 14;
    static final int OP_TYPE_VOID = 19;
    static final int OP_TYPE_INT = 21;
    static final int OP_TYPE_FLOAT = 22;
    static final int OP_TYPE_VECTOR = 23;
    static final int OP_TYPE_RUNTIME_ARRAY = 29;
    static final int OP_TYPE_STRUCT = 30;
    static final int OP_TYPE_POINTER = 32;
    static final int OP_TYPE_FUNCTION = 33;
    static final int OP_CONSTANT = 43;
    static final int OP_FUNCTION = 54;
    static final int OP_FUNCTION_END = 56;
    static final int OP_VARIABLE = 59;
    static final int OP_LOAD = 61;
    static final int OP_STORE = 62;
    static final int OP_ACCESS_CHAIN = 65;
    static final int OP_DECORATE = 71;
    static final int OP_MEMBER_DECORATE = 72;
    static final int OP_LABEL = 248;
    static final int OP_RETURN = 253;

    static final int OP_IADD = 128;
    static final int OP_ISUB = 130;
    static final int OP_IMUL = 132;
    static final int OP_FADD = 129;
    static final int OP_FSUB = 131;
    static final int OP_FMUL = 133;

    // --- enumerants -----------------------------------------------------------------------------
    static final int CAPABILITY_SHADER = 1;
    static final int ADDRESSING_LOGICAL = 0;
    static final int MEMORY_MODEL_GLSL450 = 1;
    static final int EXEC_MODEL_GLCOMPUTE = 5;
    static final int EXEC_MODE_LOCAL_SIZE = 17;
    static final int SOURCE_LANG_UNKNOWN = 0;

    static final int STORAGE_UNIFORM = 2;   // SSBO via BufferBlock in SPIR-V 1.0
    static final int STORAGE_INPUT = 1;     // built-in inputs (gl_GlobalInvocationID)

    static final int DECORATION_BLOCK = 2;
    static final int DECORATION_BUFFER_BLOCK = 3;
    static final int DECORATION_ARRAY_STRIDE = 6;
    static final int DECORATION_BUILTIN = 11;
    static final int DECORATION_BINDING = 33;
    static final int DECORATION_DESCRIPTOR_SET = 34;
    static final int DECORATION_OFFSET = 35;

    static final int BUILTIN_GLOBAL_INVOCATION_ID = 28;

    // --- GLSL.std.450 extended instruction set --------------------------------------------------
    /** The canonical name imported via {@link #OP_EXT_INST_IMPORT}. */
    static final String EXT_GLSL_STD_450 = "GLSL.std.450";
    // Instruction numbers within the GLSL.std.450 set (Khronos GLSL.std.450 registry).
    static final int GLSL_SIN = 13;
    static final int GLSL_COS = 14;
    static final int GLSL_TAN = 15;
    static final int GLSL_EXP = 27;
    static final int GLSL_LOG = 28;
    static final int GLSL_SQRT = 31;
    static final int GLSL_FABS = 4;
    static final int GLSL_FLOOR = 8;
}
