/*
 * Aetherium Framework — runtime content index (processor → loader hand-off).
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
 * The compile-time → run-time bridge: a flat, line-oriented index of declarative content.
 *
 * <p>EN: The annotation processor serializes every {@link ContentEntry} into
 * {@code META-INF/aetherium/content.index} (one pipe-delimited record per line). At runtime the
 * loader calls {@link #load(ClassLoader)}, which reads <em>all</em> such resources on the classpath
 * (every Aetherium-built mod contributes one) and reconstructs the entries — no classpath scanning,
 * no reflection over annotations, no Minecraft types. Keeping the format here (in pure datagen) lets
 * both the writer (content processor) and the reader (loader) share one source of truth.
 *
 * <p>RU: Мост «время компиляции → время выполнения»: плоский построчный индекс контента. Процессор
 * сериализует каждый {@link ContentEntry} в {@code META-INF/aetherium/content.index} (одна запись с
 * разделителем «|» на строку). В рантайме загрузчик вызывает {@link #load(ClassLoader)}, читающий
 * <em>все</em> такие ресурсы на classpath и восстанавливающий записи — без сканирования classpath,
 * без рефлексии по аннотациям, без типов Minecraft.
 */
public final class ContentIndex {

    /** Classpath resource path written by the processor and read by the loader. */
    public static final String RESOURCE = "META-INF/aetherium/content.index";

    private static final char SEP = '|';

    private ContentIndex() {
    }

    /** Serialize one entry to a single index line (no trailing newline). */
    public static String serialize(ContentEntry e) {
        return String.join(String.valueOf(SEP),
                e.kind().name(),
                esc(e.modId()),
                esc(e.name()),
                esc(e.className()),
                Float.toString(e.hardness()),
                Float.toString(e.effectiveResistance()),
                Boolean.toString(e.requiresTool()),
                Boolean.toString(e.dropSelf()),
                Integer.toString(e.maxStackSize()),
                esc(e.displayName()));
    }

    /** Parse one index line back into a {@link ContentEntry}, or {@code null} if blank/comment. */
    public static ContentEntry parse(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        String[] f = splitEscaped(trimmed);
        if (f.length < 10) {
            throw new IllegalArgumentException("Malformed content.index line: " + line);
        }
        return new ContentEntry(
                ContentKind.valueOf(f[0]),
                f[1],
                f[2],
                f[3],
                Float.parseFloat(f[4]),
                Float.parseFloat(f[5]),
                Boolean.parseBoolean(f[6]),
                Boolean.parseBoolean(f[7]),
                Integer.parseInt(f[8]),
                f[9]);
    }

    /** Read and merge every {@code content.index} resource visible to {@code loader}. */
    public static List<ContentEntry> load(ClassLoader loader) {
        List<ContentEntry> entries = new ArrayList<>();
        try {
            Enumeration<URL> resources = loader.getResources(RESOURCE);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream in = url.openStream();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        ContentEntry e = parse(line);
                        if (e != null) {
                            entries.add(e);
                        }
                    }
                }
            }
        } catch (IOException io) {
            throw new java.io.UncheckedIOException("Failed to read " + RESOURCE, io);
        }
        return entries;
    }

    // --- field escaping (only '|', '\\' and newlines need escaping) ---------

    private static String esc(String v) {
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case SEP -> sb.append("\\p");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String[] splitEscaped(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char n = line.charAt(++i);
                switch (n) {
                    case '\\' -> cur.append('\\');
                    case 'p' -> cur.append(SEP);
                    case 'n' -> cur.append('\n');
                    case 'r' -> cur.append('\r');
                    default -> cur.append(n);
                }
            } else if (c == SEP) {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(String[]::new);
    }
}
