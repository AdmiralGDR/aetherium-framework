/*
 * Aetherium Framework — declarative content descriptor.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

import java.util.Locale;
import java.util.Objects;

/**
 * A single piece of declarative content, fully described without any Minecraft type.
 *
 * <p>EN: This is the contract between the three sides of the content pipeline: the annotation
 * processor (builds entries from {@code @AetheriumBlock}/{@code @AetheriumItem}), the DataGen engine
 * ({@link AssetGenerator}, turns entries into JSON), and the loader (registers entries to the vanilla
 * registries). It carries only primitives/strings, so it crosses the "no Minecraft in datagen" line
 * cleanly. {@code resistance < 0} means "use {@code hardness}". {@code dropSelf} controls loot-table
 * generation. {@code displayName} is the human label (auto-derived from {@code name} when blank).
 *
 * <p>RU: Контракт между тремя сторонами конвейера контента: аннотационный процессор (строит записи
 * из {@code @AetheriumBlock}/{@code @AetheriumItem}), движок DataGen ({@link AssetGenerator},
 * превращает записи в JSON) и загрузчик (регистрирует записи в ванильных реестрах). Содержит только
 * примитивы/строки. {@code resistance < 0} означает «использовать {@code hardness}». {@code dropSelf}
 * управляет генерацией loot-таблицы. {@code displayName} — человекочитаемая метка (выводится из
 * {@code name}, если пусто).
 *
 * @param kind         block or item
 * @param modId        owning mod id (registry namespace)
 * @param name         registry path (e.g. {@code "steel_block"})
 * @param className    fully-qualified annotated class (diagnostics / round-trip only)
 * @param hardness     destroy time (blocks); ignored for items
 * @param resistance   blast resistance (blocks); {@code < 0} → equal to {@code hardness}
 * @param requiresTool whether the block requires the correct tool to drop
 * @param dropSelf     whether the block drops itself (generate a loot table)
 * @param maxStackSize item max stack size; ignored for blocks
 * @param displayName  human-readable label for the lang file
 */
public record ContentEntry(
        ContentKind kind,
        String modId,
        String name,
        String className,
        float hardness,
        float resistance,
        boolean requiresTool,
        boolean dropSelf,
        int maxStackSize,
        String displayName) {

    public ContentEntry {
        Objects.requireNonNull(kind, "kind");
        modId = requireId(modId, "modId");
        name = requireId(name, "name");
        className = className == null ? "" : className;
        if (resistance < 0) {
            resistance = hardness;
        }
        if (maxStackSize <= 0) {
            maxStackSize = 64;
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = humanize(name);
        }
    }

    /** Effective blast resistance (already normalized to {@code hardness} when unset). */
    public float effectiveResistance() {
        return resistance;
    }

    private static String requireId(String value, String label) {
        Objects.requireNonNull(value, label);
        String v = value.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return v;
    }

    /** {@code "steel_block"} → {@code "Steel Block"}. */
    static String humanize(String id) {
        String[] parts = id.split("[_/]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)))
              .append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? id : sb.toString();
    }
}
