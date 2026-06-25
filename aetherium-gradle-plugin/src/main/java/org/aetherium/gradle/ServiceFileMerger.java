/*
 * Aetherium Framework — ServiceLoader manifest merge strategy.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gradle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges {@code META-INF/services/<service>} files from multiple sources by <strong>concatenation</strong>,
 * never overwrite — the correct strategy when several jars are flattened into one.
 *
 * <p>EN: When a fat/bundle jar flattens two jars that both contain {@code META-INF/services/com.x.Spi}, a
 * naive copy keeps only one file and silently drops the other's providers — `ServiceLoader` then misses
 * implementations. This merger unions all providers per service, deduplicating while preserving first-seen
 * order, and drops blank/comment lines. Pure (no Gradle types) so it is unit-testable.
 * RU: Когда fat/bundle-jar сплющивает два jar, оба содержащих {@code META-INF/services/com.x.Spi}, наивное
 * копирование оставляет один файл и молча теряет провайдеров другого. Этот объединитель собирает всех
 * провайдеров по сервису, дедуплицируя с сохранением порядка. Чистый, тестируемый.
 */
public final class ServiceFileMerger {

    private ServiceFileMerger() {
    }

    /**
     * Merge several {@code service → lines} maps into {@code service → merged file content}.
     *
     * @param sources one map per jar/output (service file name → its raw lines)
     * @return service file name → concatenated, deduplicated content (trailing newline)
     */
    public static Map<String, String> mergeAll(List<Map<String, List<String>>> sources) {
        Map<String, Set<String>> providers = new LinkedHashMap<>();
        for (Map<String, List<String>> source : sources) {
            for (Map.Entry<String, List<String>> e : source.entrySet()) {
                Set<String> set = providers.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>());
                for (String raw : e.getValue()) {
                    String line = stripComment(raw).trim();
                    if (!line.isEmpty()) {
                        set.add(line);
                    }
                }
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : providers.entrySet()) {
            out.put(e.getKey(), String.join("\n", new ArrayList<>(e.getValue())) + "\n");
        }
        return out;
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }
}
