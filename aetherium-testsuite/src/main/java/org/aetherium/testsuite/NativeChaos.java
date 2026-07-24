/*
 * Aetherium Framework — native/FFM chaos tasks.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FFM chaos: simulate catastrophic native-memory misuse using only FFM-guarded operations.
 *
 * <p>EN: Real wild-pointer dereferences would SIGSEGV the JVM and are uncatchable, so we never do
 * that. Instead we trigger the failures FFM <em>does</em> guard — use-after-free (touching a closed
 * Arena's segment), out-of-bounds access, and allocation pressure ("leaks") — and assert each is
 * contained as a catchable Java exception. {@link #runOne()} returns {@code true} when the hostile
 * operation was safely contained.
 *
 * <p>RU: Настоящее разыменование диких указателей вызвало бы SIGSEGV и неперехватываемо, поэтому мы
 * так не делаем. Вместо этого мы вызываем сбои, которые FFM <em>действительно</em> охраняет —
 * use-after-free (обращение к сегменту закрытой Arena), выход за границы и давление аллокаций
 * («утечки») — и утверждаем, что каждый локализован как перехватываемое Java-исключение.
 * {@link #runOne()} возвращает {@code true}, когда враждебная операция безопасно локализована.
 */
public final class NativeChaos {

    private NativeChaos() {
    }

    enum Kind { USE_AFTER_FREE, OUT_OF_BOUNDS, ALLOC_PRESSURE }

    /** Run one random native-chaos operation. Returns true if it was safely contained. */
    public static boolean runOne() {
        Kind kind = Kind.values()[ThreadLocalRandom.current().nextInt(Kind.values().length)];
        return switch (kind) {
            case USE_AFTER_FREE -> useAfterFree();
            case OUT_OF_BOUNDS -> outOfBounds();
            case ALLOC_PRESSURE -> allocPressure();
        };
    }

    /** Touch a segment after its Arena is closed — FFM throws IllegalStateException. */
    private static boolean useAfterFree() {
        MemorySegment dangling;
        Arena arena = Arena.ofConfined();
        try {
            dangling = arena.allocate(64);
        } finally {
            arena.close(); // free the backing memory
        }
        try {
            dangling.set(ValueLayout.JAVA_BYTE, 0, (byte) 1); // use-after-free
            return false; // should not reach here
        } catch (IllegalStateException contained) {
            return true;
        }
    }

    /** Read past the end of a segment — FFM throws IndexOutOfBoundsException. */
    private static boolean outOfBounds() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(16);
            try {
                long ignored = seg.get(ValueLayout.JAVA_LONG, 1_000_000L); // way OOB
                return ignored == Long.MIN_VALUE && false; // unreachable; keep 'ignored' used
            } catch (IndexOutOfBoundsException contained) {
                return true;
            }
        }
    }

    /** Allocate a large burst of segments to stress the allocator, releasing them deterministically. */
    private static boolean allocPressure() {
        // Confined arena in try-with-resources: the burst simulates a mod hammering the allocator, and
        // close() frees every byte deterministically. (This used to use Arena.ofAuto(), whose
        // GC-dependent reclamation left the burst lingering in native memory until a collection ran —
        // exactly the "lingering direct memory" the FFM capital-debugging audit (`aetherium ffmaudit`,
        // NMT 'Other' telemetry) exists to flag. Deterministic release keeps the same no-crash
        // property with a provably zero post-run footprint.)
        long total = 0;
        try (Arena burst = Arena.ofConfined()) {
            for (int i = 0; i < 256; i++) {
                MemorySegment seg = burst.allocate(1024);
                seg.set(ValueLayout.JAVA_BYTE, 0, (byte) i);
                total += seg.byteSize();
            }
        }
        return total == 256L * 1024L;
    }
}
