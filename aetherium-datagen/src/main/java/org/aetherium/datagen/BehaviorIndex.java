/*
 * Aetherium Framework — runtime behavior index (processor → loader hand-off).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * The compile-time → run-time bridge for content <em>behaviors</em> — a flat, line-oriented index.
 *
 * <p>EN: The annotation processor serializes each {@link BehaviorEntry} into
 * {@code META-INF/aetherium/behaviors.index} (one pipe-delimited record per line), mirroring how
 * {@link ContentIndex} carries the content itself. At runtime the loader calls {@link #load(ClassLoader)},
 * reconstructs the entries, and for each machine-logic block registers a ticking {@code BlockEntity} — no
 * classpath scanning, no annotation reflection.
 * RU: Мост «компиляция → выполнение» для поведений контента — плоский построчный индекс. Процессор
 * сериализует каждый {@link BehaviorEntry} в {@code META-INF/aetherium/behaviors.index}, как
 * {@link ContentIndex} несёт сам контент. В рантайме загрузчик вызывает {@link #load(ClassLoader)} и для
 * каждого блока с machine-logic регистрирует тикающую {@code BlockEntity}.
 */
public final class BehaviorIndex {

    /** Classpath resource path written by the processor and read by the loader. */
    public static final String RESOURCE = "META-INF/aetherium/behaviors.index";

    private static final char SEP = '|';

    private BehaviorIndex() {
    }

    /** Serialize one entry to a single index line (no trailing newline). */
    public static String serialize(BehaviorEntry e) {
        return String.join(String.valueOf(SEP),
                e.kind().name(),
                esc(e.modId()),
                esc(e.ownerName()),
                esc(e.behaviorClass()),
                Boolean.toString(e.machineLogic()));
    }

    /** Parse one index line back into a {@link BehaviorEntry}, or {@code null} if blank/comment. */
    public static BehaviorEntry parse(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        String[] parts = trimmed.split("\\" + SEP, -1);
        if (parts.length < 5) {
            return null;
        }
        return new BehaviorEntry(
                ContentKind.valueOf(parts[0]),
                unesc(parts[1]),
                unesc(parts[2]),
                unesc(parts[3]),
                Boolean.parseBoolean(parts[4]));
    }

    /** Read every {@code behaviors.index} on the classpath and reconstruct the entries. */
    public static List<BehaviorEntry> load(ClassLoader loader) {
        List<BehaviorEntry> out = new ArrayList<>();
        try {
            Enumeration<URL> resources = loader.getResources(RESOURCE);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream in = url.openStream();
                     BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        BehaviorEntry e = parse(line);
                        if (e != null) {
                            out.add(e);
                        }
                    }
                }
            }
        } catch (IOException io) {
            throw new java.io.UncheckedIOException("could not read " + RESOURCE, io);
        }
        return out;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\|");
    }

    private static String unesc(String s) {
        return s.replace("\\|", "|").replace("\\\\", "\\");
    }
}
