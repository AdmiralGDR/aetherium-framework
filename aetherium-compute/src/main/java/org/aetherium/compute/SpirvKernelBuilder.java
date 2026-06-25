/*
 * Aetherium Framework — SPIR-V emitter for element-wise array-arithmetic kernels.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Emits a structurally valid Vulkan SPIR-V compute module for {@code dst[i] = a[i] OP b[i]}.
 *
 * <p>EN: Given the operand element type, the arithmetic op, and the work-group X size, this assembles
 * the canonical SPIR-V word-stream by hand — capability, memory model, {@code GLCompute} entry point,
 * {@code LocalSize} execution mode, std430 SSBO type/decoration graph (three buffers, bindings 0/1/2),
 * the {@code gl_GlobalInvocationID} built-in, and a function body that loads {@code a[i]} and
 * {@code b[i]}, applies the op, and stores into {@code c[i]}. Result {@code <id>}s are issued in order
 * and the header's id-bound is set from the high-water mark, so the binary parses cleanly (the magic is
 * the standard {@code 0x07230203}). Full GPU execution requires a driver; the binary itself is
 * spec-shaped and verifiable today (see {@link SpirvModule#verify()}).
 *
 * <p>RU: По типу элемента, арифметической операции и размеру X рабочей группы вручную собирается
 * канонический поток слов SPIR-V — capability, модель памяти, точка входа {@code GLCompute}, режим
 * исполнения {@code LocalSize}, граф типов/декораций std430-SSBO (три буфера, привязки 0/1/2),
 * встроенный {@code gl_GlobalInvocationID} и тело функции, которое читает {@code a[i]} и {@code b[i]},
 * применяет операцию и сохраняет в {@code c[i]}. Идентификаторы результата выдаются по порядку, а
 * граница id в заголовке берётся из максимума, поэтому бинарь чисто разбирается (магия — стандартная
 * {@code 0x07230203}). Полное исполнение на GPU требует драйвера; сам бинарь имеет форму спецификации
 * и проверяем уже сейчас (см. {@link SpirvModule#verify()}).
 */
final class SpirvKernelBuilder {

    /** Number of std430 storage buffers a binary op binds: a, b (inputs) + c (output). */
    private static final int BUFFER_COUNT = 3;

    private final List<Integer> capabilities = new ArrayList<>();
    private final List<Integer> extImports = new ArrayList<>();
    private final List<Integer> memoryModel = new ArrayList<>();
    private final List<Integer> entryPoints = new ArrayList<>();
    private final List<Integer> executionModes = new ArrayList<>();
    private final List<Integer> debug = new ArrayList<>();
    private final List<Integer> decorations = new ArrayList<>();
    private final List<Integer> typesConstsGlobals = new ArrayList<>();
    private final List<Integer> functions = new ArrayList<>();

    private int nextId = 1;

    private SpirvKernelBuilder() {
    }

    static SpirvModule build(ComputeElementType type, ComputeBinaryOp op, int localSizeX) {
        if (localSizeX < 1) {
            throw new IllegalArgumentException("localSizeX must be >= 1: " + localSizeX);
        }
        return new SpirvKernelBuilder().assemble(type, op, localSizeX);
    }

    /** Build a unary math kernel {@code c[i] = fn(a[i])} via a GLSL.std.450 {@code OpExtInst} (float-only). */
    static SpirvModule buildUnary(ComputeUnaryOp op, int localSizeX) {
        if (localSizeX < 1) {
            throw new IllegalArgumentException("localSizeX must be >= 1: " + localSizeX);
        }
        return new SpirvKernelBuilder().assembleUnary(op, localSizeX);
    }

    /** Two std430 storage buffers a unary op binds: a (input) + c (output). */
    private static final int UNARY_BUFFER_COUNT = 2;

