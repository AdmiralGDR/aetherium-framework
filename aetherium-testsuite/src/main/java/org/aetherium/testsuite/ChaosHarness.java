/*
 * Aetherium Framework — Chaos Engineering harness.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import org.aetherium.bytecode.BytecodeEngine;
import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.CollectingDiagnosticSink;
import org.aetherium.bytecode.TransformResult;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Drives the chaos run: hundreds of hostile "mods" loaded simultaneously on virtual threads.
 *
 * <p>EN: Simulates 500+ heavy mods initializing at once. Each task synthesizes a (usually corrupted)
 * class and feeds it to a real {@link BytecodeEngine} whose chain includes a transformer that
 * randomly throws — so we stress both input corruption and transformer-exception fallback. A
 * fraction of tasks instead exercise {@link NativeChaos}. Everything runs inside a top-level
 * {@code try/catch(Throwable)} per task: if the engine's safety net ever failed, we would record an
 * "escape". The assertion is zero escapes and a live JVM.
 *
 * <p>RU: Имитирует одновременную инициализацию 500+ тяжёлых модов. Каждая задача синтезирует
 * (обычно повреждённый) класс и передаёт его реальному {@link BytecodeEngine}, цепочка которого
 * включает случайно бросающий трансформер — так мы нагружаем и порчу входа, и откат при исключении
 * трансформера. Часть задач вместо этого выполняет {@link NativeChaos}. Всё выполняется внутри
 * верхнеуровневого {@code try/catch(Throwable)} на задачу: если бы страховка движка отказала, мы
 * зафиксировали бы «escape». Утверждение — ноль escape и живая JVM.
 */
public final class ChaosHarness {

    /** Default simulated mod count — comfortably above the 500-mod target. */
    public static final int DEFAULT_MOD_COUNT = 600;

    private ChaosHarness() {
    }

    public static ChaosReport run() {
        return run(DEFAULT_MOD_COUNT);
    }

    public static ChaosReport run(int modCount) {
        // ~15% of tasks are native chaos; the rest are bytecode chaos.
        int nativeCount = Math.max(64, modCount / 6);

        CollectingDiagnosticSink sink = new CollectingDiagnosticSink();
        BytecodeEngine engine = BytecodeEngine.builder()
                .transformer(new RandomlyFailingTransformer())
                .classLoader(ChaosHarness.class.getClassLoader())
                .build();

        AtomicInteger transformedOk = new AtomicInteger();
        AtomicInteger reverted = new AtomicInteger();
        AtomicInteger escaped = new AtomicInteger();
        AtomicInteger nativeContained = new AtomicInteger();
        AtomicInteger nativeEscaped = new AtomicInteger();
        Map<String, LongAdder> byKind = new ConcurrentHashMap<>();

        long start = System.nanoTime();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?>[] futures = new Future<?>[modCount + nativeCount];
            int idx = 0;

            // Bytecode chaos tasks.
            for (int i = 0; i < modCount; i++) {
                final int seq = i;
                futures[idx++] = pool.submit(() -> {
                    try {
                        // 1 in 8 tasks is a valid control sample; the rest are corrupted.
                        ChaosMutators.Kind kind = (seq % 8 == 0)
                                ? ChaosMutators.Kind.VALID
                                : ChaosMutators.randomCorruption();
                        byte[] original = ChaosMutators.mutate(kind, seq);

                        byte[] result = engine.transformClass(original, sink);

                        if (Arrays.equals(original, result)) {
                            reverted.incrementAndGet();
                        } else {
                            transformedOk.incrementAndGet();
                        }
                        byKind.computeIfAbsent(kind.name(), k -> new LongAdder()).increment();
                    } catch (Throwable escape) {
                        // The engine is contractually total; reaching here means the safety net failed.
                        escaped.incrementAndGet();
                    }
                });
            }

            // Native/FFM chaos tasks.
            for (int i = 0; i < nativeCount; i++) {
                futures[idx++] = pool.submit(() -> {
                    try {
                        if (NativeChaos.runOne()) {
                            nativeContained.incrementAndGet();
                        } else {
                            nativeEscaped.incrementAndGet(); // op was NOT contained as expected
                        }
                    } catch (Throwable escape) {
                        nativeEscaped.incrementAndGet();
                    }
                });
            }

            // Join everything.
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Throwable joinFailure) {
                    escaped.incrementAndGet();
                }
            }
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000L;

        Map<String, Integer> kindCounts = new ConcurrentHashMap<>();
        byKind.forEach((k, v) -> kindCounts.put(k, v.intValue()));

        return new ChaosReport(
                modCount,
                transformedOk.get(),
                reverted.get(),
                escaped.get(),
                sink.count(),
                Map.copyOf(kindCounts),
                nativeCount,
                nativeContained.get(),
                nativeEscaped.get(),
                modCount + nativeCount,
                durationMs);
    }

    /**
     * A transformer that throws for a random ~30% of valid classes — to stress the engine's
     * revert-to-original path even when the input bytecode itself is fine.
     */
    private static final class RandomlyFailingTransformer implements ClassTransformer {
        @Override
        public int order() {
            return 100;
        }

        @Override
        public boolean handles(ClassContext context) {
            return true;
        }

        @Override
        public TransformResult apply(ClassContext context) {
            if (ThreadLocalRandom.current().nextInt(100) < 30) {
                throw new IllegalStateException("chaos: simulated transformer explosion");
            }
            return new TransformResult.Skipped("chaos no-op");
        }
    }
}
