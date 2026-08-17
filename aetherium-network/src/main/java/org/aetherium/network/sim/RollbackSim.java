/*
 * Aetherium Framework — rollback simulation (restore + re-simulate on a corrected input).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network.sim;

import org.aetherium.core.compute.StructArena;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

/**
 * A deterministic simulation that can <strong>roll back</strong>: when a late remote input corrects a tick the
 * client had predicted, it restores an earlier state and re-simulates forward — GGPO-style netcode.
 *
 * <p>EN: Online, a client can't wait for every peer's input, so it <em>predicts</em> and advances. When the
 * real input for a past tick arrives and differs, the predicted future is wrong and must be redone. This
 * engine keeps a baseline snapshot of the state plus the input history, so {@link #correct} rewinds to the
 * baseline, substitutes the corrected input, and replays every tick — yielding exactly the state the client
 * would have had with the right input all along (proven by the tests: rollback == no-rollback). Because the
 * sim is deterministic ({@link LockstepSim}), the replay is bit-exact. Zero-dependency. (A production engine
 * also advances the baseline as inputs are confirmed, bounding replay to the unconfirmed window; the
 * correctness guarantee is the same.)
 * RU: В сети клиент не может ждать ввод каждого узла, поэтому <em>предсказывает</em> и продвигается. Когда
 * приходит реальный ввод для прошлого тика и он другой — предсказанное будущее неверно и его надо переиграть.
 * Движок хранит базовый снимок состояния и историю вводов, поэтому {@link #correct} отматывает к базе,
 * подставляет исправленный ввод и переигрывает все тики — получая ровно то состояние, что было бы с верным
 * вводом с самого начала (доказано тестами: rollback == no-rollback). Симуляция детерминирована, поэтому
 * переигрывание побайтово точно. Без зависимостей.
 */
public final class RollbackSim {

    private final StructArena state;
    private final LockstepSim.Step step;
    private final byte[] baseline;
    private long[] history = new long[16];
    private int ticks;

    /** Wrap {@code state} (its current bytes become the rollback baseline) and drive it with {@code step}. */
    public RollbackSim(StructArena state, LockstepSim.Step step) {
        this.state = state;
        this.step = step;
        this.baseline = snapshot(state);
    }

    /** Advance one tick with {@code input}, recording it so a later {@link #correct} can replay it. */
    public void advance(long input) {
        if (ticks == history.length) {
            history = Arrays.copyOf(history, history.length * 2);
        }
        history[ticks] = input;
        step.apply(state, ticks, input);
        ticks++;
    }

    /**
     * Replace the input at {@code tick} with {@code correctedInput} and re-simulate from the baseline to the
     * present — the rollback. Returns the corrected present-state checksum.
     *
     * @throws IndexOutOfBoundsException if {@code tick} is not an already-advanced tick
     */
    public long correct(int tick, long correctedInput) {
        if (tick < 0 || tick >= ticks) {
            throw new IndexOutOfBoundsException("tick " + tick + " is not in [0, " + ticks + ")");
        }
        history[tick] = correctedInput;
        restore(state, baseline);
        for (int t = 0; t < ticks; t++) {
            step.apply(state, t, history[t]);
        }
        return StateChecksum.of(state);
    }

    /** The checksum of the current (present) state. */
    public long checksum() {
        return StateChecksum.of(state);
    }

    /** Number of ticks advanced so far. */
    public int ticks() {
        return ticks;
    }

    private static byte[] snapshot(StructArena arena) {
        int size = Math.toIntExact(arena.byteSize());
        byte[] buffer = new byte[size];
        MemorySegment.copy(arena.segment(), ValueLayout.JAVA_BYTE, 0L, buffer, 0, size);
        return buffer;
    }

    private static void restore(StructArena arena, byte[] buffer) {
        MemorySegment.copy(buffer, 0, arena.segment(), ValueLayout.JAVA_BYTE, 0L, buffer.length);
    }
}
