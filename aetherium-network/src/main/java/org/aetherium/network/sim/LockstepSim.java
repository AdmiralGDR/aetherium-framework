/*
 * Aetherium Framework — deterministic fixed-step simulation runner + desync detection.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network.sim;

import org.aetherium.core.compute.StructArena;

import java.util.OptionalLong;

/**
 * Runs a deterministic fixed-step simulation over a {@link StructArena} and detects desync by comparing
 * per-tick {@link StateChecksum}s — the foundation for lockstep and rollback netcode.
 *
 * <p>EN: The runner is deterministic by construction: it advances a fixed number of ticks in order, with no
 * wall-clock and no hash-ordered iteration, feeding each tick its input. The determinism then rests entirely
 * on the caller's {@link Step} (arithmetic over {@code state}/{@code tick}/{@code input} only — no
 * {@code System.nanoTime()}, no unseeded RNG, no {@code HashMap} iteration). {@link #firstDivergence} makes a
 * desync <em>loud</em>: it returns the exact tick two peers' checksum streams first differ, so the caller
 * resyncs instead of drifting silently. Zero-dependency.
 * RU: Раннер детерминирован по построению: продвигает фиксированное число тиков по порядку, без стенных часов и
 * без итерации по хешу, передавая каждому тику его вход. Детерминизм далее целиком на {@link Step}
 * вызывающего (только арифметика над {@code state}/{@code tick}/{@code input} — без {@code System.nanoTime()},
 * без незасеянного RNG, без итерации {@code HashMap}). {@link #firstDivergence} делает рассинхрон
 * <em>громким</em>: возвращает точный тик, где потоки контрольных сумм разошлись. Без зависимостей.
 */
public final class LockstepSim {

    /** One deterministic simulation step: mutate {@code state} from {@code tick} and {@code input} only. */
    @FunctionalInterface
    public interface Step {
        void apply(StructArena state, long tick, long input);
    }

    private LockstepSim() {
    }

    /**
     * Advance {@code state} through {@code inputs.length} fixed steps, returning the {@link StateChecksum} of
     * the whole state after each tick (so index {@code t} is the checksum after tick {@code t}).
     */
    public static long[] run(StructArena state, Step step, long[] inputs) {
        long[] checksums = new long[inputs.length];
        for (int tick = 0; tick < inputs.length; tick++) {
            step.apply(state, tick, inputs[tick]);
            checksums[tick] = StateChecksum.of(state);
        }
        return checksums;
    }

    /**
     * The first tick at which two peers' checksum streams differ — i.e. where they desynced — or
     * {@link OptionalLong#empty()} if they match over every compared tick. Unequal-length streams diverge at
     * the shorter length (the first tick one peer is missing). Never silent: a desync is a returned value the
     * caller must act on.
     */
    public static OptionalLong firstDivergence(long[] local, long[] remote) {
        int common = Math.min(local.length, remote.length);
        for (int tick = 0; tick < common; tick++) {
            if (local[tick] != remote[tick]) {
                return OptionalLong.of(tick);
            }
        }
        if (local.length != remote.length) {
            return OptionalLong.of(common);
        }
        return OptionalLong.empty();
    }

    /** Whether two peers' checksum streams are fully in sync. */
    public static boolean inSync(long[] local, long[] remote) {
        return firstDivergence(local, remote).isEmpty();
    }
}
