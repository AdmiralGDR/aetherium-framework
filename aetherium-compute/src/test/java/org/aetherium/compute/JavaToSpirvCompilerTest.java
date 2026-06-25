/*
 * Aetherium Framework — Java→SPIR-V compiler tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import org.aetherium.compute.sample.ArrayAddKernel;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN: Proves a basic array-addition Java method compiles to a binary that starts with the SPIR-V magic
 * word {@code 0x07230203} and is structurally parseable.
 * RU: Доказывает, что базовый Java-метод сложения массивов компилируется в бинарь, начинающийся с
 * магического слова SPIR-V {@code 0x07230203} и структурно разбираемый.
 */
class JavaToSpirvCompilerTest {

    private final JavaToSpirvCompiler compiler = new JavaToSpirvCompiler();

    @Test
    void arrayAddCompilesToSpirvMagicBytes() {
        SpirvModule module = compiler.compile(ArrayAddKernel.class, "add");

        // The header word must be the SPIR-V magic number.
        assertEquals(0x07230203, module.magic(), "SPIR-V magic word");

        // ... and the raw little-endian bytes must literally be 03 02 23 07.
        byte[] bytes = module.toByteArray();
        assertArrayEquals(new byte[]{0x03, 0x02, 0x23, 0x07},
                new byte[]{bytes[0], bytes[1], bytes[2], bytes[3]}, "magic bytes (LE)");

        // Reading the first int little-endian round-trips to the magic value.
        assertEquals(0x07230203, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0));

        assertEquals(ComputeElementType.FLOAT32, module.elementType());
        assertEquals(ComputeBinaryOp.ADD, module.op());
        assertEquals(64, module.localSizeX());
    }

    @Test
    void compiledModuleVerifiesStructurally() {
        SpirvModule module = compiler.compile(ArrayAddKernel.class, "add");
        SpirvModule.Verification v = module.verify();
        assertTrue(v.valid(), () -> "structural verify failed: " + v.detail());
        assertTrue(v.instructionCount() > 10, "expected a non-trivial instruction stream");
        assertTrue(module.idBound() > 0, "positive id bound");
    }

    @Test
    void detectsOperationAndTypeFromBytecode() {
        SpirvModule mul = compiler.compile(ArrayAddKernel.class, "multiplyInts");
        assertEquals(ComputeBinaryOp.MUL, mul.op());
        assertEquals(ComputeElementType.INT32, mul.elementType());
        assertEquals(0x07230203, mul.magic());
    }

    @Test
    void mathSinLowersToGlslStd450ExtInst() {
        SpirvModule sine = compiler.compile(ArrayAddKernel.class, "sineWave");

        // The Math.sin call selected the SIN unary op (no binary op), float-only, 128 work-group.
        assertEquals(ComputeUnaryOp.SIN, sine.unaryOp());
        assertEquals(null, sine.op(), "a unary-math kernel has no binary op");
        assertEquals(ComputeElementType.FLOAT32, sine.elementType());
        assertEquals(128, sine.localSizeX());
        assertEquals(0x07230203, sine.magic());

        SpirvModule.Verification v = sine.verify();
        assertTrue(v.valid(), () -> "sine module verify failed: " + v.detail());

        // The binary must actually carry an OpExtInstImport (op 11) of "GLSL.std.450" AND an
        // OpExtInst (op 12) whose extended-instruction operand is GLSL Sin (13).
        assertTrue(hasOpcode(sine, 11), "expected OpExtInstImport (GLSL.std.450 import)");
        assertTrue(extInstInvokesGlsl(sine, 13), "expected an OpExtInst calling GLSL.std.450 Sin (13)");
    }

    /** Walk the SPIR-V word stream; true if any instruction has the given opcode. */
    private static boolean hasOpcode(SpirvModule module, int opcode) {
        int[] words = words(module);
        int i = 5;
        while (i < words.length) {
            int header = words[i];
            int count = (header >>> 16) & 0xFFFF;
            if ((header & 0xFFFF) == opcode) {
                return true;
            }
            if (count == 0) {
                break;
            }
            i += count;
        }
        return false;
    }

    /** True if some OpExtInst (op 12) names {@code glslInstruction} as its extended-instruction number. */
    private static boolean extInstInvokesGlsl(SpirvModule module, int glslInstruction) {
        int[] words = words(module);
        int i = 5;
        while (i < words.length) {
            int header = words[i];
            int count = (header >>> 16) & 0xFFFF;
            if (count == 0) {
                break;
            }
            // OpExtInst layout: [hdr][resultType][resultId][set][instruction][operands...]
            if ((header & 0xFFFF) == 12 && count >= 5 && words[i + 4] == glslInstruction) {
                return true;
            }
            i += count;
        }
        return false;
    }

    private static int[] words(SpirvModule module) {
        byte[] bytes = module.toByteArray();
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] words = new int[bytes.length / 4];
        for (int i = 0; i < words.length; i++) {
            words[i] = bb.getInt(i * 4);
        }
        return words;
    }

    @Test
    void rejectsMethodOutsideTheSubset() {
        // This very test method allocates objects / has no primitive-array store → unsupported.
        assertThrows(UnsupportedShaderException.class,
                () -> compiler.compile(JavaToSpirvCompilerTest.class, "rejectsMethodOutsideTheSubset"));
    }

    @Test
    void selfTestPasses() {
        SpirvSelfTest.Result r = SpirvSelfTest.run();
        assertTrue(r.passed(), () -> "self-test failed: " + r.notes());
    }
}