    private SpirvModule assembleUnary(ComputeUnaryOp op, int localSizeX) {
        // --- result ids (issued in declaration order) ------------------------------------------
        int idMain = id();
        int idExtSet = id();          // the imported GLSL.std.450 set
        int tVoid = id();
        int tFuncVoid = id();
        int tUint = id();
        int tInt = id();
        int tFloat = id();
        int tV3uint = id();
        int tRuntimeArray = id();
        int tStruct = id();
        int tPtrUniformStruct = id();
        int tPtrUniformElem = id();
        int tPtrInputV3uint = id();
        int tPtrInputUint = id();
        int cUint0 = id();
        int cInt0 = id();
        int vBufA = id();
        int vBufC = id();
        int vGlobalId = id();

        // --- 1. capability, ext-inst import, memory model --------------------------------------
        emit(capabilities, Spirv.OP_CAPABILITY, Spirv.CAPABILITY_SHADER);
        // OpExtInstImport <id> "GLSL.std.450"
        emitString(extImports, Spirv.OP_EXT_INST_IMPORT, new int[]{idExtSet}, Spirv.EXT_GLSL_STD_450, new int[]{});
        emit(memoryModel, Spirv.OP_MEMORY_MODEL, Spirv.ADDRESSING_LOGICAL, Spirv.MEMORY_MODEL_GLSL450);

        // --- 2. entry point + execution mode ----------------------------------------------------
        emitString(entryPoints, Spirv.OP_ENTRY_POINT,
                new int[]{Spirv.EXEC_MODEL_GLCOMPUTE, idMain}, "main", new int[]{vGlobalId});
        emit(executionModes, Spirv.OP_EXECUTION_MODE,
                idMain, Spirv.EXEC_MODE_LOCAL_SIZE, localSizeX, 1, 1);

        // --- 3. debug ---------------------------------------------------------------------------
        emit(debug, Spirv.OP_SOURCE, Spirv.SOURCE_LANG_UNKNOWN, 0);

        // --- 4. decorations (two float SSBOs, bindings 0/1) -------------------------------------
        emit(decorations, Spirv.OP_DECORATE, tRuntimeArray, Spirv.DECORATION_ARRAY_STRIDE,
                ComputeElementType.FLOAT32.byteSize());
        emit(decorations, Spirv.OP_MEMBER_DECORATE, tStruct, 0, Spirv.DECORATION_OFFSET, 0);
        emit(decorations, Spirv.OP_DECORATE, tStruct, Spirv.DECORATION_BUFFER_BLOCK);
        int[] buffers = {vBufA, vBufC};
        for (int binding = 0; binding < buffers.length; binding++) {
            emit(decorations, Spirv.OP_DECORATE, buffers[binding], Spirv.DECORATION_DESCRIPTOR_SET, 0);
            emit(decorations, Spirv.OP_DECORATE, buffers[binding], Spirv.DECORATION_BINDING, binding);
        }
        emit(decorations, Spirv.OP_DECORATE, vGlobalId, Spirv.DECORATION_BUILTIN, Spirv.BUILTIN_GLOBAL_INVOCATION_ID);

        // --- 5. types, constants, globals -------------------------------------------------------
        emit(typesConstsGlobals, Spirv.OP_TYPE_VOID, tVoid);
        emit(typesConstsGlobals, Spirv.OP_TYPE_FUNCTION, tFuncVoid, tVoid);
        emit(typesConstsGlobals, Spirv.OP_TYPE_INT, tUint, 32, 0);
        emit(typesConstsGlobals, Spirv.OP_TYPE_INT, tInt, 32, 1);
        emit(typesConstsGlobals, Spirv.OP_TYPE_FLOAT, tFloat, 32);
        emit(typesConstsGlobals, Spirv.OP_TYPE_VECTOR, tV3uint, tUint, 3);
        emit(typesConstsGlobals, Spirv.OP_TYPE_RUNTIME_ARRAY, tRuntimeArray, tFloat);
        emit(typesConstsGlobals, Spirv.OP_TYPE_STRUCT, tStruct, tRuntimeArray);
        emit(typesConstsGlobals, Spirv.OP_TYPE_POINTER, tPtrUniformStruct, Spirv.STORAGE_UNIFORM, tStruct);
        emit(typesConstsGlobals, Spirv.OP_TYPE_POINTER, tPtrUniformElem, Spirv.STORAGE_UNIFORM, tFloat);
        emit(typesConstsGlobals, Spirv.OP_TYPE_POINTER, tPtrInputV3uint, Spirv.STORAGE_INPUT, tV3uint);
        emit(typesConstsGlobals, Spirv.OP_TYPE_POINTER, tPtrInputUint, Spirv.STORAGE_INPUT, tUint);
        emit(typesConstsGlobals, Spirv.OP_CONSTANT, tUint, cUint0, 0);
        emit(typesConstsGlobals, Spirv.OP_CONSTANT, tInt, cInt0, 0);
        emit(typesConstsGlobals, Spirv.OP_VARIABLE, tPtrUniformStruct, vBufA, Spirv.STORAGE_UNIFORM);
        emit(typesConstsGlobals, Spirv.OP_VARIABLE, tPtrUniformStruct, vBufC, Spirv.STORAGE_UNIFORM);
        emit(typesConstsGlobals, Spirv.OP_VARIABLE, tPtrInputV3uint, vGlobalId, Spirv.STORAGE_INPUT);

        // --- 6. function body: c[i] = ext(a[i]) -------------------------------------------------
        int idLabel = id();
        int idGidPtr = id();
        int idI = id();
        int idPtrA = id();
        int idValA = id();
        int idResult = id();
        int idPtrC = id();

        emit(functions, Spirv.OP_FUNCTION, tVoid, idMain, 0, tFuncVoid);
        emit(functions, Spirv.OP_LABEL, idLabel);
        emit(functions, Spirv.OP_ACCESS_CHAIN, tPtrInputUint, idGidPtr, vGlobalId, cUint0);
        emit(functions, Spirv.OP_LOAD, tUint, idI, idGidPtr);
        emit(functions, Spirv.OP_ACCESS_CHAIN, tPtrUniformElem, idPtrA, vBufA, cInt0, idI);
        emit(functions, Spirv.OP_LOAD, tFloat, idValA, idPtrA);
        // OpExtInst <float> <result> <set> <glsl-instruction> <operand>
        emit(functions, Spirv.OP_EXT_INST, tFloat, idResult, idExtSet, op.glslExtInstruction(), idValA);
        emit(functions, Spirv.OP_ACCESS_CHAIN, tPtrUniformElem, idPtrC, vBufC, cInt0, idI);
        emit(functions, Spirv.OP_STORE, idPtrC, idResult);
        emit(functions, Spirv.OP_RETURN);
        emit(functions, Spirv.OP_FUNCTION_END);

        return new SpirvModule(serialize(), ComputeElementType.FLOAT32, null, op, localSizeX, UNARY_BUFFER_COUNT);
    }

