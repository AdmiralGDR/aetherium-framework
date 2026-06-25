/*
 * Aetherium Framework — assembles and runs the standard fuzzing campaign.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer;

import org.aetherium.fuzzer.target.ComputeCompilerFuzzTarget;
import org.aetherium.fuzzer.target.SpirvFuzzTarget;
import org.aetherium.fuzzer.target.WasmBridgeFuzzTarget;
import org.aetherium.fuzzer.target.WasmLoaderFuzzTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * The standard Aetherium fuzzing campaign over the SPIR-V and WASM attack surface.
 *
 * <p>EN: Wires up the four targets, runs {@code iterationsPerTarget} reproducibly-seeded cases against
 * each, and returns a {@link Result} the CLI {@code fuzz} command renders and the {@code check} test
 * asserts on. The lone stateful target (the WASM bridge owns off-heap memory) is closed deterministically
 * after the run. The default iteration count is aggressive enough to exercise every adversarial shape
 * many times over while staying fast enough to run on every build.
 * RU: Связывает четыре цели, прогоняет {@code iterationsPerTarget} воспроизводимо засеянных случаев по
 * каждой и возвращает {@link Result}, который рендерит CLI {@code fuzz} и проверяет тест {@code check}.
 * Единственная stateful-цель (мост WASM владеет off-heap памятью) детерминированно закрывается после
 * прогона. Число итераций по умолчанию агрессивно, но достаточно быстро для каждой сборки.
 */
public final class FuzzerSelfTest {

    /** Cases per target on a standard campaign (4 targets → 40k cases total). */
    public static final int DEFAULT_ITERATIONS = 10_000;

    private FuzzerSelfTest() {
    }

    /** Run the standard campaign with the default seed and iteration count. */
    public static Result run() {
        return run(DEFAULT_ITERATIONS, 0xAE7E_8160_2026_0616L);
    }

    /** Run the standard campaign with an explicit iteration count and seed. */
    public static Result run(int iterationsPerTarget, long seed) {
        try (WasmBridgeFuzzTarget bridge = new WasmBridgeFuzzTarget()) {
            List<FuzzTarget> targets = new ArrayList<>();
            targets.add(new SpirvFuzzTarget());
            targets.add(new ComputeCompilerFuzzTarget());
            targets.add(new WasmLoaderFuzzTarget());
            targets.add(bridge);

            FuzzReport report = FuzzEngine.run(targets, iterationsPerTarget, seed);

            List<String> notes = new ArrayList<>();
            for (FuzzReport.TargetStat s : report.targets()) {
                notes.add(String.format("%-28s %,8d cases · %,7d handled · %,7d rejected · %d crashed",
                        s.name(), s.iterations(), s.handled(), s.rejected(), s.crashed()));
            }
            for (FuzzReport.Finding f : report.findings()) {
                notes.add("CRASH " + f);
            }
            return new Result(report, notes);
        }
    }

    /** Outcome of the standard campaign, rendered by the CLI {@code fuzz} command. */
    public record Result(FuzzReport report, List<String> notes) {

        public boolean passed() {
            return report.passed();
        }

        public long totalCases() {
            return report.totalCases();
        }

        public long totalRejected() {
            return report.totalRejected();
        }

        public List<FuzzReport.Finding> findings() {
            return report.findings();
        }
    }
}
