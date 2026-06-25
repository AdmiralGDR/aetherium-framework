/*
 * Aetherium Framework — fuzz target: the Java→SPIR-V compiler front-end (ASM class parsing).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer.target;

import org.aetherium.compute.JavaToSpirvCompiler;
import org.aetherium.compute.SpirvModule;
import org.aetherium.compute.UnsupportedShaderException;
import org.aetherium.fuzzer.FuzzTarget;

import java.io.IOException;
import java.io.InputStream;
import java.util.random.RandomGenerator;

/**
 * Feeds malformed and bit-flipped {@code .class} bytes to {@link JavaToSpirvCompiler#compileBytes(byte[])}.
 *
 * <p>EN: The compiler's front-end runs ASM's {@code ClassReader} over caller-supplied bytes. Raw ASM
 * throws unchecked exceptions ({@code ArrayIndexOutOfBoundsException}, {@code IllegalArgumentException},
 * …) on a non-class blob; the documented contract is that only {@link UnsupportedShaderException} ever
 * escapes {@code compileBytes}. This target mixes pure-hostile blobs (mostly rejected before ASM even
 * parses) with <em>bit-flips of a real compiled kernel</em> — the latter sail past the class-magic check
 * and detonate deep inside ASM's structural parse, which is exactly where the rejection normalization
 * matters. A {@code null} kernel input is also fired in. (This target surfaced the pre-gap where
 * raw ASM exceptions leaked through the front-end.)
 * RU: Front-end компилятора прогоняет ASM {@code ClassReader} по байтам вызывающего. Сырой ASM бросает
 * unchecked-исключения на не-класс; контракт — наружу выходит только {@link UnsupportedShaderException}.
 * Цель смешивает чисто-враждебные блобы с <em>инверсиями битов реального ядра</em> — последние проходят
 * проверку магии класса и взрываются глубоко в разборе ASM. Также подаётся {@code null}.
 */
public final class ComputeCompilerFuzzTarget implements FuzzTarget {

    // Java class file magic 0xCAFEBABE, big-endian on the wire → bytes CA FE BA BE (LE-words form here).
    private static final int[] CLASS_MAGIC_LE = {0xBEBAFECA};
    private static final int MAX_LEN = 1024;

    private final JavaToSpirvCompiler compiler = new JavaToSpirvCompiler();
    private final byte[] realKernel = loadRealKernelBytes();

    @Override
    public String name() {
        return "compute.compileBytes(ASM)";
    }

    @Override
    public void exercise(RandomGenerator rng) {
        byte[] input;
        int mode = rng.nextInt(realKernel.length > 0 ? 3 : 2);
        input = switch (mode) {
            case 0 -> null;                                  // null contract case (every ~Nth pass)
            case 1 -> FuzzBytes.hostile(rng, CLASS_MAGIC_LE, MAX_LEN);
            default -> FuzzBytes.bitFlip(rng, realKernel);   // mutate a real kernel — stresses ASM deeply
        };
        // A successful compile is fine (rare); anything thrown must be the documented type.
        SpirvModule maybe = compiler.compileBytes(input);
        if (maybe != null) {
            maybe.verify(); // never throws; just exercise the produced module
        }
    }

    @Override
    public boolean expects(Throwable t) {
        // The only sanctioned failure mode of the front-end.
        return t instanceof UnsupportedShaderException;
    }

    /** Read the bytes of a known-good compiled kernel from the classpath (empty if unavailable). */
    private static byte[] loadRealKernelBytes() {
        String resource = "org/aetherium/compute/sample/ArrayAddKernel.class";
        try (InputStream in = ComputeCompilerFuzzTarget.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? new byte[0] : in.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
