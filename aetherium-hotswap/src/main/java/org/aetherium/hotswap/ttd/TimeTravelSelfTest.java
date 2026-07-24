/*
 * Aetherium Framework — Time-Travel Debugger end-to-end self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap.ttd;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructField;
import org.aetherium.core.compute.StructLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A dependency-free proof of the Time-Travel Debugger: bounded journaling, byte-exact rewind, and
 * post-crash inspection.
 *
 * <p>EN: It journals a small off-heap entity arena through {@value #TICKS} deterministic physics ticks
 * ({@code x += vx}), then asserts three things: (1) after thousands of ticks the journal's footprint
 * stayed under its fixed ceiling — the ring buffer never grows without bound (the Durability rule);
 * (2) rewinding N ticks reconstructs the arena byte-exactly (entity 0's {@code x} at tick {@code T} is
 * exactly {@code T}), and rewinding past the retained window clamps to the oldest kept state; and
 * (3) when a later tick corrupts an entity and throws, the engine freezes the crash scene for inspection
 * yet the committed history remains intact — the developer can step back to the exact state before the
 * failure.
 *
 * <p>RU: Журналирует небольшую off-heap арену сущностей на {@value #TICKS} детерминированных тиков
 * физики ({@code x += vx}), затем проверяет: (1) после тысяч тиков объём журнала остался под фиксированным
 * потолком — кольцо не растёт неограниченно; (2) перемотка на N тиков реконструирует арену байт-в-байт, а
 * перемотка за пределы окна упирается в старейшее хранимое состояние; (3) когда поздний тик портит
 * сущность и бросает исключение, движок замораживает сцену краха для инспекции, но зафиксированная
 * история цела — можно вернуться к точному состоянию перед сбоем.
 */
public final class TimeTravelSelfTest {

    public static final int ENTITIES = 8;
    public static final int TICKS = 2_000;
    public static final int CAPACITY = 16;
    private static final double CORRUPT_SENTINEL = -999.0;
    private static final int CORRUPT_ENTITY = 3;

    private TimeTravelSelfTest() {
    }

