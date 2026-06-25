/*
 * Aetherium Framework — compiled SPIR-V module + structural verifier.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * An emitted SPIR-V binary plus the kernel metadata it was compiled from.
 *
 * <p>EN: SPIR-V is little-endian 32-bit words. This holds the raw {@code byte[]} (ready to hand to
 * Vulkan / the native bridge) and exposes the header fields and a {@link #verify()} that walks the
 * instruction stream word-by-word — proving the binary is parseable (every instruction has a sane word
 * count and the stream ends exactly on an instruction boundary), and that it begins with the SPIR-V
 * magic {@code 0x07230203}.
 * RU: SPIR-V — little-endian 32-битные слова. Здесь хранится сырой {@code byte[]} (готовый для
 * передачи Vulkan / нативному мосту), доступны поля заголовка и {@link #verify()}, который проходит
 * поток инструкций слово за словом — доказывая, что бинарь разбираем (у каждой инструкции корректный
 * счётчик слов, поток оканчивается ровно на границе инструкции) и начинается с магии SPIR-V
 * {@code 0x07230203}.
 */
public final class SpirvModule {

    private final byte[] words;
    private final ComputeElementType elementType;
    private final ComputeBinaryOp op;
    private final ComputeUnaryOp unaryOp;
    private final int localSizeX;
    private final int bufferCount;

    SpirvModule(byte[] words, ComputeElementType elementType, ComputeBinaryOp op,
                int localSizeX, int bufferCount) {
        this(words, elementType, op, null, localSizeX, bufferCount);
    }

    SpirvModule(byte[] words, ComputeElementType elementType, ComputeBinaryOp op,
                ComputeUnaryOp unaryOp, int localSizeX, int bufferCount) {
        this.words = words;
        this.elementType = elementType;
        this.op = op;
        this.unaryOp = unaryOp;
        this.localSizeX = localSizeX;
        this.bufferCount = bufferCount;
    }

    /**
     * EN: Wrap externally-supplied SPIR-V bytes (e.g. a precompiled {@code .spv}, or fuzzed input) for
     * structural {@link #verify() verification} and {@link SpirvVulkanDispatch dispatch}. The kernel
     * metadata ({@link #elementType()} / {@link #op()}) is unknown for a wrapped binary and reads back
     * {@code null}; the header accessors are bounds-safe and never throw, even on truncated or garbage
     * input — {@link #verify()} is the authority on whether the binary is well-formed.
     * RU: Обернуть внешние байты SPIR-V (напр. предкомпилированный {@code .spv} или фаззинг-вход) для
     * структурной {@link #verify() проверки} и {@link SpirvVulkanDispatch диспетчеризации}. Метаданные
     * ядра неизвестны и читаются как {@code null}; аксессоры заголовка не бросают исключений даже на
     * усечённом/мусорном входе — авторитет о корректности у {@link #verify()}.
     */
    public static SpirvModule wrap(byte[] spirv) {
        Objects.requireNonNull(spirv, "spirv");
        return new SpirvModule(spirv.clone(), null, null, 0, 0);
    }

    /** The raw SPIR-V binary (little-endian words), ready for {@code vkCreateShaderModule}. */
    public byte[] toByteArray() {
        return words.clone();
    }

    /** Number of 32-bit words in the module (header included). */
    public int wordCount() {
        return words.length / 4;
    }

    /** The first word — must equal {@code 0x07230203}. */
    public int magic() {
        return wordAt(0);
    }

    /** The SPIR-V version word (e.g. {@code 0x00010000} for 1.0). */
    public int version() {
        return wordAt(1);
    }

    /** The id-bound from the header: every result {@code <id>} is {@code < bound}. */
    public int idBound() {
        return wordAt(3);
    }

    public ComputeElementType elementType() {
        return elementType;
    }

    public ComputeBinaryOp op() {
        return op;
    }

    /** The unary math op for a {@code dst[i] = fn(a[i])} kernel (GLSL.std.450), or {@code null}. */
    public ComputeUnaryOp unaryOp() {
        return unaryOp;
    }

    public int localSizeX() {
        return localSizeX;
    }

    /** Number of std430 storage buffers the kernel binds (2 inputs + 1 output = 3). */
    public int bufferCount() {
        return bufferCount;
    }

    /** A compact hex dump of the header words, e.g. for the CLI / report. */
    public String headerHex() {
        return String.format("magic=0x%08X version=0x%08X generator=0x%08X bound=%d schema=%d",
                wordAt(0), wordAt(1), wordAt(2), wordAt(3), wordAt(4));
    }

    private int wordAt(int wordIndex) {
        // Bounds-safe: a wrapped, unverified or truncated binary must never throw from a header
        // accessor — verify() is the sole authority on well-formedness. Out-of-range reads as 0.
        int byteIndex = wordIndex * 4;
        if (wordIndex < 0 || byteIndex < 0 || byteIndex + 4 > words.length) {
            return 0;
        }
        return ByteBuffer.wrap(words).order(ByteOrder.LITTLE_ENDIAN).getInt(byteIndex);
    }

    /**
     * EN: Structurally verify the binary: correct magic, a 5-word header, and an instruction stream
     * whose word counts tile exactly to the end with no overrun. Returns a {@link Verification} rather
     * than throwing so callers (and the self-test) can report precisely.
     * RU: Структурно проверить бинарь: корректная магия, 5-словный заголовок и поток инструкций,
     * чьи счётчики слов укладываются ровно до конца без переполнения. Возвращает {@link Verification},
     * а не бросает исключение, чтобы вызывающий код мог точно отчитаться.
     */
    public Verification verify() {
        if (words.length % 4 != 0) {
            return Verification.fail("binary length " + words.length + " is not a multiple of 4 bytes");
        }
        int total = wordCount();
        if (total < 5) {
            return Verification.fail("too short for a SPIR-V header (" + total + " words)");
        }
        if (magic() != Spirv.MAGIC) {
            return Verification.fail(String.format("bad magic 0x%08X (expected 0x07230203)", magic()));
        }
        int bound = idBound();
        if (bound <= 0) {
            return Verification.fail("non-positive id bound " + bound);
        }
        // Walk the instruction stream starting after the 5-word header.
        int i = 5;
        int instructions = 0;
        while (i < total) {
            int word = wordAt(i);
            int wordCount = (word >>> 16) & 0xFFFF;
            if (wordCount == 0) {
                return Verification.fail("zero word-count instruction at word " + i);
            }
            if (i + wordCount > total) {
                return Verification.fail("instruction at word " + i + " overruns the stream");
            }
            i += wordCount;
            instructions++;
        }
        if (i != total) {
            return Verification.fail("instruction stream did not end on a boundary (" + i + "/" + total + ")");
        }
        return Verification.ok(instructions);
    }

    /** Result of {@link #verify()}: structurally valid (and how many instructions) or why not. */
    public record Verification(boolean valid, int instructionCount, String detail) {
        static Verification ok(int instructionCount) {
            return new Verification(true, instructionCount, "parseable: " + instructionCount + " instructions");
        }

        static Verification fail(String reason) {
            return new Verification(false, 0, reason);
        }
    }
}
