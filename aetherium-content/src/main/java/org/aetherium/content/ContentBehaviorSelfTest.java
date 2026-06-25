/*
 * Aetherium Framework — content-behavior self-test (machine logic ticking, offline).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import org.aetherium.datagen.BehaviorEntry;
import org.aetherium.datagen.BehaviorIndex;
import org.aetherium.datagen.ContentKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives an {@link AetheriumMachineLogic} with a fake {@link MachineContext} and round-trips a
 * {@link BehaviorEntry} — proving the behavior pipeline works with no game.
 *
 * <p>EN: A sample smelter machine accumulates progress each tick and finishes a smelt every 10 ticks; the
 * test runs 25 ticks and asserts the persistent {@link MachineState}. Then it serializes/parses a behavior
 * index line to confirm the compile-time → run-time hand-off the loader consumes. The CLI {@code behavior}
 * command renders the result.
 * RU: Пример печи накапливает прогресс каждый тик и завершает плавку каждые 10 тиков; тест прогоняет 25
 * тиков и проверяет сохраняемое {@link MachineState}. Затем сериализует/парсит строку индекса поведений.
 */
public final class ContentBehaviorSelfTest {

    private ContentBehaviorSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();

        // 1) Tick a machine for 25 ticks; it finishes a smelt every 10 ticks.
        AetheriumMachineLogic smelter = new Smelter();
        FakeContext ctx = new FakeContext();
        smelter.onPlaced(ctx);
        for (int t = 0; t < 25; t++) {
            ctx.tick = t;
            smelter.tick(ctx);
        }
        long smelted = ctx.state().getLong("smelted", -1);
        long progress = ctx.state().getLong("progress", -1);
        boolean tickingOk = smelted == 2 && progress == 5;
        notes.add("smelter after 25 ticks: smelted=" + smelted + ", progress=" + progress);

        // 2) The behavior index round-trips (processor → loader hand-off).
        BehaviorEntry entry = new BehaviorEntry(ContentKind.BLOCK, "demo", "iron_furnace",
                "com.example.IronFurnaceLogic", true);
        String line = BehaviorIndex.serialize(entry);
        BehaviorEntry decoded = BehaviorIndex.parse(line);
        boolean indexOk = entry.equals(decoded) && decoded != null && decoded.machineLogic();
        notes.add("behavior index line: " + line);

        boolean passed = tickingOk && indexOk;
        return new Result(tickingOk, indexOk, smelted, notes, passed);
    }

    /** Outcome of the content-behavior self-test, rendered by the CLI {@code behavior} command. */
    public record Result(boolean tickingOk, boolean indexOk, long smeltCount,
                         List<String> notes, boolean passed) {
    }

    /** A sample machine: accumulate progress, finish a smelt every 10 ticks. */
    private static final class Smelter implements AetheriumMachineLogic {
        private static final long SMELT_TIME = 10;

        @Override
        public void tick(MachineContext ctx) {
            long progress = ctx.state().increment("progress", 1);
            if (progress >= SMELT_TIME) {
                ctx.state().setLong("progress", 0);
                ctx.state().increment("smelted", 1);
            }
        }
    }

    /** A pure, in-memory MachineContext (the loader supplies the real one over a BlockEntity). */
    private static final class FakeContext implements MachineContext {
        private final MachineState state = new MachineState();
        private long tick;

        @Override public long ticks() { return tick; }
        @Override public boolean isClient() { return false; }
        @Override public int x() { return 0; }
        @Override public int y() { return 64; }
        @Override public int z() { return 0; }
        @Override public MachineState state() { return state; }
    }
}
