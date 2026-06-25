/*
 * Aetherium Framework — fuzz target: the StructArena↔WASM linear-memory bridge (OOB + leak stress).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer.target;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructLayout;
import org.aetherium.fuzzer.FuzzTarget;
import org.aetherium.wasm.StructArenaWasmBridge;
import org.aetherium.wasm.WasmSandbox;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.random.RandomGenerator;

/**
 * Hammers {@link StructArenaWasmBridge#runPhysics} with out-of-bounds sizes and misbehaving kernels.
 *
 * <p>EN: This is the "out-of-bounds memory requests" target. It drives a single shared bridge with
 * randomized {@code byteCount}s — negative, zero, in-range, and past the arena — and kernels that
 * sometimes deliberately write past the segment they were handed. Every illegal access must surface as a
 * caught {@link IndexOutOfBoundsException} (FFM bounds-checks the confined segment), never a native
 * segfault. Reusing <em>one</em> bridge across thousands of cases also stresses the scratch-buffer reuse:
 * the pre-bridge allocated a fresh off-heap segment per call into a long-lived arena, so a tight
 * loop leaked native memory until the host OS was starved — the reused grow-on-demand buffer keeps the
 * footprint flat, which this target's volume proves.
 * RU: Цель «запросы памяти вне границ». Гоняет один общий мост со случайными {@code byteCount}
 * (отрицательные, ноль, в диапазоне, за пределами арены) и ядрами, иногда пишущими за выданный сегмент.
 * Любой нелегальный доступ обязан стать перехваченным {@link IndexOutOfBoundsException} (FFM проверяет
 * границы), а не нативным segfault. Переиспользование <em>одного</em> моста на тысячах случаев нагружает
 * переиспользование scratch-буфера: до фазы 16 мост аллоцировал свежий сегмент на каждый вызов в
 * долгоживущую арену и в плотном цикле истощал нативную память — переиспользуемый буфер держит
 * footprint плоским.
 */
public final class WasmBridgeFuzzTarget implements FuzzTarget, AutoCloseable {

    private static final long ENTITY_COUNT = 256;

    private final StructLayout layout = StructLayout.builder()
            .floats("x").floats("y").floats("vx").floats("vy").build();
    private final StructArena arena = StructArena.allocate(layout, ENTITY_COUNT);
    private final WasmSandbox sandbox = WasmSandbox.open();
    private final StructArenaWasmBridge bridge = new StructArenaWasmBridge(sandbox);
    private final long arenaBytes = arena.byteSize();

    @Override
    public String name() {
        return "wasm.bridge.runPhysics(OOB)";
    }

    @Override
    public void exercise(RandomGenerator rng) {
        long byteCount = switch (rng.nextInt(5)) {
            case 0 -> -rng.nextLong(1, 4096);                 // negative → bridge guard rejects
            case 1 -> 0L;                                     // empty → must be a valid no-op
            case 2 -> arenaBytes + rng.nextLong(1, 4096);     // past the arena → bridge guard rejects
            case 3 -> rng.nextLong(0, arenaBytes + 1);        // in range
            default -> alignedInRange(rng);                   // in range, 4-byte aligned (clean writes)
        };
        boolean misbehave = rng.nextInt(3) == 0;              // ~1/3 kernels try an out-of-bounds write
        long offset = rng.nextLong(0, Math.max(1, arenaBytes + 64)); // may exceed byteCount on purpose

        bridge.runPhysics(arena, byteCount, (linear, n, policy) -> kernel(linear, n, misbehave, offset));
    }

    /** A sandboxed kernel: either a clean in-bounds pass, or a deliberate out-of-bounds write. */
    private static void kernel(MemorySegment linear, long byteCount, boolean misbehave, long offset) {
        if (misbehave) {
            // Write a 4-byte int at a possibly-out-of-range offset: FFM must throw, not segfault.
            linear.set(ValueLayout.JAVA_INT_UNALIGNED, offset, 0xA5A5A5A5);
            return;
        }
        // Clean pass: integrate x += vx for each whole 16-byte entity that fits in byteCount.
        long stride = 16;
        for (long base = 0; base + stride <= byteCount; base += stride) {
            float x = linear.get(ValueLayout.JAVA_FLOAT_UNALIGNED, base);
            float vx = linear.get(ValueLayout.JAVA_FLOAT_UNALIGNED, base + 8);
            linear.set(ValueLayout.JAVA_FLOAT_UNALIGNED, base, x + vx);
        }
    }

    private long alignedInRange(RandomGenerator rng) {
        long max = arenaBytes / 4;
        return (max <= 0 ? 0 : rng.nextLong(0, max + 1)) * 4;
    }

    @Override
    public boolean expects(Throwable t) {
        // Negative / oversized byteCount and out-of-bounds kernel writes are all bounds violations.
        return t instanceof IndexOutOfBoundsException;
    }

    @Override
    public void close() {
        bridge.close();
        sandbox.close();
        arena.close();
    }
}
