/*
 * Aetherium Framework — fuzzing campaign report.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer;

import java.util.List;

/**
 * The aggregate outcome of a fuzzing campaign: per-target counts plus any crash findings.
 *
 * <p>EN: A campaign {@link #passed() passes} only if no target produced an unexpected throwable. Each
 * {@link Finding} carries the exact seed and iteration that triggered it, so a crash is always
 * replayable. {@link TargetStat#rejected} counts <em>clean</em> contractual rejections — high numbers
 * there are good: they prove the fuzzer is actually reaching the reject paths, not just feeding inputs
 * the code happily ignores.
 * RU: Кампания {@link #passed() проходит}, только если ни одна цель не выдала неожиданного throwable.
 * Каждая {@link Finding} несёт точный сид и итерацию, поэтому краш всегда воспроизводим.
 * {@link TargetStat#rejected} считает <em>чистые</em> контрактные отказы — большие значения здесь
 * хороши: они доказывают, что фаззер реально достигает путей отказа.
 *
 * @param seed     the campaign seed (every per-case seed derives from this)
 * @param targets  per-target statistics
 * @param findings unexpected throwables (crashes); empty on a passing campaign
 */
public record FuzzReport(long seed, List<TargetStat> targets, List<Finding> findings) {

    public FuzzReport {
        targets = List.copyOf(targets);
        findings = List.copyOf(findings);
    }

    /** A campaign passes iff nothing crashed. */
    public boolean passed() {
        return findings.isEmpty();
    }

    /** Total cases executed across all targets. */
    public long totalCases() {
        return targets.stream().mapToLong(TargetStat::iterations).sum();
    }

    /** Total clean contractual rejections across all targets (proof the reject paths were reached). */
    public long totalRejected() {
        return targets.stream().mapToLong(TargetStat::rejected).sum();
    }

    /** Per-target counts. {@code handled + rejected + crashed == iterations}. */
    public record TargetStat(String name, long iterations, long handled, long rejected, long crashed) {
    }

    /** A single crash: the reproducible seed/iteration and the unexpected throwable it produced. */
    public record Finding(String target, long caseSeed, int iteration, String throwable, String message) {
        @Override
        public String toString() {
            return target + " #" + iteration + " (seed=" + caseSeed + "): " + throwable + ": " + message;
        }
    }
}
