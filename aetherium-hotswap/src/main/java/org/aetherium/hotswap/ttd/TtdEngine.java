/*
 * Aetherium Framework — the Time-Travel Debugger engine (deterministic tick journaling + rewind).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap.ttd;

import org.aetherium.core.compute.StructArena;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Drives a {@link StructArena} through journaled ticks and lets a developer step backward in time when
 * one crashes — the Durability pillar of the ACID engine.
 *
 * <p>EN: Wrap the game's per-tick simulation in {@link #tick(TickBody)}. On a clean tick the engine
 * commits a bounded memory delta to its {@link StructArenaJournal} and advances. If the tick body throws,
 * the engine <strong>does not</strong> commit; it freezes the faulted arena into a {@link TtdFault} and
 * keeps running-order intact, so the developer can {@link #rewind(int)} through the last known-good tick
 * states — reconstructed byte-exactly from the ring buffer — to see precisely which entity's value went
 * wrong before the exception. The journal's footprint is strictly bounded (see
 * {@link StructArenaJournal#maxRetainedBytes()}), so an all-day session never risks OOM.
 *
 * <p>RU: Оборачивайте поменную симуляцию в {@link #tick(TickBody)}. На чистом тике движок коммитит
 * ограниченную дельту памяти в {@link StructArenaJournal} и продвигается. Если тело тика бросает
 * исключение, движок <strong>не</strong> коммитит; он замораживает аварийную арену в {@link TtdFault},
 * позволяя {@link #rewind(int)} шагать по последним «хорошим» состояниям тиков, реконструированным из
 * кольцевого буфера байт-в-байт. Объём журнала строго ограничен, поэтому сессия на весь день не рискует
 * OOM.
 */
public final class TtdEngine {

    /** A per-tick simulation step over the shared arena. May throw to simulate a crash. */
    @FunctionalInterface
    public interface TickBody {
        void run(StructArena arena, long tick);
    }

    /** The result of one {@link #tick(TickBody)} call. */
    public enum Status { COMMITTED, FAULTED }

    /** Structured outcome of a single tick. */
    public record TickOutcome(long tick, Status status, int changedBytes, Throwable cause) {
        public boolean committed() {
            return status == Status.COMMITTED;
        }
    }

    private final StructArena arena;
    private final StructArenaJournal journal;
    private final int byteSize;
    private long tick;
    private TtdFault fault;

    public TtdEngine(StructArena arena, int journalCapacity) {
        this.arena = Objects.requireNonNull(arena, "arena");
        this.journal = new StructArenaJournal(arena, journalCapacity);
        long bytes = arena.byteSize();
        this.byteSize = (int) Math.min(bytes, Integer.MAX_VALUE);
    }

    /**
     * Run one guarded tick. On success the delta is journaled; on any {@link Throwable} the arena state
     * is frozen into a {@link TtdFault} and the exception is contained (never rethrown — the host lives).
     */
    public TickOutcome tick(TickBody body) {
        long t = tick + 1;
        try {
            body.run(arena, t);
        } catch (Throwable ex) {
            // Freeze the crash scene for post-mortem rewinding; do not commit or advance the shadow.
            this.fault = new TtdFault(t, snapshotLive(), ex);
            return new TickOutcome(t, Status.FAULTED, 0, ex);
        }
        int changed = journal.commit(t);
        tick = t;
        return new TickOutcome(t, Status.COMMITTED, changed, null);
    }

    /** Read the live arena into an immutable snapshot (used to capture a fault scene). */
    private ArenaSnapshot snapshotLive() {
        byte[] live = new byte[byteSize];
        MemorySegment.copy(arena.segment(), ValueLayout.JAVA_BYTE, 0L, live, 0, byteSize);
        return new ArenaSnapshot(arena.layout(), arena.count(), live);
    }

    /** True if the most recent tick faulted and a crash scene is available to inspect. */
    public boolean hasFault() {
        return fault != null;
    }

    /** The captured fault, or {@code null} if no tick has thrown. */
    public TtdFault fault() {
        return fault;
    }

    /** The last committed tick number. */
    public long committedTick() {
        return tick;
    }

    /** How many past states can be rewound to right now. */
    public int retainedFrames() {
        return journal.retainedFrames();
    }

    /** The journal backing this engine (footprint reporting, advanced inspection). */
    public StructArenaJournal journal() {
        return journal;
    }

    /** The latest committed state. */
    public ArenaSnapshot latest() {
        return journal.latest();
    }

    /**
     * Step {@code ticksBack} committed ticks into the past (0 = the latest committed state). Clamped to
     * the retained window.
     */
    public ArenaSnapshot rewind(int ticksBack) {
        return journal.reconstruct(ticksBack);
    }

    /** Reconstruct the state at a specific committed tick (nearest retained if evicted). */
    public ArenaSnapshot stateAtTick(long targetTick) {
        long latest = journal.latestTick();
        long back = Math.max(0, latest - targetTick);
        return journal.reconstruct((int) Math.min(back, Integer.MAX_VALUE));
    }
}
