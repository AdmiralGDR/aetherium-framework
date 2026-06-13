/*
 * Aetherium Framework — async tick task.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.tick;

/**
 * A unit of per-tick work split into a parallel phase and a main-thread commit phase.
 *
 * <p>EN: {@link #computeAsync()} runs on a virtual thread and must touch only data the task owns
 * (its own off-heap slice, local buffers) — never shared Minecraft state. {@link #commit()} runs
 * <em>after</em> the Sync Barrier, sequentially on the main thread, to write results back safely.
 * This split is what makes parallel ticking free of {@code ConcurrentModificationException}.
 *
 * <p>RU: {@link #computeAsync()} выполняется на виртуальном потоке и должен трогать только данные,
 * которыми владеет задача (свой off-heap срез, локальные буферы) — никогда общее состояние
 * Minecraft. {@link #commit()} выполняется <em>после</em> Sync-барьера, последовательно на главном
 * потоке, чтобы безопасно записать результаты. Это разделение и избавляет параллельный тик от
 * {@code ConcurrentModificationException}.
 */
public interface AsyncTickTask {

    /** Heavy, parallel-safe work. Runs on a virtual thread. */
    void computeAsync();

    /** Optional main-thread write-back, run after the barrier. Default: nothing to commit. */
    default void commit() {
    }

    /** Stable id for diagnostics. */
    default String id() {
        return getClass().getSimpleName();
    }
}