    public record Result(int entities,
                         long ticksRun,
                         int journalCapacity,
                         int retainedFrames,
                         long journalBytes,
                         long journalMaxBytes,
                         boolean footprintBounded,
                         boolean rewindAccurate,
                         boolean clampWorks,
                         boolean faultCaptured,
                         long faultTick,
                         double faultCorruptValue,
                         boolean historyIntactAfterFault,
                         List<String> notes) {
        public boolean passed() {
            return footprintBounded && rewindAccurate && clampWorks
                    && faultCaptured && historyIntactAfterFault;
        }
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();

        StructLayout layout = StructLayout.builder()
                .doubles("x")     // position
                .doubles("vx")    // velocity
                .ints("id")
                .build();
        StructField x = layout.field("x");
        StructField vx = layout.field("vx");
        StructField id = layout.field("id");

        try (StructArena arena = StructArena.allocate(layout, ENTITIES)) {
            // Seed: entity i starts at x=0 with velocity vx=(i+1). So after t ticks, entity i's x = t*(i+1).
            for (int i = 0; i < ENTITIES; i++) {
                arena.setDouble(i, x, 0.0);
                arena.setDouble(i, vx, i + 1);
                arena.setInt(i, id, i);
            }

            TtdEngine engine = new TtdEngine(arena, CAPACITY);

            // (1) Thousands of clean ticks: x += vx for every entity.
            for (int t = 0; t < TICKS; t++) {
                engine.tick((a, tick) -> {
                    for (int i = 0; i < ENTITIES; i++) {
                        a.setDouble(i, x, a.getDouble(i, x) + a.getDouble(i, vx));
                    }
                });
            }
            notes.add("ran " + TICKS + " journaled ticks; committedTick=" + engine.committedTick());

            long journalBytes = engine.journal().estimatedRetainedBytes();
            long journalMax = engine.journal().maxRetainedBytes();
            boolean footprintBounded = engine.retainedFrames() == CAPACITY
                    && journalBytes <= journalMax
                    && journalMax < 64 * 1024; // a tiny, constant ceiling regardless of tick count
            notes.add("journal footprint = " + journalBytes + " B (ceiling " + journalMax
                    + " B, capacity " + CAPACITY + " frames) after " + TICKS
                    + " ticks -> bounded=" + footprintBounded);

            // (2) Rewind accuracy: entity 0 (vx=1) has x == tick. Verify several reconstructions.
            boolean rewindAccurate =
                    engine.rewind(0).getDouble(0, x) == TICKS            // latest
                    && engine.rewind(1).getDouble(0, x) == TICKS - 1
                    && engine.rewind(5).getDouble(0, x) == TICKS - 5
                    && engine.rewind(CAPACITY).getDouble(0, x) == TICKS - CAPACITY;
            // Cross-check a faster entity (id=7, vx=8) too.
            rewindAccurate = rewindAccurate
                    && engine.rewind(3).getDouble(7, x) == (double) (TICKS - 3) * 8;
            notes.add("rewind: entity0.x @now=" + engine.rewind(0).getDouble(0, x)
                    + ", @-1=" + engine.rewind(1).getDouble(0, x)
                    + ", @-5=" + engine.rewind(5).getDouble(0, x)
                    + " (expected " + TICKS + "/" + (TICKS - 1) + "/" + (TICKS - 5) + ")");

            // Clamp: rewinding far past the retained window yields the oldest reconstructable state.
            double clamped = engine.rewind(10_000).getDouble(0, x);
            double oldest = engine.rewind(CAPACITY).getDouble(0, x);
            boolean clampWorks = clamped == oldest;
            notes.add("rewind past window (10000 back) clamps to oldest retained x=" + clamped
                    + " (== rewind(" + CAPACITY + ")=" + oldest + ")");

            // Snapshot the last good value of the entity we're about to corrupt.
            double preFaultValue = engine.latest().getDouble(CORRUPT_ENTITY, x);

            // (3) A faulting tick: corrupt one entity, then throw (simulated crash / Heisenbug).
            TtdEngine.TickOutcome faulted = engine.tick((a, tick) -> {
                a.setDouble(CORRUPT_ENTITY, x, CORRUPT_SENTINEL);   // the corruption
                throw new IllegalStateException("entity " + CORRUPT_ENTITY + " NaN-guard tripped @tick " + tick);
            });
            boolean faultCaptured = faulted.status() == TtdEngine.Status.FAULTED
                    && engine.hasFault()
                    && engine.fault().faultState().getDouble(CORRUPT_ENTITY, x) == CORRUPT_SENTINEL;
            notes.add("fault: " + engine.fault().summary()
                    + "; crash scene shows entity " + CORRUPT_ENTITY + ".x=" + CORRUPT_SENTINEL);

            // History integrity: the faulted tick was NOT committed, so the latest committed state still
            // shows the pre-fault value — the debugger can inspect the exact state before the failure.
            boolean historyIntact = engine.committedTick() == TICKS
                    && engine.latest().getDouble(CORRUPT_ENTITY, x) == preFaultValue
                    && engine.rewind(0).getDouble(CORRUPT_ENTITY, x) == preFaultValue;
            notes.add("post-fault: committedTick still " + engine.committedTick()
                    + "; last-good entity " + CORRUPT_ENTITY + ".x=" + preFaultValue
                    + " (faulted tick uncommitted) -> historyIntact=" + historyIntact);

            return new Result(ENTITIES, TICKS, CAPACITY, engine.retainedFrames(),
                    journalBytes, journalMax, footprintBounded, rewindAccurate, clampWorks,
                    faultCaptured, engine.fault().tick(), CORRUPT_SENTINEL, historyIntact,
                    List.copyOf(notes));
        }
    }
}