    private SpirvModule assemble(ComputeElementType type, ComputeBinaryOp op, int localSizeX) {
        // --- result ids (issued in declaration order) ------------------------------------------
        int idMain = id();
        int tVoid = id();
        int tFuncVoid = id();
        int tUint = id();
        int tInt = id();
        int tFloat = type.isFloat() ? id() : 0;
        int tElem = type.isFloat() ? tFloat : tInt;
        int tV3uint = id();
        int tRuntimeArray = id();
        int tStruct = id();
        int tPtrUniformStruct = id();
        int tPtrUniformElem = id();
        int tPtrInputV3uint = id();
        int tPtrInputUint = id();
        int cUint0 = id();
        int cInt0 = id();
        int vBufA = id();
        int vBufB = id();
        int vBufC = id();
        int vGlobalId = id();

        // --- 1. capability + memory model -------------------------------------------------------
        emit(capabilities, Spirv.OP_CAPABILITY, Spirv.CAPABILITY_SHADER);
        emit(memoryModel, Spirv.OP_MEMORY_MODEL, Spirv.ADDRESSING_LOGICAL, Spirv.MEMORY_MODEL_GLSL450);

        // --- 2. entry point + execution mode ----------------------------------------------------
        emitString(entryPoints, Spirv.OP_ENTRY_POINT,
                new int[]{Spirv.EXEC_MODEL_GLCOMPUTE, idMain}, "main", new int[]{vGlobalId});
        emit(executionModes, Spirv.OP_EXECUTION_MODE,
                idMain, Spirv.EXEC_MODE_LOCAL_SIZE, localSizeX, 1, 1);

        // --- 3. debug (minimal, valid) ----------------------------------------------------------
        emit(debug, Spirv.OP_SOURCE, Spirv.SOURCE_LANG_UNKNOWN, 0);

        // --- 4. decorations ---------------------------------------------------------------------
        emit(decorations, Spirv.OP_DECORATE, tRuntimeArray, Spirv.DECORATION_ARRAY_STRIDE, type.byteSize());
        emit(decorations, Spirv.OP_MEMBER_DECORATE, tStruct, 0, Spirv.DECORATION_OFFSET, 0);
        emit(decorations, Spirv.OP_DECORATE, tStruct, Spirv.DECORATION_BUFFER_BLOCK);
        int[] buffers = {vBufA, vBufB, vBufC};
        for (int binding = 0; binding < buffers.length; binding++) {
            emit(decorations, Spirv.OP_DECORATE, buffers[binding], Spirv.DECORATION_DESCRIPTOR_SET, 0);
            emit(decorations, Spirv.OP_DECORATE, buffers[binding], Spirv.DECORATION_BINDING, binding);
        }
        emit(decorations, Spirv.OP_DECORATE, vGlobalId, Spirv.DECORATION_BUILTIN, Spirv.BUILTIN_GLOBAL_INVOCATION_ID);

        // --- 5. types, constants, global variables ----------------------------------------------
        emit(typesConstsGlobals, Spirv.OP_TYPE_VOID, tVoid);
        emit(typesConstsGlobals, Spirv.OP_TYPE_FUNCTION, tFuncVoid, tVoid);
        emit(typesConstsGlobals, Spirv.OP_TYPE_INT, tUint, 32, 0);
        emit(typesConstsGlobals, Spirv.OP_TYPE_INT, tInt, 32, 1);
        if (type.isFloat()) {
            emit(typesConstsGlobals, Spirv.OP_TYPE_FLOAT, tFloat, 32);
        }
        emit(typesConstsGlobals, Spirv.OP_TYPE_VECTOR, tV3uint, tUint, 3);
        emit(typesConstsGlobals, Spirv.OP_TYPE_RUNTIME_ARRAY, tRuntimeArray, tElem);
        emit(typesConstsGlobals, Spirv.OP_TYPE_STRUCT, tStruct, tRuntimeArray);
        emit(typesConstsGlobals, Spirv.OP_TYPE_POINTER, tPtrUniformStruct, Spirv.STORAGE_UNIFORM, tStruct);
        emit(typesConstsGlobals, Spirv.OP_TYPE_POINTER, tPtrUniformElem, Spirv.STORAGE_UNIFORM, tElem);
        emit(typesConstsGlobals, Spirv.OP_TYPE_POINTER, tPtrInputV3uint, Spirv.STORAGE_INPUT, tV3uint);
        emit(typesConstsGlobals, Spirv.OP_TYPE_POINTER, tPtrInputUint, Spirv.STORAGE_INPUT, tUint);
        emit(typesConstsGlobals, Spirv.OP_CONSTANT, tUint, cUint0, 0);
        emit(typesConstsGlobals, Spirv.OP_CONSTANT, tInt, cInt0, 0);
        emit(typesConstsGlobals, Spirv.OP_VARIABLE, tPtrUniformStruct, vBufA, Spirv.STORAGE_UNIFORM);
        emit(typesConstsGlobals, Spirv.OP_VARIABLE, tPtrUniformStruct, vBufB, Spirv.STORAGE_UNIFORM);
        emit(typesConstsGlobals, Spirv.OP_VARIABLE, tPtrUniformStruct, vBufC, Spirv.STORAGE_UNIFORM);
        emit(typesConstsGlobals, Spirv.OP_VARIABLE, tPtrInputV3uint, vGlobalId, Spirv.STORAGE_INPUT);

        // --- 6. function body: c[i] = a[i] OP b[i] ----------------------------------------------
        int idLabel = id();
        int idGidPtr = id();
        int idI = id();
        int idPtrA = id();
        int idValA = id();
        int idPtrB = id();
        int idValB = id();
        int idResult = id();
        int idPtrC = id();

        emit(functions, Spirv.OP_FUNCTION, tVoid, idMain, 0, tFuncVoid);
        emit(functions, Spirv.OP_LABEL, idLabel);
        // i = gl_GlobalInvocationID.x
        emit(functions, Spirv.OP_ACCESS_CHAIN, tPtrInputUint, idGidPtr, vGlobalId, cUint0);
        emit(functions, Spirv.OP_LOAD, tUint, idI, idGidPtr);
        // a[i]
        emit(functions, Spirv.OP_ACCESS_CHAIN, tPtrUniformElem, idPtrA, vBufA, cInt0, idI);
        emit(functions, Spirv.OP_LOAD, tElem, idValA, idPtrA);
        // b[i]
        emit(functions, Spirv.OP_ACCESS_CHAIN, tPtrUniformElem, idPtrB, vBufB, cInt0, idI);
        emit(functions, Spirv.OP_LOAD, tElem, idValB, idPtrB);
        // a[i] OP b[i]
        emit(functions, op.spirvOpcode(type), tElem, idResult, idValA, idValB);
        // c[i] = result
        emit(functions, Spirv.OP_ACCESS_CHAIN, tPtrUniformElem, idPtrC, vBufC, cInt0, idI);
        emit(functions, Spirv.OP_STORE, idPtrC, idResult);
        emit(functions, Spirv.OP_RETURN);
        emit(functions, Spirv.OP_FUNCTION_END);

        return new SpirvModule(serialize(), type, op, localSizeX, BUFFER_COUNT);
    }

