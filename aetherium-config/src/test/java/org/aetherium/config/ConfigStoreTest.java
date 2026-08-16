/*
 * Aetherium Framework — config store tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.config;

import org.aetherium.core.AetheriumException;
import org.aetherium.network.Tree;
import org.aetherium.network.TreeNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigStoreTest {

    @Test
    void fullLifecycle() {
        ConfigSelfTest.Result r = ConfigSelfTest.run();
        assertTrue(r.passed(), () -> "config self-test failed: " + r.notes());
        assertTrue(r.wroteDefaults());
        assertTrue(r.roundTrip());
        assertTrue(r.validatorClamped());
        assertTrue(r.hotReloaded());
        assertTrue(r.containedBadEdit());
        assertTrue(r.reloadResultOk());
    }

    @Test
    void jsonRoundTripThroughTree() {
        TreeNode tree = Tree.object()
                .put("name", "Iron Vanguard")
                .put("level", 7L)
                .put("rate", 0.25)
                .put("open", true)
                .put("members", Tree.list(Tree.of("Steve"), Tree.of("Alex")))
                .build();
        String json = TreeJson.write(tree);
        assertEquals(tree, TreeJson.parse(json), "JSON must round-trip byte-exact through TreeNode");
    }

    @Test
    void malformedJsonIsRejected() {
        assertThrows(AetheriumException.class, () -> TreeJson.parse("{ \"a\": }"));
        assertThrows(AetheriumException.class, () -> TreeJson.parse("[1,2,3")); // unterminated
        assertThrows(AetheriumException.class, () -> TreeJson.parse("{} garbage")); // trailing content
    }

    /**
     * {@code close()} is a hard barrier. After a watched store is closed, no {@code onReload}
     * listener may fire again — even a reload that still parses only updates the value, never dispatches. This
     * is the deterministic (race-free) half of the fix: the guard inside {@link ConfigStore#reload()} itself.
     * The consumer relies on it to hand ownership of the live state to a freshly-opened store without the old
     * one's late callback overwriting it.
     */
    @Test
    void closeIsAHardBarrier() throws IOException {
        Path dir = Files.createTempDirectory("aetherium-config-barrier");
        try {
            Path file = dir.resolve("faction.json");
            ConfigStore.Codec<ConfigSelfTest.FactionConfig> codec = new ConfigStore.Codec<>() {
                @Override
                public TreeNode toTree(ConfigSelfTest.FactionConfig v) {
                    return v.toTree();
                }

                @Override
                public ConfigSelfTest.FactionConfig fromTree(TreeNode t) {
                    return ConfigSelfTest.FactionConfig.fromTree(t);
                }
            };
            ConfigSelfTest.FactionConfig defaults = new ConfigSelfTest.FactionConfig("A", 10, 0.0);
            ConfigStore<ConfigSelfTest.FactionConfig> store = ConfigStore.open(file, codec, defaults);
            AtomicInteger hits = new AtomicInteger();
            store.onReload(v -> hits.incrementAndGet()).watch();

            // While running, a direct reload() dispatches to listeners.
            Files.writeString(file, TreeJson.write(new ConfigSelfTest.FactionConfig("B", 11, 0.0).toTree()));
            store.reload();
            int afterOpen = hits.get();
            assertTrue(afterOpen >= 1, "a reload while running must fire listeners");

            // close() is the barrier: after it returns, no listener fires again — by any path.
            store.close();
            Files.writeString(file, TreeJson.write(new ConfigSelfTest.FactionConfig("C", 12, 0.0).toTree()));
            ConfigStore.ReloadResult late = store.reload();
            assertTrue(late.ok(), "the late reload still parses (only dispatch is barred)");
            assertEquals(afterOpen, hits.get(), "close() is a hard barrier: no listener may fire after close()");
            assertEquals("C", store.get().name(), "a direct reload still updates the value; only dispatch is barred");
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort
                    }
                });
            }
        }
    }

    @Test
    void concurrentSavesNeverCorruptOrLeakTemps() throws Exception {
        // save() is public and unsynchronized. With a fixed "<name>.tmp" temp, concurrent writers interleave
        // their bytes into that one file and the second move throws NoSuchFile — a corrupt/lost config and a
        // spurious exception. A per-write unique temp makes every save land atomically and independently.
        Path dir = Files.createTempDirectory("aetherium-config-concurrent");
        try {
            Path file = dir.resolve("faction.json");
            ConfigStore.Codec<ConfigSelfTest.FactionConfig> codec = new ConfigStore.Codec<>() {
                @Override public TreeNode toTree(ConfigSelfTest.FactionConfig v) { return v.toTree(); }
                @Override public ConfigSelfTest.FactionConfig fromTree(TreeNode t) {
                    return ConfigSelfTest.FactionConfig.fromTree(t);
                }
            };
            ConfigStore<ConfigSelfTest.FactionConfig> store =
                    ConfigStore.open(file, codec, new ConfigSelfTest.FactionConfig("A", 1, 0.0));

            int threads = 8;
            int itersPerThread = 200;
            var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
            var errors = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
            var start = new java.util.concurrent.CountDownLatch(1);
            var done = new java.util.concurrent.CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                final int id = t;
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < itersPerThread; i++) {
                            store.set(new ConfigSelfTest.FactionConfig("F" + id, id * 1000 + i, 0.1));
                        }
                    } catch (Throwable e) {
                        errors.add(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown(); // release all writers at once for maximum contention
            assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS), "concurrent saves must finish");
            pool.shutdownNow();

            assertTrue(errors.isEmpty(), () -> "concurrent save() must never throw, but got: " + errors);

            // The persisted file must be one writer's COMPLETE value — never a torn or truncated temp.
            ConfigSelfTest.FactionConfig parsed = ConfigSelfTest.FactionConfig.fromTree(
                    TreeJson.parse(Files.readString(file)));
            assertTrue(parsed.name().startsWith("F"), "the persisted config must be a complete written value");

            // No unique temp may be left behind after all saves complete.
            try (var entries = Files.list(dir)) {
                assertTrue(entries.noneMatch(p -> p.getFileName().toString().contains(".tmp")),
                        "no .tmp file may leak after concurrent saves");
            }
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort
                    }
                });
            }
        }
    }

    @Test
    void deeplyNestedJsonIsRejectedNotStackOverflow() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < TreeJson.MAX_DEPTH + 50; i++) {
            deep.append("[");
        }
        assertThrows(AetheriumException.class, () -> TreeJson.parse(deep.toString()));
    }
}
