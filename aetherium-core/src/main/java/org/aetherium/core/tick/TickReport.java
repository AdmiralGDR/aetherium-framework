/*
 * Aetherium Framework — tick report.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.tick;

/**
 * Outcome of a single {@link AetheriumTickEngine#tick()}.
 *
 * @param totalTasks   tasks dispatched this tick
 * @param completed    tasks whose async phase finished inside the budget
 * @param timedOut     tasks cancelled at the Sync Barrier for exceeding the budget
 * @param failed       tasks whose async phase threw (contained, not propagated)
 * @param committed    tasks whose commit() ran on the main thread
 * @param durationNanos wall-clock duration of the whole tick
 * @param withinBudget whether the tick finished inside its time budget
 */
public record TickReport(int totalTasks,
                         int completed,
                         int timedOut,
                         int failed,
                         int committed,
                         long durationNanos,
                         boolean withinBudget) {

    public double durationMillis() {
        return durationNanos / 1_000_000.0;
    }

    /** No task timed out or failed. */
    public boolean clean() {
        return timedOut == 0 && failed == 0;
    }
}
