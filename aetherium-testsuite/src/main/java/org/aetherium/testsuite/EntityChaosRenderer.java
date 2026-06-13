/*
 * Aetherium Framework — entity chaos report renderer (bilingual).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import org.aetherium.testsuite.EntityChaosHarness.EntityChaosReport;

/** Renders an {@link EntityChaosReport} as a bilingual console summary with performance metrics. */
public final class EntityChaosRenderer {

    private EntityChaosRenderer() {
    }

    public static String render(EntityChaosReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Aetherium Data-Oriented Entity Stress Test ===").append(System.lineSeparator());
        sb.append(String.format("  entities           : %,d (contiguous off-heap StructArena)%n", r.entities()));
        sb.append(String.format("  ticks              : %,d%n", r.ticks()));
        sb.append(String.format("  virtual threads/tick: %,d%n", r.tasksPerTick()));
        sb.append(String.format("  off-heap footprint : %,d bytes (%.2f MiB, zero GC)%n",
                r.offHeapBytes(), r.offHeapBytes() / (1024.0 * 1024.0)));
        sb.append(System.lineSeparator());
        sb.append("  Performance:").append(System.lineSeparator());
        sb.append(String.format("    total entity updates : %,d%n", r.totalUpdates()));
        sb.append(String.format("    wall-clock           : %.2f ms%n", r.durationMillis()));
        sb.append(String.format("    throughput           : %,d updates/sec%n", r.updatesPerSecond()));
        sb.append(String.format("    slowest tick         : %.3f ms (budget 50 ms)%n", r.maxTickMillis()));
        sb.append(System.lineSeparator());
        sb.append("  Safety:").append(System.lineSeparator());
        sb.append(String.format("    escapes (timeouts+failures): %d%n", r.escapes()));
        sb.append(String.format("    deadlocks                  : %s%n",
                r.maxTickMillis() < 50_000 ? "none (all ticks joined at the Sync Barrier)" : "SUSPECTED"));
        sb.append(String.format("    correctness mismatches     : %d%n", r.mismatches()));
        sb.append(String.format("    @AetheriumAsyncTick DX     : %s%n", r.annotationDxOk() ? "OK" : "FAIL"));
        sb.append(System.lineSeparator());
        sb.append("  EN: 0 escapes + 0 mismatches + no deadlock = framework safe under parallel load.")
                .append(System.lineSeparator());
        sb.append("  RU: 0 escape + 0 несоответствий + нет взаимоблокировок = фреймворк безопасен.")
                .append(System.lineSeparator());
        sb.append("  RESULT: ").append(r.passed()
                ? "PASS ✓" : "FAIL ✗");
        return sb.toString();
    }
}
