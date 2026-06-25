/*
 * Aetherium Framework — the fuzzing campaign as a build gate (runs during ./gradlew check).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the standard fuzzing campaign so it executes automatically on every {@code ./gradlew check}.
 *
 * <p>EN: This is the rule made executable: an aggressive campaign over the SPIR-V and WASM
 * attack surface that must produce <strong>zero</strong> unexpected throwables (the proof that no
 * adversarial input crashed the JVM or host). It additionally asserts the campaign actually reached the
 * contractual reject paths — a fuzzer that feeds only inputs the code ignores proves nothing.
 * RU: Правило фазы 16, ставшее исполняемым: агрессивная кампания по поверхности SPIR-V и WASM, которая
 * обязана дать <strong>ноль</strong> неожиданных throwable. Дополнительно проверяет, что кампания реально
 * достигла путей контрактного отказа.
 */
final class FuzzerCheckTest {

    @Test
    void campaignNeverCrashesTheJvmOrHost() {
        FuzzerSelfTest.Result result = FuzzerSelfTest.run();

        assertTrue(result.passed(),
                () -> "fuzzer caught a crash:\n  " + String.join("\n  ",
                        result.findings().stream().map(Object::toString).toList()));

        // 4 targets × default iterations — the campaign really ran at volume.
        assertEquals(4L * FuzzerSelfTest.DEFAULT_ITERATIONS, result.totalCases());

        // The fuzzer must have driven the production code down its contractual reject paths, not just
        // fed inputs it silently accepted — otherwise "no crash" would be vacuous.
        assertTrue(result.totalRejected() > 0,
                "expected the fuzzer to reach contractual reject paths");
    }

    @Test
    void everyTargetExecutedItsCases() {
        FuzzReport report = FuzzerSelfTest.run(500, 0x1234_5678L).report();
        assertEquals(4, report.targets().size());
        for (FuzzReport.TargetStat s : report.targets()) {
            assertEquals(500L, s.iterations(), s.name());
            assertEquals(s.iterations(), s.handled() + s.rejected() + s.crashed(), s.name());
            assertEquals(0L, s.crashed(), () -> s.name() + " crashed");
        }
    }
}
