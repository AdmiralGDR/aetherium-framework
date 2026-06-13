/*
 * Aetherium Framework — high-level native bridge.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.native_bridge;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * High-level, brokered access to the native bridge. The only thing the rest of the framework uses.
 *
 * <p>EN: Locates and loads {@code libaetherium_native.so}, verifies its ABI version, and exposes
 * typed, allow-listed operations — never a generic "call any symbol" surface (confidentiality,
 * {@code ARCHITECTURE.md} ). Native buffers are always Arena-owned (deterministic free). Loading
 * may fail (missing {@code .so}, wrong OS, ABI mismatch); callers — chiefly the Pre-Flight Check —
 * catch that and degrade. The location is resolved without hardcoding: the system property
 * {@code aetherium.native.lib} wins, otherwise the {@code .so} is extracted from the classpath
 * resource {@code /native/}.
 *
 * <p>RU: Находит и загружает {@code libaetherium_native.so}, проверяет версию ABI и предоставляет
 * типизированные операции из белого списка — никогда не «вызвать любой символ» (конфиденциальность).
 * Нативные буферы всегда принадлежат Arena (детерминированное освобождение). Загрузка может
 * провалиться (нет {@code .so}, не та ОС, несовпадение ABI); вызывающие — прежде всего Pre-Flight
 * Check — это перехватывают и деградируют. Расположение определяется без хардкода: системное
 * свойство {@code aetherium.native.lib} приоритетно, иначе {@code .so} извлекается из ресурса
 * classpath {@code /native/}.
 */
public final class NativeBridge implements AutoCloseable {

    private static final String LIB_RESOURCE = "/native/libaetherium_native.so";
    private static final String LIB_FILE_NAME = "libaetherium_native.so";
    private static final String LIB_PATH_PROPERTY = "aetherium.native.lib";

    private final NativeLibrary library;

    private NativeBridge(NativeLibrary library) {
        this.library = library;
    }

    /**
     * Locate, load, and ABI-check the native library.
     *
     * @throws UnsatisfiedLinkError if the library cannot be located, loaded, or linked
     * @throws IllegalStateException if the loaded library reports an incompatible ABI version
     */
    public static NativeBridge load() {
        Path soPath = locate();
        NativeLibrary library = NativeLibrary.load(soPath);
        int abi = library.abiVersion();
        if (abi != NativeLibrary.EXPECTED_ABI_VERSION) {
            library.close();
            throw new IllegalStateException(
                    "Native ABI mismatch: library reports " + abi + ", expected " + NativeLibrary.EXPECTED_ABI_VERSION);
        }
        return new NativeBridge(library);
    }

    private static Path locate() {
        String explicit = System.getProperty(LIB_PATH_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit);
            if (Files.isDirectory(path)) {
                path = path.resolve(LIB_FILE_NAME);
            }
            if (!Files.isReadable(path)) {
                throw new UnsatisfiedLinkError("Native library not readable at " + LIB_PATH_PROPERTY + "=" + explicit);
            }
            return path;
        }
        return extractFromClasspath();
    }

    private static Path extractFromClasspath() {
        try (InputStream in = NativeBridge.class.getResourceAsStream(LIB_RESOURCE)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Native library resource not found on classpath: " + LIB_RESOURCE);
            }
            Path temp = Files.createTempFile("aetherium-native-", ".so");
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            return temp;
        } catch (IOException e) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError("Failed to extract native library: " + e.getMessage());
            error.initCause(e);
            throw error;
        }
    }

    /** Native sanity self-test ({@code input * 2}). */
    public long selfTest(int input) {
        return library.selfTest(input);
    }

    /**
     * Dummy native allocation used by the Pre-Flight Check: allocate {@code byteCount} bytes in a
     * confined Arena, fill them with {@code 1}, hand the pointer to native code to sum, and free the
     * memory deterministically when the Arena closes. Returns the native-computed sum (== byteCount
     * on success). EN/RU: proves Arena-owned memory crosses the FFM boundary with no leak.
     */
    public long allocateAndSum(int byteCount) {
        if (byteCount < 0) {
            throw new IllegalArgumentException("byteCount must be >= 0: " + byteCount);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(byteCount);
            for (int i = 0; i < byteCount; i++) {
                buffer.set(ValueLayout.JAVA_BYTE, i, (byte) 1);
            }
            return library.sumBytes(buffer);
        }
    }

    /** Run the Vulkan hardware-access probe. Never throws for "unavailable" — reports it in the result. */
    public VulkanProbe probeVulkan() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(NativeLibrary.VK_INFO_LAYOUT);
            int status = library.vkProbe(info);
            if (status != 0) {
                return VulkanProbe.unavailable(status);
            }
            int available = info.get(ValueLayout.JAVA_INT, 0);
            int devices = info.get(ValueLayout.JAVA_INT, 4);
            int queueFamilies = info.get(ValueLayout.JAVA_INT, 8);
            return new VulkanProbe(available == 1, devices, queueFamilies, status);
        }
    }

    public int abiVersion() {
        return library.abiVersion();
    }

    @Override
    public void close() {
        library.close();
    }

    @Override
    public String toString() {
        return "NativeBridge[abi=" + Objects.toString(safeAbi()) + "]";
    }

    private Integer safeAbi() {
        try {
            return library.abiVersion();
        } catch (Throwable t) {
            return null;
        }
    }
}
