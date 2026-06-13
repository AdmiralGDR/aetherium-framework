/*
 * Aetherium Framework — chaos run report.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import java.util.Map;

/**
 * Structured outcome of a Chaos Engineering run.
 *
 * <p>EN: The headline invariant is {@link #escaped()} == 0 and {@link #nativeEscaped()} == 0 — i.e.
 * no hostile input ever produced an uncaught {@link Throwable} or an uncontained native fault. Since
 * the harness reaches the point of building this report, the JVM is alive by construction.
 *
 * <p>RU: Главный инвариант — {@link #escaped()} == 0 и {@link #nativeEscaped()} == 0, т.е. ни один
 * враждебный вход не породил неперехваченного {@link Throwable} или неконтролируемого нативного
 * сбоя. Поскольку харнесс дошёл до построения этого отчёта, JVM по построению жива.
 *
 * @param modTasks        number of bytecode chaos tasks
 * @param transformedOk   tasks whose (valid) class transformed successfully
 * @param reverted        tasks safely reverted to the input bytes (contained failure)
 * @param escaped         tasks that leaked an uncaught throwable (MUST be 0)
 * @param diagnostics     total structured diagnostics emitted by the engine
 * @param byKind          revert/handle counts per corruption kind
 * @param nativeTasks     number of FFM/native chaos tasks
 * @param nativeContained native faults safely contained as catchable exceptions
 * @param nativeEscaped   native faults not contained (MUST be 0)
 * @param threadsUsed     virtual threads spawned (one per task)
 * @param durationMs      wall-clock duration
 */
public record ChaosReport(int modTasks,
                          int transformedOk,
                          int reverted,
                          int escaped,
                          int diagnostics,
                          Map<String, Integer> byKind,
                          int nativeTasks,
                          int nativeContained,
                          int nativeEscaped,
                          int threadsUsed,
                          long durationMs) {

    /** The framework survived catastrophic input with zero escapes and full accounting. */
    public boolean passed() {
        return escaped == 0
                && nativeEscaped == 0
                && (transformedOk + reverted) == modTasks;
    }
}
