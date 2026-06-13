/*
 * Aetherium Framework — chaos report renderer (bilingual).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import java.util.Map;
import java.util.TreeMap;

/**
 * Renders a {@link ChaosReport} as a bilingual console summary. Shared by the standalone
 * {@link ChaosMain} and the {@code aetherium-cli chaos} command.
 */
public final class ChaosReportRenderer {

    private ChaosReportRenderer() {
    }

    public static String render(ChaosReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Aetherium Chaos Engineering — Stress Test Summary ===").append(System.lineSeparator());
        sb.append(String.format("  simulated mods (parallel virtual threads): %d%n", r.threadsUsed()));
        sb.append(String.format("  duration: %d ms%n", r.durationMs()));
        sb.append(System.lineSeparator());
        sb.append("  Bytecode chaos:").append(System.lineSeparator());
        sb.append(String.format("    tasks            : %d%n", r.modTasks()));
        sb.append(String.format("    transformed OK   : %d%n", r.transformedOk()));
        sb.append(String.format("    safely reverted  : %d%n", r.reverted()));
        sb.append(String.format("    diagnostics      : %d%n", r.diagnostics()));
        sb.append(String.format("    ESCAPED (uncaught): %d%n", r.escaped()));
        sb.append("    by corruption kind:").append(System.lineSeparator());
        for (Map.Entry<String, Integer> e : new TreeMap<>(r.byKind()).entrySet()) {
            sb.append(String.format("      %-16s %d%n", e.getKey(), e.getValue()));
        }
        sb.append(System.lineSeparator());
        sb.append("  Native/FFM chaos:").append(System.lineSeparator());
        sb.append(String.format("    tasks            : %d%n", r.nativeTasks()));
        sb.append(String.format("    contained        : %d%n", r.nativeContained()));
        sb.append(String.format("    ESCAPED          : %d%n", r.nativeEscaped()));
        sb.append(System.lineSeparator());
        sb.append(String.format("  EN: JVM survived = true; total escapes = %d.%n", r.escaped() + r.nativeEscaped()));
        sb.append(String.format("  RU: JVM выжила = true; всего escape = %d.%n", r.escaped() + r.nativeEscaped()));
        sb.append(System.lineSeparator());
        sb.append("  RESULT: ").append(r.passed() ? "PASS ✓ (framework contained all catastrophic failures)"
                : "FAIL ✗ (a failure escaped containment)");
        return sb.toString();
    }
}
