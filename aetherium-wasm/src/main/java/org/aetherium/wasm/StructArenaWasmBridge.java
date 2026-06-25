/*
 * Aetherium Framework — bridges WASM linear memory to the FFM StructArena.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.wasm;

import org.aetherium.core.compute.StructArena;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Moves entity data between the off-heap {@link StructArena} and a WASM module's linear memory.
 *
 * <p>EN: A WASM mod computes over its own linear memory; the game's entities live in a contiguous
 * off-heap {@link StructArena}. This bridge owns a confined off-heap "linear memory" segment and copies
 * the arena's bytes in, runs the sandboxed {@link WasmCompute} kernel over that segment, then copies the
 * result back — all off-heap, with no GC and, crucially, <strong>without granting filesystem or network
 * access</strong> (the kernel only ever sees a byte segment, never the host). The {@link WasmCompute}
 * seam is identical whether the kernel is a real exported {@code .wasm} function (when GraalWASM is
 * installed) or a pure-Java reference kernel (offline), so the data path is exercised either way.
 *
 * <p>RU: WASM-мод вычисляет над своей линейной памятью; сущности игры живут в непрерывной off-heap
 * {@link StructArena}. Этот мост владеет ограниченным off-heap сегментом «линейной памяти» и копирует
 * байты арены внутрь, запускает изолированное ядро {@link WasmCompute} над этим сегментом, затем
 * копирует результат обратно — всё off-heap, без GC и, что важно, <strong>без предоставления доступа к
 * файловой системе или сети</strong> (ядро видит лишь байтовый сегмент, никогда не хост). Шов
 * {@link WasmCompute} одинаков, будь то реальная экспортированная функция {@code .wasm} (при наличии
 * GraalWASM) или чистое Java-ядро (офлайн).
 */
public final class StructArenaWasmBridge implements AutoCloseable {

    private final WasmSecurityPolicy policy;
    private final Arena arena = Arena.ofConfined();

    // Reused off-heap "linear memory" scratch, grown only to the high-water mark. Allocating a fresh
    // segment per runPhysics call would accumulate in this confined arena unbounded (it frees only on
    // close), so a tight per-tick / fuzz loop would exhaust native memory and crash the host. Reusing a
    // grow-on-demand buffer bounds the footprint to O(max byteCount) regardless of call count.
    private MemorySegment scratch;
    private long scratchCapacity;

    public StructArenaWasmBridge(WasmSandbox sandbox) {
        this.policy = sandbox.policy();
        // Defensive: never operate a bridge under a policy that would allow host I/O.
        this.policy.assertStrict();
    }

    /** A {@code byteCount}-length view of the reusable scratch buffer, growing it only when needed. */
    private MemorySegment linearScratch(long byteCount) {
        if (scratch == null || byteCount > scratchCapacity) {
            // Allocate at least one byte so a zero-length request still yields a valid base segment.
            scratch = arena.allocate(Math.max(byteCount, 1L));
            scratchCapacity = byteCount;
        }
        return scratch.asSlice(0L, byteCount);
    }

    /**
     * EN: Copy the first {@code byteCount} bytes of {@code entities} into linear memory, run the
     * sandboxed kernel, and copy the result back into the arena. Returns the linear memory segment.
     * RU: Скопировать первые {@code byteCount} байт {@code entities} в линейную память, запустить
     * изолированное ядро и скопировать результат обратно в арену. Возвращает сегмент линейной памяти.
     */
    public MemorySegment runPhysics(StructArena entities, long byteCount, WasmCompute kernel) {
        if (byteCount < 0 || byteCount > entities.byteSize()) {
            throw new IndexOutOfBoundsException("byteCount " + byteCount + " out of arena bounds " + entities.byteSize());
        }
        MemorySegment linear = linearScratch(byteCount);
        // arena → linear (the mod's view of the entities)
        MemorySegment.copy(entities.segment(), 0L, linear, 0L, byteCount);
        // sandboxed compute over linear memory only — no host handle is ever passed in
        kernel.compute(linear, byteCount, policy);
        // linear → arena (publish the computed result back to the game's entity store)
        MemorySegment.copy(linear, 0L, entities.segment(), 0L, byteCount);
        return linear;
    }

    public WasmSecurityPolicy policy() {
        return policy;
    }

    @Override
    public void close() {
        arena.close();
    }

    /**
     * EN: A compute kernel that may only read/write the supplied linear-memory segment.
     * RU: Вычислительное ядро, которому разрешено только читать/писать переданный сегмент линейной памяти.
     */
    @FunctionalInterface
    public interface WasmCompute {
        void compute(MemorySegment linearMemory, long byteCount, WasmSecurityPolicy policy);
    }
}
