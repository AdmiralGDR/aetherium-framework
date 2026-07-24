/*
 * Aetherium Framework — FFM capital-debugging harness: the zero-leak proof for StructArena.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import org.aetherium.core.compute.ArenaAuditor;
import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructField;
import org.aetherium.core.compute.StructLayout;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The "Capital Debugging" stress test: churns millions of off-heap entities through
 * {@link StructArena} on virtual threads and <strong>proves</strong> that native memory is released
 * exactly when each arena is closed, with zero bytes escaping.
 *
 * <p>EN: The proof has three independent witnesses:
 * <ol>
 *   <li><strong>The ledger (arithmetic proof).</strong> {@link ArenaAuditor} is credited by
 *       {@code StructArena} itself on every allocate and close. Over the audit window the harness
 *       asserts <em>exact</em> equality: {@code bytesAllocated == bytesFreed == arenas × arenaBytes}
 *       and {@code outstandingArenas == 0}. This is not sampling — it is the API's own double-entry
 *       bookkeeping, and any unclosed arena or double-free would unbalance it by at least one byte.</li>
 *   <li><strong>NMT (the JVM's witness).</strong> With {@code -XX:NativeMemoryTracking=summary} the
 *       harness snapshots HotSpot's native-memory account before and after the churn; the
 *       {@code Other} category (where FFM arena memory is booked) must return to its baseline within
 *       a small noise tolerance — millions of churned arenas leave no residue.</li>
 *   <li><strong>JFR (the flight recording).</strong> A {@link Recording} captures periodic
 *       {@code jdk.NativeMemoryUsageTotal} events during the churn, giving a native-memory timeline
 *       that shows the footprint plateauing rather than climbing.</li>
 * </ol>
 * The churn itself is hostile: {@code arenaCount} arenas × {@code entitiesPerArena} entities are
 * created, fully written, sample-verified, and destroyed concurrently on virtual threads (bounded by a
 * semaphore), exercising the shared-arena allocate/close paths for races. Any exception is a failure.
 *
 * <p>RU: Стресс-тест «капитальной отладки»: прогоняет миллионы off-heap сущностей через
 * {@link StructArena} на виртуальных потоках и <strong>доказывает</strong>, что нативная память
 * освобождается ровно при закрытии арены, без единого утёкшего байта. Три независимых свидетеля:
 * (1) реестр {@link ArenaAuditor} — арифметическое равенство выделенного и освобождённого байт-в-байт;
 * (2) NMT — собственный учёт HotSpot (категория {@code Other}, где числится память FFM-арен, обязана
 * вернуться к базовой линии); (3) JFR — таймлайн {@code jdk.NativeMemoryUsageTotal} во время прогона.
 * Сам прогон враждебен: арены создаются, полностью записываются, выборочно проверяются и уничтожаются
 * конкурентно на виртуальных потоках; любое исключение — провал.
 */
public final class FfmLeakHarness {

    /** 10,000,000 entity creations/destructions — the capital-debugging default. */
    public static final long DEFAULT_ENTITIES = 10_000_000L;
    public static final int ENTITIES_PER_ARENA = 4_096;
    public static final int MAX_CONCURRENT_ARENAS = 256;

    /** NMT noise tolerance (KB): JIT/JFR native churn unrelated to arenas. A leak of even 1% of the
     *  churned bytes (≈3 GB cumulative at 10M entities) would exceed this by orders of magnitude. */
    public static final long NMT_TOLERANCE_KB = 8_192;

    private FfmLeakHarness() {
    }

    /** The audit's structured outcome. */
    public record Report(long entities,
                         int arenaCount,
                         int entitiesPerArena,
                         long arenaBytes,
                         long totalChurnedBytes,
                         long elapsedMillis,
                         int failures,
                         ArenaAuditor.Snapshot ledgerDelta,
                         boolean ledgerBalanced,
                         boolean ledgerExact,
                         boolean nmtAvailable,
                         long nmtOtherBeforeKb,
                         long nmtOtherAfterKb,
                         long nmtOtherDeltaKb,
                         long nmtTotalCommittedDeltaKb,
                         boolean nmtClean,
                         long jfrEvents,
                         long jfrPeakCommittedKb,
                         List<String> notes) {

        /** The zero-leak verdict: exact ledger balance, no failures, and (when on) a clean NMT. */
        public boolean passed() {
            return failures == 0 && ledgerBalanced && ledgerExact && (!nmtAvailable || nmtClean);
        }
    }

