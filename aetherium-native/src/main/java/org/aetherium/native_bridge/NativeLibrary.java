/*
 * Aetherium Framework — FFM bindings to the native bridge.
 * Copyright (C) 2026 RedstoneTeam.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.native_bridge;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Low-level FFM bindings to {@code libaetherium_native.so}.
 *
 * <p>EN: Resolves every C symbol into a {@link MethodHandle} <strong>once</strong> at load time;
 * each later call is a constant-overhead {@code invokeExact} downcall — the {@code O(1)} invocation
 * contract. The whole library lifetime is tied to a single {@link Arena}: {@link #close()} unloads
 * the {@code .so} and frees everything deterministically (no GC finalization, no leaks). This is the
 * raw bridge; mod developers never touch it — they use the {@code ComputePipeline} interface.
 *
 * <p>RU: Разрешает каждый C-символ в {@link MethodHandle} <strong>один раз</strong> при загрузке;
 * каждый последующий вызов — downcall {@code invokeExact} с константными накладными расходами —
 * контракт {@code O(1)}. Время жизни всей библиотеки привязано к одной {@link Arena}:
 * {@link #close()} выгружает {@code .so} и детерминированно всё освобождает. Это сырой мост; мод-
 * разработчики его не касаются — они используют интерфейс {@code ComputePipeline}.
 */
public final class NativeLibrary implements AutoCloseable {

    /** Must match {@code AETH_ABI_VERSION} in the C source. */
    public static final int EXPECTED_ABI_VERSION = 1;

    /** Mirror of the C {@code AethVkInfo} struct: 4 × int32. */
    static final MemoryLayout VK_INFO_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("available"),
            ValueLayout.JAVA_INT.withName("device_count"),
            ValueLayout.JAVA_INT.withName("queue_family_count"),
            ValueLayout.JAVA_INT.withName("api_version"));

    private final Arena arena;
    private final MethodHandle abiVersion;   // () -> int
    private final MethodHandle selfTest;     // (int) -> long
    private final MethodHandle sumBytes;     // (ptr, long) -> long
    private final MethodHandle vkProbe;      // (ptr) -> int
    private final MethodHandle vkDispatch;   // (spirvPtr, spirvLen, inPtr, outPtr, n, lsx) -> int

    private NativeLibrary(Arena arena, SymbolLookup lookup) {
        this.arena = arena;
        Linker linker = Linker.nativeLinker();
        this.abiVersion = linker.downcallHandle(
                find(lookup, "aeth_native_abi_version"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
        this.selfTest = linker.downcallHandle(
                find(lookup, "aeth_self_test"),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        this.sumBytes = linker.downcallHandle(
                find(lookup, "aeth_sum_bytes"),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.vkProbe = linker.downcallHandle(
                find(lookup, "aeth_vk_probe"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        // WS-6: real GPU compute dispatch. (spirvPtr, spirvLen, inPtr, outPtr, elemCount, localSizeX)
        this.vkDispatch = linker.downcallHandle(
                find(lookup, "aeth_vk_dispatch"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    }

    private static MemorySegment find(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("Aetherium native symbol not found: " + symbol));
    }

    /**
     * Load the library at {@code soPath} into a fresh shared {@link Arena}. The Arena is shared so
     * the resulting handles are usable from any thread; it is owned by the returned instance.
     *
     * @throws UnsatisfiedLinkError if the library or a required symbol cannot be resolved
     */
    public static NativeLibrary load(Path soPath) {
        Objects.requireNonNull(soPath, "soPath");
        Arena arena = Arena.ofShared();
        try {
            SymbolLookup lookup = SymbolLookup.libraryLookup(soPath, arena);
            return new NativeLibrary(arena, lookup);
        } catch (RuntimeException | Error linkFailure) {
            arena.close();
            throw linkFailure;
        }
    }

    /** ABI version reported by the loaded library. */
    public int abiVersion() {
        try {
            return (int) abiVersion.invokeExact();
        } catch (Throwable t) {
            throw sneaky(t);
        }
    }

    /** Native sanity self-test: returns {@code input * 2}. */
    public long selfTest(int input) {
        try {
            return (long) selfTest.invokeExact(input);
        } catch (Throwable t) {
            throw sneaky(t);
        }
    }

    /**
     * Sum the bytes of an Arena-owned segment. EN: the segment must outlive the call; the caller's
     * Arena owns and frees it. RU: сегмент должен пережить вызов; им владеет и освобождает Arena
     * вызывающей стороны.
     */
    public long sumBytes(MemorySegment data) {
        Objects.requireNonNull(data, "data");
        try {
            return (long) sumBytes.invokeExact(data, data.byteSize());
        } catch (Throwable t) {
            throw sneaky(t);
        }
    }

    /**
     * Dispatch a unary SPIR-V compute kernel on a real Vulkan compute queue (WS-6).
     *
     * <p>EN: The kernel binds two std430 SSBOs (binding 0 = input, binding 1 = output, set 0) indexed by
     * {@code gl_GlobalInvocationID.x}, exactly what {@code aetherium-compute} emits. Off-heap input is
     * uploaded, the pipeline runs on the GPU, and the result is read back. Returns {@code null} on any
     * failure (no usable device, bad SPIR-V, bind error) so the caller degrades to the CPU/SIMD path — the
     * same graceful-degradation contract as the rest of the native bridge. Zero external dependency: the Zig
     * side reaches Vulkan by runtime {@code dlopen}.
     * RU: Ядро связывает два std430-SSBO (0 = вход, 1 = выход, set 0) по {@code gl_GlobalInvocationID.x} —
     * ровно то, что эмитит {@code aetherium-compute}. Вход загружается off-heap, пайплайн исполняется на GPU,
     * результат читается назад. При любой неудаче возвращает {@code null} → CPU/SIMD-путь.
     */
    public float[] vkDispatchUnary(byte[] spirv, float[] input, int localSizeX) {
        Objects.requireNonNull(spirv, "spirv");
        Objects.requireNonNull(input, "input");
        try (Arena call = Arena.ofConfined()) {
            MemorySegment spv = call.allocate(spirv.length, 4); // SPIR-V is uint32 words → 4-byte aligned
            MemorySegment.copy(spirv, 0, spv, ValueLayout.JAVA_BYTE, 0, spirv.length);
            MemorySegment in = call.allocate((long) input.length * Float.BYTES, 4);
            MemorySegment.copy(input, 0, in, ValueLayout.JAVA_FLOAT, 0, input.length);
            MemorySegment out = call.allocate((long) input.length * Float.BYTES, 4);
            int rc = (int) vkDispatch.invokeExact(spv, spv.byteSize(), in, out, input.length, localSizeX);
            if (rc != 0) {
                return null;
            }
            float[] result = new float[input.length];
            MemorySegment.copy(out, ValueLayout.JAVA_FLOAT, 0, result, 0, input.length);
            return result;
        } catch (Throwable degrade) {
            return null;
        }
    }

    /**
     * Probe Vulkan hardware access. Writes into a caller-provided {@link #VK_INFO_LAYOUT} segment
     * and returns the C status code (0 = ok, negative = unavailable/bad-args).
     */
    public int vkProbe(MemorySegment vkInfoOut) {
        Objects.requireNonNull(vkInfoOut, "vkInfoOut");
        try {
            return (int) vkProbe.invokeExact(vkInfoOut);
        } catch (Throwable t) {
            throw sneaky(t);
        }
    }

    @Override
    public void close() {
        arena.close();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneaky(Throwable t) throws T {
        throw (T) t;
    }
}
