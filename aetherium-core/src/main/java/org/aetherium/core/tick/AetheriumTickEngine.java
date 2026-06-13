/*
 * Aetherium Framework — async tick engine (virtual threads + Sync Barrier).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.tick;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Offloads heavy per-tick logic onto Java 21 virtual threads, joined by a Sync Barrier each tick.
 *
 * <p>EN: Register {@link AsyncTickTask}s (or annotated methods via {@link #registerAnnotated}). Each
 * {@link #tick()}: (1) dispatch every task's {@code computeAsync()} on its own virtual thread —
 * massively parallel, cheap to spawn; (2) the <strong>Sync Barrier</strong> waits for all of them up
 * to the tick budget (default 50 ms), cancelling stragglers; (3) {@code commit()} runs sequentially
 * on the calling (main) thread for tasks that finished, writing results back safely. The split makes
 * parallel ticking free of {@code ConcurrentModificationException}. The engine is total: a task that
 * throws or times out is contained and counted in the {@link TickReport}; the tick never crashes.
 *
 * <p>RU: Регистрируйте {@link AsyncTickTask} (или аннотированные методы через
 * {@link #registerAnnotated}). На каждом {@link #tick()}: (1) запуск {@code computeAsync()} каждой
 * задачи на своём виртуальном потоке — массовый параллелизм, дёшево; (2) <strong>Sync-барьер</strong>
 * ждёт всех в пределах бюджета тика (по умолчанию 50 мс), отменяя опоздавших; (3) {@code commit()}
 * выполняется последовательно на вызывающем (главном) потоке для завершившихся задач, безопасно
 * записывая результаты. Разделение избавляет параллельный тик от
 * {@code ConcurrentModificationException}. Движок тотален: задача, бросившая исключение или
 * превысившая бюджет, локализуется и учитывается в {@link TickReport}; тик не падает.
 */
public final class AetheriumTickEngine {

    /** Minecraft's tick is 50 ms (20 TPS). */
    public static final Duration DEFAULT_BUDGET = Duration.ofMillis(50);

    private final List<AsyncTickTask> tasks = new CopyOnWriteArrayList<>();
    private final Duration budget;

    public AetheriumTickEngine() {
        this(DEFAULT_BUDGET);
    }

    public AetheriumTickEngine(Duration budget) {
        this.budget = budget;
    }

    /** Register a task to run every tick. */
    public AetheriumTickEngine register(AsyncTickTask task) {
        tasks.add(task);
        return this;
    }

    /**
     * Reflectively register every no-arg method on {@code holder} annotated {@link AetheriumAsyncTick}
     * as an async task. This is the zero-boilerplate entry point for modders.
     */
    public AetheriumTickEngine registerAnnotated(Object holder) {
        for (Method method : holder.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(AetheriumAsyncTick.class) && method.getParameterCount() == 0) {
                method.setAccessible(true);
                AetheriumAsyncTick meta = method.getAnnotation(AetheriumAsyncTick.class);
                String label = meta.value().isEmpty() ? method.getName() : meta.value();
                tasks.add(new AsyncTickTask() {
                    @Override
                    public void computeAsync() {
                        try {
                            method.invoke(holder);
                        } catch (ReflectiveOperationException e) {
                            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
                        }
                    }

                    @Override
                    public String id() {
                        return label;
                    }
                });
            }
        }
        return this;
    }

    public int taskCount() {
        return tasks.size();
    }

    /**
     * Run one tick: parallel async phase → Sync Barrier → sequential commit phase. Never throws.
     */
    public TickReport tick() {
        long start = System.nanoTime();
        long deadlineNanos = start + budget.toNanos();

        List<AsyncTickTask> snapshot = new ArrayList<>(tasks);
        int total = snapshot.size();
        int completed = 0;
        int timedOut = 0;
        int failed = 0;
        int committed = 0;

        boolean[] ok = new boolean[total];

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>(total);
            for (AsyncTickTask task : snapshot) {
                futures.add(executor.submit(() -> {
                    // Contain every failure here so one bad task can't abort the barrier.
                    task.computeAsync();
                    return Boolean.TRUE;
                }));
            }

            // --- Sync Barrier: await all async work up to the tick budget ---
            for (int i = 0; i < total; i++) {
                long remaining = deadlineNanos - System.nanoTime();
                Future<Boolean> future = futures.get(i);
                if (remaining <= 0) {
                    future.cancel(true);
                    timedOut++;
                    continue;
                }
                try {
                    future.get(remaining, TimeUnit.NANOSECONDS);
                    ok[i] = true;
                    completed++;
                } catch (TimeoutException te) {
                    future.cancel(true);
                    timedOut++;
                } catch (Exception ex) {
                    failed++;
                }
            }
        }

        // --- Commit phase: main thread, sequential, only for tasks that finished cleanly ---
        for (int i = 0; i < total; i++) {
            if (ok[i]) {
                try {
                    snapshot.get(i).commit();
                    committed++;
                } catch (Throwable commitFailure) {
                    // A failed commit is contained; the tick continues.
                    failed++;
                }
            }
        }

        long durationNanos = System.nanoTime() - start;
        boolean withinBudget = durationNanos <= budget.toNanos();
        return new TickReport(total, completed, timedOut, failed, committed, durationNanos, withinBudget);
    }
}
