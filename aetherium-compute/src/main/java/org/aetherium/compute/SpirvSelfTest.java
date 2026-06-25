/*
 * Aetherium Framework — Java→SPIR-V compiler self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import org.aetherium.compute.sample.ArrayAddKernel;
import org.aetherium.native_bridge.VulkanProbe;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end proof of the compiler: a plain-Java array-add method becomes a valid SPIR-V binary.
 *
 * <p>EN: Compiles {@link ArrayAddKernel#add} (float {@code +}) and {@link ArrayAddKernel#multiplyInts}
 * (int {@code *}), asserts the SPIR-V magic word {@code 0x07230203} and that each module verifies
 * structurally, hands a module to {@link SpirvVulkanDispatch} (CPU-fallback probe, so it runs offline),
 * and confirms that a non-kernel method is rejected. The CLI {@code spirv} command renders the result.
 * RU: Компилирует {@link ArrayAddKernel#add} (float {@code +}) и {@link ArrayAddKernel#multiplyInts}
 * (int {@code *}), проверяет магическое слово SPIR-V {@code 0x07230203} и структурную валидность
 * каждого модуля, передаёт модуль в {@link SpirvVulkanDispatch} (CPU-fallback зонд, чтобы работало
 * офлайн) и подтверждает, что не-ядро отвергается. Команда CLI {@code spirv} отображает результат.
 */
public final class SpirvSelfTest {

    private SpirvSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();
        JavaToSpirvCompiler compiler = new JavaToSpirvCompiler();

        // 1) Compile the canonical float array-addition kernel.
        SpirvModule addModule = compiler.compile(ArrayAddKernel.class, "add");
        boolean magicOk = addModule.magic() == 0x07230203;
        SpirvModule.Verification addVerify = addModule.verify();
        notes.add("compiled add(float[]) → " + addModule.headerHex());
        notes.add("structural verify: " + addVerify.detail());

        // 2) Compile an integer multiply kernel to prove op/type selection from bytecode.
        SpirvModule mulModule = compiler.compile(ArrayAddKernel.class, "multiplyInts");
        boolean mulMagicOk = mulModule.magic() == 0x07230203;
        SpirvModule.Verification mulVerify = mulModule.verify();
        notes.add("compiled multiplyInts(int[]) → op=" + mulModule.op() + " type=" + mulModule.elementType()
                + " (" + mulVerify.detail() + ")");

        // 3) Hand the binary to the native Vulkan bridge (offline CPU-fallback probe).
        SpirvVulkanDispatch dispatch = new SpirvVulkanDispatch(VulkanProbe.unavailable(0));
        SpirvVulkanDispatch.DispatchResult dr = dispatch.dispatch(addModule);
        notes.add("dispatch: " + dr.message());

        // 3b) A Math.sin kernel must lower to a GLSL.std.450 OpExtInst (the math polyfills).
        SpirvModule sineModule = compiler.compile(ArrayAddKernel.class, "sineWave");
        boolean mathSinMapped = sineModule.unaryOp() == ComputeUnaryOp.SIN
                && sineModule.verify().valid()
                && containsExtInst(sineModule, Spirv.GLSL_SIN);
        notes.add("compiled sineWave(float[]) → Math.sin mapped to GLSL.std.450 "
                + sineModule.unaryOp() + " (OpExtInst), " + sineModule.verify().detail());

        // 4) A non-kernel (no array store) must be rejected, not silently mis-compiled.
        boolean rejectedNonKernel;
        try {
            compiler.compile(SpirvSelfTest.class, "run");
            rejectedNonKernel = false;
        } catch (UnsupportedShaderException expected) {
            rejectedNonKernel = true;
            notes.add("rejected non-kernel method: " + expected.getMessage());
        }

        boolean passed = magicOk && mulMagicOk
                && addVerify.valid() && mulVerify.valid()
                && dr.uploaded() && rejectedNonKernel && mathSinMapped;

        return new Result(magicOk && mulMagicOk, addVerify.valid() && mulVerify.valid(),
                dr.uploaded(), rejectedNonKernel, mathSinMapped, addModule.magic(), addModule.wordCount(),
                addModule.headerHex(), notes, passed);
    }

    /** Walk the word stream; true if some OpExtInst names {@code glslInstruction}. */
    private static boolean containsExtInst(SpirvModule module, int glslInstruction) {
        byte[] bytes = module.toByteArray();
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int total = bytes.length / 4;
        int i = 5;
        while (i < total) {
            int header = bb.getInt(i * 4);
            int wordCount = (header >>> 16) & 0xFFFF;
            if (wordCount == 0) {
                break;
            }
            // OpExtInst (12): [hdr][resultType][resultId][set][instruction][operands...]
            if ((header & 0xFFFF) == Spirv.OP_EXT_INST && wordCount >= 5
                    && bb.getInt((i + 4) * 4) == glslInstruction) {
                return true;
            }
            i += wordCount;
        }
        return false;
    }

    /** Outcome of the SPIR-V self-test, rendered by the CLI {@code spirv} command. */
    public record Result(boolean magicOk, boolean structuralOk, boolean dispatched,
                         boolean rejectedNonKernel, boolean mathSinMapped, int magicWord, int wordCount,
                         String headerHex, List<String> notes, boolean passed) {
    }
}
