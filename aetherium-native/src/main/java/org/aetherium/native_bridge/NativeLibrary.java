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
