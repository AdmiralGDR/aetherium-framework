package org.aetherium.core.compute;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Off-heap memory management via the FFM API ({@code java.lang.foreign}).
 *
 * <p>EN: Groundwork for low-level, GC-free memory the native bridge and compute pipelines need.
 * Allocations are owned by a confined {@link Arena}; {@link #close()} releases <em>all</em> of
 * them deterministically — no finalizer reliance, no per-tick leaks ({@code docs/en/native-bridge.md}
 * ). Uses preview FFM, so the whole project builds and runs with {@code --enable-preview}; that
 * the {@link #confined()} factory below compiles is itself proof the preview toolchain is wired
 * correctly.
 *
 * <p>RU: Основа для низкоуровневой памяти без GC, нужной нативному мосту и вычислительным
 * конвейерам. Аллокации принадлежат ограниченной {@link Arena}; {@link #close()} детерминированно
 * освобождает их <em>все</em> — без опоры на финализаторы, без утечек на тик. Использует preview
 * FFM, поэтому весь проект собирается и запускается с {@code --enable-preview}; компилируемость
 * фабрики {@link #confined()} сама по себе доказывает корректность настройки preview-тулчейна.
 */
public interface OffHeapAllocator extends AutoCloseable {

    /** Allocate {@code byteSize} bytes with the given alignment. */
    MemorySegment allocate(long byteSize, long byteAlignment);

    /** Allocate {@code byteSize} bytes with default 8-byte alignment. */
    default MemorySegment allocate(long byteSize) {
        return allocate(byteSize, 8L);
    }

    /** The backing arena, exposing its lifetime scope. */
    Arena arena();

    /** Release every segment owned by this allocator. */
    @Override
    void close();

    /**
     * A confined allocator backed by a fresh {@link Arena#ofConfined()} — usable from a single
     * thread, freed deterministically on {@link #close()}. This is the {@code PURE_JAVA}-tier
     * default; native tiers may override with pinned or NUMA-aware arenas later.
     */
    static OffHeapAllocator confined() {
        final Arena arena = Arena.ofConfined();
        return new OffHeapAllocator() {
            @Override
            public MemorySegment allocate(long byteSize, long byteAlignment) {
                return arena.allocate(byteSize, byteAlignment);
            }

            @Override
            public Arena arena() {
                return arena;
            }

            @Override
            public void close() {
                arena.close();
            }
        };
    }
}
