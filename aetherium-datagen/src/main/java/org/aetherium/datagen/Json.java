/*
 * Aetherium Framework — minimal JSON escaping (zero-dependency).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

/**
 * Tiny JSON string-escaper so the DataGen engine needs no external JSON library.
 *
 * <p>EN: The generated documents have a fixed, known shape (templated in {@link AssetGenerator}), so
 * the only untrusted text is the display name — this escapes it per RFC 8259. Keeping datagen
 * dependency-free preserves its strict purity and offline build.
 *
 * <p>RU: Генерируемые документы имеют фиксированную, известную форму (шаблоны в
 * {@link AssetGenerator}), поэтому единственный недоверенный текст — отображаемое имя; он
 * экранируется по RFC 8259. Отсутствие зависимостей сохраняет строгую чистоту datagen и офлайн-сборку.
 */
final class Json {

    private Json() {
    }

    static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
