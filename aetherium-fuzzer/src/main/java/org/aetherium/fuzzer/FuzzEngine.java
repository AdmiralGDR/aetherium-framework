/*
 * Aetherium Framework — the deterministic fuzzing campaign runner.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Runs each {@link FuzzTarget} for a fixed number of reproducibly-seeded cases and classifies outcomes.
 *
 * <p>EN: For every case the engine derives a per-case seed from {@code (campaignSeed, targetName,
 * iteration)} so a finding can be replayed exactly. It catches <strong>every</strong> {@link Throwable}
 * — including {@link Error} — because the whole point is to prove that no input escapes as a VM-level
 * failure. A caught throwable that the target {@link FuzzTarget#expects expects} is a clean rejection;
 * anything else becomes a {@link FuzzReport.Finding}. (FFM bounds checks turn out-of-bounds memory
 * requests into {@code IndexOutOfBoundsException} rather than a native segfault, so "the JVM survived"
 * is exactly equivalent to "the engine caught it".)
 * RU: Для каждого случая движок выводит сид из {@code (сид кампании, имя цели, итерация)}, чтобы находку
 * можно было воспроизвести. Перехватывает <strong>каждый</strong> {@link Throwable}, включая
 * {@link Error}, ведь цель — доказать, что ни один вход не уходит в сбой уровня VM. Перехваченный
 * throwable, который цель {@link FuzzTarget#expects ожидает}, — чистый отказ; всё прочее становится
 * {@link FuzzReport.Finding}.
 */
public final class FuzzEngine {

    /** Cap on stored findings so a pathological run can't exhaust memory building the report. */
    private static final int MAX_FINDINGS = 64;

    private FuzzEngine() {
    }

    /** Run a campaign: {@code iterationsPerTarget} cases against each target, seeded from {@code seed}. */
    public static FuzzReport run(List<FuzzTarget> targets, int iterationsPerTarget, long seed) {
        List<FuzzReport.TargetStat> stats = new ArrayList<>(targets.size());
        List<FuzzReport.Finding> findings = new ArrayList<>();

        for (FuzzTarget target : targets) {
            long handled = 0;
            long rejected = 0;
            long crashed = 0;
            for (int i = 0; i < iterationsPerTarget; i++) {
                long caseSeed = caseSeed(seed, target.name(), i);
                try {
                    target.exercise(new SplittableRandom(caseSeed));
                    handled++;
                } catch (Throwable t) {
                    if (target.expects(t)) {
                        rejected++;
                    } else {
                        crashed++;
                        if (findings.size() < MAX_FINDINGS) {
                            findings.add(new FuzzReport.Finding(
                                    target.name(), caseSeed, i, t.getClass().getName(),
                                    String.valueOf(t.getMessage())));
                        }
                    }
                }
            }
            stats.add(new FuzzReport.TargetStat(target.name(), iterationsPerTarget, handled, rejected, crashed));
        }
        return new FuzzReport(seed, stats, findings);
    }

    /** Mixes the campaign seed, the target name, and the iteration into a reproducible per-case seed. */
    private static long caseSeed(long seed, String targetName, int iteration) {
        long h = seed * 0x9E3779B97F4A7C15L + targetName.hashCode();
        h ^= (h >>> 29);
        h += ((long) iteration << 1) + 0x632BE59BD9B4E019L;
        h ^= (h >>> 32);
        return h;
    }
}
