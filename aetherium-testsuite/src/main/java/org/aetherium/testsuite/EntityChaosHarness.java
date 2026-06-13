/*
 * Aetherium Framework — data-oriented entity chaos/stress test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructLayout;
import org.aetherium.core.compute.StructField;
import org.aetherium.core.tick.AetheriumAsyncTick;
import org.aetherium.core.tick.AetheriumTickEngine;
import org.aetherium.core.tick.AsyncTickTask;
import org.aetherium.core.tick.TickReport;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress test: 10,000 data-oriented entities advancing their positions in parallel, every tick.
 *
 * <p>EN: Entities live in one contiguous off-heap {@link StructArena} (fields x,y,z,vx,vy,vz). Each
 * tick the {@link AetheriumTickEngine} splits the entities into disjoint slices and advances each
 * slice on its own virtual thread (massive parallelism), joined by the Sync Barrier. Because the
 * slices are disjoint byte ranges of off-heap memory, there is no shared mutable Java state — hence
 * no locks, no {@code ConcurrentModificationException}, and no deadlocks. After all ticks we verify
 * every entity advanced by exactly {@code ticks * velocity} (proving correctness under parallelism)
 * and assert zero escapes (no task threw or timed out). Also exercises the {@code @AetheriumAsyncTick}
 * annotation path for DX.
 *
 * <p>RU: Сущности живут в одном непрерывном off-heap {@link StructArena} (поля x,y,z,vx,vy,vz). На
 * каждом тике {@link AetheriumTickEngine} делит сущности на непересекающиеся срезы и продвигает
 * каждый срез на своём виртуальном потоке (массовый параллелизм), объединяя Sync-барьером. Поскольку
 * срезы — непересекающиеся диапазоны off-heap памяти, нет общего изменяемого Java-состояния — значит
 * нет блокировок, {@code ConcurrentModificationException} и взаимоблокировок. После всех тиков мы
 * проверяем, что каждая сущность продвинулась ровно на {@code ticks * velocity} (корректность при
 * параллелизме) и утверждаем ноль escape. Также проверяется путь аннотации {@code @AetheriumAsyncTick}.
 */
public final class EntityChaosHarness {

    public static final int DEFAULT_ENTITIES = 10_000;
    public static final int DEFAULT_TICKS = 200;
    public static final int DEFAULT_TASKS = 256;

    private EntityChaosHarness() {
    }

    /** Structured outcome with performance metrics. */
    public record EntityChaosReport(int entities,
                                    int ticks,
                                    int tasksPerTick,
                                    long totalUpdates,
                                    double durationMillis,
                                    double maxTickMillis,
                                    long updatesPerSecond,
                                    long offHeapBytes,
                                    int escapes,
                                    int mismatches,
                                    boolean annotationDxOk) {
        public boolean passed() {
            return escapes == 0 && mismatches == 0 && annotationDxOk;
        }
    }

    public static EntityChaosReport run() {
        return run(DEFAULT_ENTITIES, DEFAULT_TICKS, DEFAULT_TASKS);
    }

    public static EntityChaosReport run(int entities, int ticks, int tasks) {
        StructLayout layout = StructLayout.builder()
                .doubles("x").doubles("y").doubles("z")
                .doubles("vx").doubles("vy").doubles("vz")
                .build();
        StructField x = layout.field("x"), y = layout.field("y"), z = layout.field("z");
        StructField vx = layout.field("vx"), vy = layout.field("vy"), vz = layout.field("vz");

        int escapes;
        int mismatches;
        double maxTickMillis = 0;
        long durationNanos;
        long offHeapBytes;

        try (StructArena arena = StructArena.allocate(layout, entities)) {
            offHeapBytes = arena.byteSize();

            // Deterministic init: position 0, a per-entity velocity so we can verify exactly.
            for (int i = 0; i < entities; i++) {
                arena.setDouble(i, x, 0.0);
                arena.setDouble(i, y, 0.0);
                arena.setDouble(i, z, 0.0);
                arena.setDouble(i, vx, 1.0);
                arena.setDouble(i, vy, 0.5);
                arena.setDouble(i, vz, 0.25);
            }

            // One AsyncTickTask per disjoint slice — advanced on its own virtual thread each tick.
            AetheriumTickEngine engine = new AetheriumTickEngine();
            int sliceSize = (entities + tasks - 1) / tasks;
            int actualTasks = 0;
            for (int t = 0; t < tasks; t++) {
                final int lo = t * sliceSize;
                final int hi = Math.min(entities, lo + sliceSize);
                if (lo >= hi) {
                    break;
                }
                actualTasks++;
                engine.register(new AsyncTickTask() {
                    @Override
                    public void computeAsync() {
                        for (int i = lo; i < hi; i++) {
                            arena.setDouble(i, x, arena.getDouble(i, x) + arena.getDouble(i, vx));
                            arena.setDouble(i, y, arena.getDouble(i, y) + arena.getDouble(i, vy));
                            arena.setDouble(i, z, arena.getDouble(i, z) + arena.getDouble(i, vz));
                        }
                    }

                    @Override
                    public String id() {
                        return "slice[" + lo + "," + hi + ")";
                    }
                });
            }

            int escapeCount = 0;
            long start = System.nanoTime();
            for (int tick = 0; tick < ticks; tick++) {
                TickReport report = engine.tick();
                escapeCount += report.timedOut() + report.failed();
                maxTickMillis = Math.max(maxTickMillis, report.durationMillis());
            }
            durationNanos = System.nanoTime() - start;
            escapes = escapeCount;
            tasks = actualTasks;

            // Verify correctness under parallelism: x advanced by ticks*1.0, y by ticks*0.5, z by ticks*0.25.
            int wrong = 0;
            double expX = ticks * 1.0, expY = ticks * 0.5, expZ = ticks * 0.25;
            for (int i = 0; i < entities; i++) {
                if (arena.getDouble(i, x) != expX || arena.getDouble(i, y) != expY || arena.getDouble(i, z) != expZ) {
                    wrong++;
                }
            }
            mismatches = wrong;
        }

        boolean annotationDxOk = verifyAnnotationDx();

        long totalUpdates = (long) entities * ticks;
        double durationMillis = durationNanos / 1_000_000.0;
        long updatesPerSecond = durationMillis > 0 ? (long) (totalUpdates / (durationMillis / 1000.0)) : 0;

        return new EntityChaosReport(entities, ticks, tasks, totalUpdates, durationMillis, maxTickMillis,
                updatesPerSecond, offHeapBytes, escapes, mismatches, annotationDxOk);
    }

    /** Confirms the zero-boilerplate {@code @AetheriumAsyncTick} annotation path runs a method. */
    private static boolean verifyAnnotationDx() {
        AnnotatedMod mod = new AnnotatedMod();
        AetheriumTickEngine engine = new AetheriumTickEngine();
        engine.registerAnnotated(mod);
        TickReport r = engine.tick();
        return engine.taskCount() == 1 && r.completed() == 1 && mod.runs.get() == 1;
    }

    /** A modder's class — they write only the annotated method; the framework does the rest. */
    static final class AnnotatedMod {
        final AtomicInteger runs = new AtomicInteger();

        @AetheriumAsyncTick("demo-physics")
        void heavyPhysics() {
            runs.incrementAndGet();
        }
    }
}
