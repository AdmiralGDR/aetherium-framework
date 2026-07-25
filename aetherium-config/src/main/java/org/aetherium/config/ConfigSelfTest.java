/*
 * Aetherium Framework — config store self-test (round-trip + atomic write + hot-reload).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.config;

import org.aetherium.network.Tree;
import org.aetherium.network.TreeNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exercises the whole {@link ConfigStore} lifecycle with a real temp file — no game, no framework.
 *
 * <p>EN: (1) opens a store on a missing file and proves the defaults were written; (2) round-trips a value
 * through JSON and re-reads it byte-for-byte; (3) proves {@link ConfigStore#validate} clamps a bad value; and
 * (4) hand-edits the file on disk and proves the {@code WatchService} hot-reload fires with the new value —
 * while a deliberately malformed edit is contained (the last-good value stays live). This is the offline
 * proof that a mod's config layer works without reinventing it.
 * RU: Прогоняет весь жизненный цикл {@link ConfigStore} на реальном temp-файле: запись defaults при
 * отсутствии файла; round-trip через JSON; ограничение значения через {@link ConfigStore#validate}; и горячую
 * перезагрузку по {@code WatchService} при правке файла, с локализацией битой правки.
 */
public final class ConfigSelfTest {

    /** A tiny faction config as a plain record (the mod's own type). */
    public record FactionConfig(String name, int maxMembers, double taxRate) {
        TreeNode toTree() {
            return Tree.object()
                    .put("name", name)
                    .put("maxMembers", maxMembers)
                    .put("taxRate", taxRate)
                    .build();
        }

        static FactionConfig fromTree(TreeNode node) {
            TreeNode.Obj o = (TreeNode.Obj) node;
            return new FactionConfig(o.getString("name", "Unnamed"),
                    (int) o.getLong("maxMembers", 10), o.getDouble("taxRate", 0.0));
        }
    }

    private static final ConfigStore.Codec<FactionConfig> CODEC = new ConfigStore.Codec<>() {
        @Override
        public TreeNode toTree(FactionConfig value) {
            return value.toTree();
        }

        @Override
        public FactionConfig fromTree(TreeNode tree) {
            return FactionConfig.fromTree(tree);
        }
    };

    private ConfigSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("aetherium-config-selftest");
            Path file = dir.resolve("faction.json");

            // 1) Open on a missing file → defaults are written to disk.
            FactionConfig defaults = new FactionConfig("Iron Vanguard", 20, 0.05);
            ConfigStore<FactionConfig> store = ConfigStore.open(file, CODEC, defaults)
                    // 3) validator: clamp maxMembers into [1, 50].
                    .validate(c -> new FactionConfig(c.name(), Math.max(1, Math.min(50, c.maxMembers())), c.taxRate()));
            boolean wroteDefaults = Files.isRegularFile(file) && store.get().equals(defaults);
            notes.add("open(missing) wrote defaults=" + wroteDefaults + " (" + Files.size(file) + " bytes)");

            // 2) Round-trip: set a value, re-open, compare.
            store.set(new FactionConfig("Steel Compact", 12, 0.1));
            ConfigStore<FactionConfig> reopened = ConfigStore.open(file, CODEC, defaults);
            boolean roundTrip = reopened.get().equals(new FactionConfig("Steel Compact", 12, 0.1));
            notes.add("round-trip through JSON equal=" + roundTrip + " (" + reopened.get() + ")");

            // 3) Validator clamps an out-of-range hand value on load.
            Files.writeString(file, TreeJson.write(new FactionConfig("Overfull", 9999, 0.2).toTree()),
                    StandardCharsets.UTF_8);
            store.reload();
            boolean clamped = store.get().maxMembers() == 50;
            notes.add("validator clamped maxMembers 9999 → " + store.get().maxMembers());

            // 4) Hot-reload: watch, edit the file, wait for the listener to fire with the new value.
            AtomicReference<FactionConfig> reloaded = new AtomicReference<>();
            store.onReload(reloaded::set).watch();
            Files.writeString(file, TreeJson.write(new FactionConfig("Live Edit", 7, 0.3).toTree()),
                    StandardCharsets.UTF_8);
            boolean hotReloaded = await(() -> reloaded.get() != null
                    && reloaded.get().equals(new FactionConfig("Live Edit", 7, 0.3)), 5000);
            notes.add("hot-reload fired=" + hotReloaded + " (saw " + reloaded.get() + ")");

            // 4b) A malformed edit is contained — the last-good value stays live, watcher survives.
            Files.writeString(file, "{ this is not valid json ", StandardCharsets.UTF_8);
            sleep(300);
            boolean containedBadEdit = store.get().equals(new FactionConfig("Live Edit", 7, 0.3));
            notes.add("malformed edit contained (kept last-good)=" + containedBadEdit);

            // 4c) A DIRECT reload() on the malformed file returns a failed result (never throws — ),
            //     with a diagnostic, and still keeps the last-good value.
            ConfigStore.ReloadResult direct = store.reload();
            boolean reloadResultOk = !direct.ok() && direct.diagnostic().isPresent()
                    && store.get().equals(new FactionConfig("Live Edit", 7, 0.3));
            notes.add("direct reload() on malformed: ok=" + direct.ok()
                    + ", code=" + direct.diagnostic().map(org.aetherium.core.Diagnostic::code).orElse("-"));

            store.close();
            reopened.close();

            boolean passed = wroteDefaults && roundTrip && clamped && hotReloaded && containedBadEdit
                    && reloadResultOk;
            return new Result(wroteDefaults, roundTrip, clamped, hotReloaded, containedBadEdit, reloadResultOk,
                    notes, passed);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(dir);
        }
    }

    /** Outcome of the config self-test. */
    public record Result(boolean wroteDefaults, boolean roundTrip, boolean validatorClamped,
                         boolean hotReloaded, boolean containedBadEdit, boolean reloadResultOk,
                         List<String> notes, boolean passed) {
    }

    private static boolean await(java.util.function.BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            sleep(50);
        }
        return condition.getAsBoolean();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
