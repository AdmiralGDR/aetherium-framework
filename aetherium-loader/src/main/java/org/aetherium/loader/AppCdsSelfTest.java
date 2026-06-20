/*
 * Aetherium Framework — AppCDS zero-parse cache round-trip self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves the AppCDS transformed-class cache survives a launch boundary and self-invalidates.
 *
 * <p>EN: In a throwaway temp directory it (1) cold-looks-up a class (miss), (2) records transformed
 * bytes and flushes, (3) <em>reopens</em> the cache from disk — simulating a fresh JVM launch, which
 * {@code mmap}s the persisted blob — and asserts a hit returning the exact bytes (zero ASM parse), and
 * (4) looks up the same class with <em>different</em> original bytes and asserts a miss (hash-keyed
 * auto-invalidation when MC/NeoForge changes a class).
 *
 * <p>RU: Во временной директории: (1) холодный поиск класса (промах), (2) запись преобразованных байт и
 * сброс, (3) <em>повторное открытие</em> кэша с диска — имитация нового запуска JVM с {@code mmap}
 * сохранённого blob — и проверка попадания с точными байтами (ноль разбора ASM), (4) поиск того же
 * класса с <em>другими</em> исходными байтами и проверка промаха (инвалидация по хэшу).
 */
public final class AppCdsSelfTest {

    private AppCdsSelfTest() {
    }

    public record Result(boolean coldMiss, boolean warmHit, boolean staleInvalidated,
                         List<String> notes) {
        public boolean passed() {
            return coldMiss && warmHit && staleInvalidated;
        }
    }

    public static Result run() throws Exception {
        List<String> notes = new ArrayList<>();
        Path dir = Files.createTempDirectory("aetherium-cds-selftest");
        try {
            String name = "net/minecraft/world/entity/Entity";
            byte[] original = "ORIGINAL-CLASS-BYTES-v1".getBytes();
            byte[] transformed = "TRANSFORMED-PAYLOAD-aetherium".getBytes();

            AppCdsManager first = AppCdsManager.open(dir);
            boolean coldMiss = first.lookup(name, original) == null;
            first.record(name, original, transformed);
            first.flush();
            notes.add("run #1: cold lookup miss=" + coldMiss + ", recorded + flushed 1 class");

            // Simulate the NEXT launch: reopen from disk (mmaps the blob).
            AppCdsManager second = AppCdsManager.open(dir);
            byte[] hit = second.lookup(name, original);
            boolean warmHit = hit != null && new String(hit).equals("TRANSFORMED-PAYLOAD-aetherium");
            boolean staleInvalidated = second.lookup(name, "ORIGINAL-CLASS-BYTES-v2".getBytes()) == null;
            notes.add("run #2 (reopened, mmap'd): warm hit=" + warmHit
                    + " (zero ASM parse), stale-bytes invalidated=" + staleInvalidated);

            return new Result(coldMiss, warmHit, staleInvalidated, List.copyOf(notes));
        } finally {
            // best-effort cleanup
            try (var paths = Files.walk(dir)) {
                paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                     .forEach(p -> p.toFile().delete());
            } catch (Exception ignored) {
                // temp dir cleanup is best-effort
            }
        }
    }
}