    private int id() {
        return nextId++;
    }

    /** Append one instruction: header word {@code (wordCount << 16) | opcode} then its operands. */
    private static void emit(List<Integer> section, int opcode, int... operands) {
        int wordCount = 1 + operands.length;
        section.add((wordCount << 16) | (opcode & 0xFFFF));
        for (int operand : operands) {
            section.add(operand);
        }
    }

    /** Append an instruction that embeds a literal string between {@code pre} and {@code post} operands. */
    private static void emitString(List<Integer> section, int opcode, int[] pre, String literal, int[] post) {
        int[] stringWords = stringToWords(literal);
        int wordCount = 1 + pre.length + stringWords.length + post.length;
        section.add((wordCount << 16) | (opcode & 0xFFFF));
        for (int p : pre) {
            section.add(p);
        }
        for (int w : stringWords) {
            section.add(w);
        }
        for (int p : post) {
            section.add(p);
        }
    }

    /** Pack a UTF-8/ASCII string into little-endian words, NUL-terminated and zero-padded. */
    private static int[] stringToWords(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        int byteLen = bytes.length + 1;            // +1 for the NUL terminator
        int wordLen = (byteLen + 3) / 4;           // round up to whole words (remaining bytes stay 0)
        int[] words = new int[wordLen];
        for (int i = 0; i < bytes.length; i++) {
            words[i / 4] |= (bytes[i] & 0xFF) << ((i % 4) * 8);
        }
        return words;
    }

    /** Concatenate the header + all logical sections into a little-endian byte array. */
    private byte[] serialize() {
        List<Integer> all = new ArrayList<>();
        // Header (5 words): magic, version, generator, id-bound, schema.
        all.add(Spirv.MAGIC);
        all.add(Spirv.VERSION_1_0);
        all.add(Spirv.GENERATOR);
        all.add(nextId);                            // id-bound: every <id> is strictly less than this
        all.add(Spirv.SCHEMA);
        all.addAll(capabilities);
        all.addAll(extImports);     // OpExtInstImport precedes OpMemoryModel in the logical layout
        all.addAll(memoryModel);
        all.addAll(entryPoints);
        all.addAll(executionModes);
        all.addAll(debug);
        all.addAll(decorations);
        all.addAll(typesConstsGlobals);
        all.addAll(functions);

        ByteBuffer buffer = ByteBuffer.allocate(all.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int word : all) {
            buffer.putInt(word);
        }
        return buffer.array();
    }
}
