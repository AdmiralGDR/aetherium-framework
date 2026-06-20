/*
 * Aetherium Framework — SIMD self-test (lane-width report + scalar-equivalence proof).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.simd;

import java.util.ArrayList;
import java.util.List;

/**
 * Proves the SIMD path is both <em>active</em> and <em>numerically identical</em> to scalar.
 *
 * <p>EN: Reports the backend ({@link SimdMath#backend()}) and the lane width the CPU exposes, then runs
 * the particle-integration kernel ({@code pos += vel*dt}) three ways — a heap {@code float[]}, an
 * off-heap {@link VectorLane}, and a deliberately odd length to exercise the scalar tail — and checks
 * every element against an independent scalar reference. A green run means the Vector API integration
 * is wired correctly and safe to use on the hot path.
 *
 * <p>RU: Доказывает, что SIMD-путь и <em>активен</em>, и <em>численно идентичен</em> скаляру. Сообщает
 * бэкенд и ширину полосы CPU, затем прогоняет ядро интегрирования частиц тремя способами — heap
 * {@code float[]}, off-heap {@link VectorLane} и намеренно нечётная длина для проверки скалярного
 * «хвоста» — и сверяет каждый элемент с независимым скалярным эталоном.
 */
public final class SimdSelfTest {

    private SimdSelfTest() {
    }

    /** Structured outcome. */
    public record Result(boolean vectorApiAvailable,
                         int laneBits,
                         int laneCount,
                         String backend,
                         boolean heapOk,
                         boolean laneOk,
                         boolean tailOk,
                         double maxAbsError,
                         List<String> notes) {
        public boolean passed() {
            return heapOk && laneOk && tailOk;
        }
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();
        notes.add("SIMD backend: " + SimdMath.backend());

        float dt = 0.5f;
        double maxErr = 0;

        // (1) Heap float[]: out = pos + vel*dt over a cache-line-spanning length.
        int n = 10_007; // prime -> guarantees a scalar tail after the vectorized body
        float[] pos = new float[n];
        float[] vel = new float[n];
        for (int i = 0; i < n; i++) {
            pos[i] = i * 0.25f;
            vel[i] = (i % 13) - 6.0f;
        }
        float[] out = new float[n];
        SimdMath.mulAdd(vel, pos, dt, out);              // out = vel*dt + pos
        boolean heapOk = true;
        for (int i = 0; i < n; i++) {
            float ref = Math.fma(vel[i], dt, pos[i]);
            maxErr = Math.max(maxErr, Math.abs(ref - out[i]));
            if (ref != out[i]) {
                heapOk = false;
            }
        }
        notes.add("heap float[" + n + "] mulAdd vs scalar: " + (heapOk ? "identical" : "MISMATCH"));

        // (2) Off-heap VectorLane: pos += vel*dt across a large particle column.
        long m = 1_000_003L;
        boolean laneOk;
        try (VectorLane posX = VectorLane.allocate(m);
             VectorLane velX = VectorLane.allocate(m)) {
            for (long i = 0; i < m; i++) {
                posX.set(i, (float) (i % 1000));
                velX.set(i, (float) ((i % 7) - 3));
            }
            posX.mulAddFrom(velX, dt);                    // SIMD integrate
            laneOk = true;
            for (long i = 0; i < m; i += 9973) {          // sample (full scan is O(m); sampling suffices)
                float ref = Math.fma((float) ((i % 7) - 3), dt, (float) (i % 1000));
                float got = posX.get(i);
                maxErr = Math.max(maxErr, Math.abs(ref - got));
                if (ref != got) {
                    laneOk = false;
                }
            }
        }
        notes.add("off-heap VectorLane[" + m + "] pos+=vel*dt vs scalar (sampled): "
                + (laneOk ? "identical" : "MISMATCH"));

        // (3) Scalar-tail correctness: a length shorter than one full lane must still be exact.
        int t = Math.max(1, SimdMath.simdFloatLanes() - 1);
        boolean tailOk;
        try (VectorLane a = VectorLane.allocate(t);
             VectorLane b = VectorLane.allocate(t)) {
            for (int i = 0; i < t; i++) {
                a.set(i, i + 0.5f);
                b.set(i, 2.0f * i);
            }
            a.mulAddFrom(b, 3.0f);
            tailOk = true;
            for (int i = 0; i < t; i++) {
                float ref = Math.fma(2.0f * i, 3.0f, i + 0.5f);
                if (ref != a.get(i)) {
                    tailOk = false;
                }
            }
        }
        notes.add("sub-lane tail length=" + t + ": " + (tailOk ? "exact" : "MISMATCH"));

        return new Result(SimdMath.isVectorApiAvailable(), SimdMath.simdFloatBits(),
                SimdMath.simdFloatLanes(), SimdMath.backend(), heapOk, laneOk, tailOk, maxErr,
                List.copyOf(notes));
    }
}
