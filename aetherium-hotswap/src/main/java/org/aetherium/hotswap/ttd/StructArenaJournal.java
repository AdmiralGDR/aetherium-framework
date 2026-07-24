/*
 * Aetherium Framework — bounded ring-buffer memory-delta journal for a StructArena (Durability).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap.ttd;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructLayout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A deterministic, <strong>strictly bounded</strong> ring buffer of per-tick memory deltas over a
 * {@link StructArena} — the recording engine behind the Time-Travel Debugger.
 *
 * <p>EN: On each {@link #commit(long)} the journal diffs the live arena against a single shadow mirror,
 * coalesces the changed bytes into runs, and stores the <em>before</em> and <em>after</em> images of
 * only those runs as one {@link Frame}. Frames live in a fixed-capacity ring: the {@code (N+1)}-th
 * commit overwrites the oldest frame, so the footprint can never grow without bound no matter how many
 * ticks run (the Durability rule). Total memory is capped at
 * {@code shadow (byteSize) + capacity × 2 × byteSize} — one mirror plus, worst case, a full before+after
 * image per retained frame; {@link #maxRetainedBytes()} reports that ceiling. Because only deltas are
 * stored, a typical tick that nudges a handful of entities costs a few dozen bytes, not a full snapshot.
 *
 * <p>To rewind, {@link #reconstruct(int)} starts from the latest shadow and applies the before-images of
 * the newest frames in reverse — classic undo-log replay — yielding a byte-exact past state.
 *
 * <p>RU: Детерминированный, <strong>строго ограниченный</strong> кольцевой буфер поменных дельт памяти
 * над {@link StructArena}. На каждом {@link #commit(long)} журнал сравнивает живую арену с единственной
 * теневой копией, объединяет изменённые байты в пробеги и хранит их <em>before</em>/<em>after</em>
 * образы как один {@link Frame}. Кадры живут в кольце фиксированной ёмкости: {@code (N+1)}-й коммит
 * перезаписывает старейший кадр, поэтому объём памяти не растёт неограниченно, сколько бы тиков ни шло.
 * Потолок: {@code shadow + capacity × 2 × byteSize}. Перемотка ({@link #reconstruct(int)}) идёт от
 * последней тени, применяя before-образы новейших кадров в обратном порядке (классический undo-log).
 */
public final class StructArenaJournal {

    /** One recorded tick: the coalesced runs of bytes that changed, with their before/after images. */
    private static final class Frame {
        long tick;
        int[] offsets;
        byte[][] before;
        byte[][] after;
        int changedBytes;
    }

    private final StructArena arena;
    private final StructLayout layout;
    private final long count;
    private final int byteSize;
    private final int capacity;

    private final byte[] shadow;       // the latest committed state (single mirror)
    private final byte[] scratch;      // reused live-read buffer — never reallocated (bounded footprint)
    private final Frame[] ring;
    private long committedCount;       // total commits ever (monotonic)