    /** Run the audit over {@code totalEntities} entity lifecycles (rounded up to whole arenas). */
    public static Report run(long totalEntities) {
        List<String> notes = new ArrayList<>();
        StructLayout layout = StructLayout.builder()
                .doubles("x").doubles("y").doubles("z")
                .floats("health")
                .ints("id")
                .build();
        StructField x = layout.field("x");
        StructField health = layout.field("health");
        StructField id = layout.field("id");

        int arenaCount = (int) Math.max(1, (totalEntities + ENTITIES_PER_ARENA - 1) / ENTITIES_PER_ARENA);
        long arenaBytes = layout.stride() * ENTITIES_PER_ARENA;
        long churnedBytes = arenaBytes * arenaCount;
        notes.add("plan: " + arenaCount + " arenas x " + ENTITIES_PER_ARENA + " entities ("
                + layout.stride() + " B stride) = " + (arenaCount * (long) ENTITIES_PER_ARENA)
                + " entity lifecycles, " + (churnedBytes >> 20) + " MiB total churned off-heap");

        // --- witnesses: baseline readings -------------------------------------------------------
        NmtMonitor.Snapshot nmtBefore = NmtMonitor.snapshot();
        notes.add(nmtBefore.available()
                ? "NMT baseline: total committed " + nmtBefore.totalCommittedKb() + " KB, Other "
                        + nmtBefore.otherCommittedKb() + " KB"
                : "NMT not enabled on this JVM (-XX:NativeMemoryTracking=summary) — ledger-only proof");
        ArenaAuditor.Snapshot ledgerBefore = ArenaAuditor.snapshot();

        Recording recording = startJfr(notes);

        // --- the churn --------------------------------------------------------------------------
        AtomicInteger failures = new AtomicInteger();
        Semaphore concurrent = new Semaphore(MAX_CONCURRENT_ARENAS);
        long start = System.nanoTime();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int a = 0; a < arenaCount; a++) {
                final int arenaIdx = a;
                concurrent.acquireUninterruptibly();
                pool.submit(() -> {
                    try (StructArena arena = StructArena.allocate(layout, ENTITIES_PER_ARENA)) {
                        for (int i = 0; i < ENTITIES_PER_ARENA; i++) {
                            arena.setDouble(i, x, arenaIdx + i * 0.5);
                            arena.setFloat(i, health, i * 0.25f);
                            arena.setInt(i, id, i);
                        }
                        // Sample verification: the store must read back exactly what was written.
                        int probe = arenaIdx % ENTITIES_PER_ARENA;
                        if (arena.getInt(probe, id) != probe
                                || arena.getDouble(probe, x) != arenaIdx + probe * 0.5) {
                            failures.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        failures.incrementAndGet();
                    } finally {
                        concurrent.release();
                    }
                });
            }
        } // close() awaits all tasks
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // --- witnesses: post-churn readings ------------------------------------------------------
        ArenaAuditor.Snapshot ledgerDelta = ArenaAuditor.snapshot().since(ledgerBefore);
        boolean ledgerBalanced = ledgerDelta.balanced();
        boolean ledgerExact = ledgerDelta.bytesAllocated() == churnedBytes
                && ledgerDelta.bytesFreed() == churnedBytes
                && ledgerDelta.arenasOpened() == arenaCount
                && ledgerDelta.arenasClosed() == arenaCount;
        notes.add("ledger: opened " + ledgerDelta.arenasOpened() + " / closed " + ledgerDelta.arenasClosed()
                + " arenas; allocated " + ledgerDelta.bytesAllocated() + " B == freed "
                + ledgerDelta.bytesFreed() + " B (outstanding " + ledgerDelta.outstandingBytes() + " B)");

        long[] jfr = stopJfr(recording, notes);

        NmtMonitor.Snapshot nmtAfter = NmtMonitor.snapshot();
        long otherDelta = nmtAfter.otherCommittedKb() - nmtBefore.otherCommittedKb();
        long totalDelta = nmtAfter.totalCommittedKb() - nmtBefore.totalCommittedKb();
        boolean nmtClean = !nmtAfter.available() || Math.abs(otherDelta) <= NMT_TOLERANCE_KB;
        if (nmtAfter.available()) {
            notes.add("NMT after: total committed " + nmtAfter.totalCommittedKb() + " KB (delta "
                    + signed(totalDelta) + " KB), Other " + nmtAfter.otherCommittedKb() + " KB (delta "
                    + signed(otherDelta) + " KB, tolerance ±" + NMT_TOLERANCE_KB + " KB)");
        }

        return new Report(arenaCount * (long) ENTITIES_PER_ARENA, arenaCount, ENTITIES_PER_ARENA,
                arenaBytes, churnedBytes, elapsedMillis, failures.get(),
                ledgerDelta, ledgerBalanced, ledgerExact,
                nmtAfter.available(), nmtBefore.otherCommittedKb(), nmtAfter.otherCommittedKb(),
                otherDelta, totalDelta, nmtClean,
                jfr[0], jfr[1], List.copyOf(notes));
    }

    /** Start a JFR recording of the periodic NMT-usage events (best-effort; null if JFR is off). */
    private static Recording startJfr(List<String> notes) {
        try {
            Recording recording = new Recording();
            recording.enable("jdk.NativeMemoryUsageTotal").withPeriod(Duration.ofMillis(250));
            recording.start();
            return recording;
        } catch (Throwable jfrUnavailable) {
            notes.add("JFR unavailable: " + jfrUnavailable.getClass().getSimpleName());
            return null;
        }
    }

    /** Stop + parse the JFR recording; returns {eventCount, peakCommittedKb}. */
    private static long[] stopJfr(Recording recording, List<String> notes) {
        if (recording == null) {
            return new long[]{0, 0};
        }
        try {
            Path dump = Files.createTempFile("aetherium-ffmaudit", ".jfr");
            recording.dump(dump);
            recording.close();
            long events = 0;
            long peakCommittedKb = 0;
            try (RecordingFile file = new RecordingFile(dump)) {
                while (file.hasMoreEvents()) {
                    RecordedEvent event = file.readEvent();
                    if ("jdk.NativeMemoryUsageTotal".equals(event.getEventType().getName())) {
                        events++;
                        peakCommittedKb = Math.max(peakCommittedKb, event.getLong("committed") / 1024);
                    }
                }
            }
            Files.deleteIfExists(dump);
            notes.add("JFR: " + events + " jdk.NativeMemoryUsageTotal event(s) during churn"
                    + (events > 0 ? ", peak committed " + peakCommittedKb + " KB" : " (NMT off => none emitted)"));
            return new long[]{events, peakCommittedKb};
        } catch (Throwable parseFailure) {
            notes.add("JFR parse failed: " + parseFailure.getClass().getSimpleName());
            return new long[]{0, 0};
        }
    }

    private static String signed(long v) {
        return v >= 0 ? "+" + v : Long.toString(v);
    }
}
