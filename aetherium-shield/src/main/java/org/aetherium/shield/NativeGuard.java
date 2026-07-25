/*
 * Aetherium Framework — FFM binding to the Zig anti-tamper guard (with pure-Java degradation).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The runtime half of the sovereign guard: a thin FFM binding to the zero-dependency Zig {@code .so}, with a
 * pure-Java fallback so the framework's FFM→pure-Java→disable ladder holds.
 *
 * <p>EN: If {@code native/libaetherium_guard.so} is bundled and loads, {@link #checksum} and
 * {@link #tracerPid} run natively (a fast FNV-1a and a real {@code /proc/self/status} read). If it is absent
 * or FFM is unavailable, the same operations run in Java — {@link #checksum} uses the identical FNV-1a, and
 * {@link #tracerPid} degrades to detecting an attached {@code -javaagent}/{@code -agentlib} via the runtime
 * MX bean. Either way the API behaves; {@link #isNative()} tells callers which path is live. Resolved once —
 * the downcall handles are cached, so per-call cost is O(1).
 * RU: Рантайм-часть суверенного гарда: тонкая FFM-привязка к без-зависимостному .so Zig, с деградацией до
 * чистого Java. Есть .so — {@link #checksum}/{@link #tracerPid} нативны; нет — те же операции на Java
 * (тот же FNV-1a; детект {@code -javaagent}/{@code -agentlib} через MX bean).
 */
public final class NativeGuard {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final String RESOURCE = "native/libaetherium_guard.so";

    private static final NativeGuard INSTANCE = create();

    private final boolean nativeLoaded;
    private final int abi;
    private final MethodHandle fnv1a;   // (MemorySegment ptr, long len) -> long
    private final MethodHandle tracer;  // () -> int
    private final MethodHandle xor16;   // (MemorySegment ptr, long count, int key) -> void
    @SuppressWarnings("unused")
    private final Arena arena;          // keeps the library mapping alive for the process lifetime

    private NativeGuard(boolean nativeLoaded, int abi, MethodHandle fnv1a, MethodHandle tracer,
                        MethodHandle xor16, Arena arena) {
        this.nativeLoaded = nativeLoaded;
        this.abi = abi;
        this.fnv1a = fnv1a;
        this.tracer = tracer;
        this.xor16 = xor16;
        this.arena = arena;
    }

    /** The process-wide guard. */
    public static NativeGuard get() {
        return INSTANCE;
    }

    /** True if the native library is loaded (false = pure-Java fallback). */
    public boolean isNative() {
        return nativeLoaded;
    }

    /** The native ABI version (0 in the fallback). */
    public int abiVersion() {
        return abi;
    }

    /** FNV-1a 64-bit checksum of {@code data} — native when available, otherwise the identical Java routine. */
    public long checksum(byte[] data) {
        if (nativeLoaded) {
            try (Arena call = Arena.ofConfined()) {
                MemorySegment seg = call.allocate(Math.max(1, data.length));
                MemorySegment.copy(data, 0, seg, ValueLayout.JAVA_BYTE, 0, data.length);
                return (long) fnv1a.invoke(seg, (long) data.length);
            } catch (Throwable t) {
                // fall through to Java on any FFM hiccup
            }
        }
        return fnv1aJava(data);
    }

    /**
     * The debugger/attach TracerPid: {@code >0} means a debugger is attached, {@code 0} means none, {@code -1}
     * unavailable. In the Java fallback, returns {@code 1} if a {@code -javaagent}/{@code -agentlib} is present
     * (a comparable "someone is instrumenting this JVM" signal), else {@code 0}.
     */
    public int tracerPid() {
        if (nativeLoaded) {
            try {
                return (int) tracer.invoke();
            } catch (Throwable t) {
                // fall through
            }
        }
        return agentAttached() ? 1 : 0;
    }

    /** True if a debugger/agent appears to be observing this JVM (native TracerPid, or a Java-agent heuristic). */
    public boolean instrumentationDetected() {
        return tracerPid() > 0;
    }

    /**
     * Decode a shielded string literal: XOR each char with {@code (key + i*7) & 0xFFFF}. Runs natively when
     * the guard is loaded (the decode routine is NOT in the calling class's bytecode), otherwise the identical
     * pure-Java routine. Symmetric with {@code StringEncryptionTransformer.encode}.
     */
    public String xorDecodeString(String cipher, int key) {
        char[] chars = cipher.toCharArray();
        if (nativeLoaded && chars.length > 0) {
            try (Arena call = Arena.ofConfined()) {
                MemorySegment seg = call.allocate((long) chars.length * Character.BYTES);
                MemorySegment.copy(chars, 0, seg, ValueLayout.JAVA_CHAR, 0, chars.length);
                xor16.invoke(seg, (long) chars.length, key);
                MemorySegment.copy(seg, ValueLayout.JAVA_CHAR, 0, chars, 0, chars.length);
                return new String(chars);
            } catch (Throwable t) {
                // fall through to Java
            }
        }
        return xorDecodeJava(cipher, key);
    }

    /** Pure-Java XOR decode — byte-identical to the native routine (the degradation path). */
    public static String xorDecodeJava(String cipher, int key) {
        char[] a = cipher.toCharArray();
        for (int j = 0; j < a.length; j++) {
            a[j] = (char) (a[j] ^ ((key + j * 7) & 0xFFFF));
        }
        return new String(a);
    }

    /** Pure-Java FNV-1a — byte-identical to the Zig implementation so results are comparable across paths. */
    public static long fnv1aJava(byte[] data) {
        long h = FNV_OFFSET;
        for (byte b : data) {
            h ^= (b & 0xffL);
            h *= FNV_PRIME;
        }
        return h;
    }

    private static boolean agentAttached() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String a : args) {
                if (a.startsWith("-javaagent") || a.startsWith("-agentlib") || a.startsWith("-agentpath")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // MX bean unavailable — treat as clean
        }
        return false;
    }

    private static NativeGuard create() {
        try {
            Path lib = extract();
            if (lib == null) {
                return fallback();
            }
            Arena arena = Arena.ofShared();
            SymbolLookup lookup = SymbolLookup.libraryLookup(lib.toString(), arena);
            Linker linker = Linker.nativeLinker();
            MethodHandle abiH = linker.downcallHandle(lookup.find("aeth_guard_abi").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));
            MethodHandle fnvH = linker.downcallHandle(lookup.find("aeth_guard_fnv1a").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            MethodHandle tracerH = linker.downcallHandle(lookup.find("aeth_guard_tracer_pid").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));
            MethodHandle xorH = linker.downcallHandle(lookup.find("aeth_guard_xor16").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
            int abi = (int) abiH.invoke();
            return new NativeGuard(true, abi, fnvH, tracerH, xorH, arena);
        } catch (Throwable degrade) {
            return fallback();
        }
    }

    private static NativeGuard fallback() {
        return new NativeGuard(false, 0, null, null, null, null);
    }

    /** Extract the bundled {@code .so} to a temp file; returns null if it is not on the classpath. */
    private static Path extract() {
        try (InputStream in = NativeGuard.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return null;
            }
            Path tmp = Files.createTempFile("aetherium_guard", ".so");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (Throwable t) {
            return null;
        }
    }
}