    /**
     * Journal {@code arena} keeping at most {@code capacity} recent frames.
     *
     * @throws IllegalArgumentException if capacity &lt; 1 or the arena exceeds {@link Integer#MAX_VALUE} bytes
     */
    public StructArenaJournal(StructArena arena, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1: " + capacity);
        }
        long bytes = arena.byteSize();
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("arena too large to journal: " + bytes + " bytes");
        }
        this.arena = arena;
        this.layout = arena.layout();
        this.count = arena.count();
        this.byteSize = (int) bytes;
        this.capacity = capacity;
        this.shadow = new byte[byteSize];
        this.scratch = new byte[byteSize];
        this.ring = new Frame[capacity];
        // Baseline: the shadow starts equal to the arena's construction-time state, so the first
        // commit records only what actually changed since journaling began.
        readLive(this.shadow);
    }

    /** Copy the live arena's bytes into {@code dst} (no allocation). */
    private void readLive(byte[] dst) {
        MemorySegment.copy(arena.segment(), ValueLayout.JAVA_BYTE, 0L, dst, 0, byteSize);
    }

    /**
     * Record the delta between the live arena and the last committed state as tick {@code tick},
     * then advance the shadow. Returns the number of changed bytes captured.
     */
    public int commit(long tick) {
        readLive(scratch);

        // Coalesce contiguous differing bytes into runs.
        int runs = 0;
        int totalChanged = 0;
        int[] starts = null;
        int[] ends = null;
        int i = 0;
        // First pass: count runs so we can size arrays exactly (bounded work, single arena scan each).
        while (i < byteSize) {
            if (scratch[i] != shadow[i]) {
                int start = i;
                while (i < byteSize && scratch[i] != shadow[i]) {
                    i++;
                }
                runs++;
                totalChanged += i - start;
            } else {
                i++;
            }
        }

        Frame frame = new Frame();
        frame.tick = tick;
        frame.changedBytes = totalChanged;
        frame.offsets = new int[runs];
        frame.before = new byte[runs][];
        frame.after = new byte[runs][];

        // Second pass: capture the before/after image of each run and update the shadow.
        int r = 0;
        i = 0;
        while (i < byteSize) {
            if (scratch[i] != shadow[i]) {
                int start = i;
                while (i < byteSize && scratch[i] != shadow[i]) {
                    i++;
                }
                int len = i - start;
                byte[] beforeRun = new byte[len];
                byte[] afterRun = new byte[len];
                System.arraycopy(shadow, start, beforeRun, 0, len);
                System.arraycopy(scratch, start, afterRun, 0, len);
                System.arraycopy(scratch, start, shadow, start, len); // advance shadow
                frame.offsets[r] = start;
                frame.before[r] = beforeRun;
                frame.after[r] = afterRun;
                r++;
            } else {
                i++;
            }
        }

        ring[(int) (committedCount % capacity)] = frame;
        committedCount++;
        return totalChanged;
    }

    /** Total commits ever recorded (monotonically increasing across ring eviction). */
    public long committedCount() {
        return committedCount;
    }

    /** How many frames are actually retained right now (min of commits and capacity). */
    public int retainedFrames() {
        return (int) Math.min(committedCount, capacity);
    }

    public int capacity() {
        return capacity;
    }

    /** The oldest tick still reconstructable, or 0 if nothing recorded. */
    public long oldestRetainedTick() {
        if (committedCount == 0) {
            return 0;
        }
        return frameAt(retainedFrames() - 1).tick;
    }

    /** The newest committed tick, or 0 if nothing recorded. */
    public long latestTick() {
        return committedCount == 0 ? 0 : frameAt(0).tick;
    }

    /** Frame {@code back} steps from the newest (0 = newest retained frame). */
    private Frame frameAt(int back) {
        // newest is at (committedCount-1) % capacity; walk backwards.
        int idx = (int) (((committedCount - 1 - back) % capacity + capacity) % capacity);
        return ring[idx];
    }

    /**
     * Reconstruct the arena state {@code framesBack} committed ticks before the latest (0 = latest).
     * Values beyond the retained window are clamped to the oldest retained state.
     */
    public ArenaSnapshot reconstruct(int framesBack) {
        if (framesBack < 0) {
            throw new IllegalArgumentException("framesBack must be >= 0: " + framesBack);
        }
        int steps = Math.min(framesBack, retainedFrames());
        byte[] state = shadow.clone();
        // Undo the newest `steps` frames by restoring their before-images.
        for (int back = 0; back < steps; back++) {
            Frame f = frameAt(back);
            for (int k = 0; k < f.offsets.length; k++) {
                System.arraycopy(f.before[k], 0, state, f.offsets[k], f.before[k].length);
            }
        }
        return new ArenaSnapshot(layout, count, state);
    }

    /** The changed-byte count recorded for the frame {@code back} steps from newest. */
    public int changedBytesAt(int back) {
        return frameAt(back).changedBytes;
    }

    /** A snapshot of the latest committed state. */
    public ArenaSnapshot latest() {
        return new ArenaSnapshot(layout, count, shadow.clone());
    }

    /** Current heap bytes held by the journal (shadow + scratch + all retained frame images). */
    public long estimatedRetainedBytes() {
        long total = (long) shadow.length + scratch.length;
        for (Frame f : ring) {
            if (f == null) {
                continue;
            }
            for (int k = 0; k < f.offsets.length; k++) {
                total += f.before[k].length + f.after[k].length + 4; // + offset int
            }
        }
        return total;
    }

    /** The hard ceiling on the journal's footprint, independent of tick count (Durability rule). */
    public long maxRetainedBytes() {
        // shadow + scratch + worst case a full before+after image per retained frame.
        return (long) byteSize * 2 + (long) capacity * 2 * byteSize;
    }
}
