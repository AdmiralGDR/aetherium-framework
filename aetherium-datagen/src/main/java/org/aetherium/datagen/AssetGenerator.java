/*
 * Aetherium Framework — autonomous asset (JSON) generator.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns declarative {@link ContentEntry} records into the raw resource-pack/data-pack JSON that
 * vanilla Minecraft loads — the autonomous "DataGen Engine".
 *
 * <p>EN: This is the module that <em>eliminates JSON Hell</em>. For one {@code @AetheriumBlock} it
 * emits the block model ({@code cube_all}), the item model (parents the block model), the blockstate,
 * and — when the block drops itself — the loot table; for one {@code @AetheriumItem} it emits the
 * {@code item/generated} model. Lang entries for every piece are merged into a single
 * {@code lang/en_us.json} per mod id. <strong>Strictly pure</strong>: no Minecraft/NeoForge types, no
 * external JSON library, runs entirely at build time. Output is a map of resource-relative path →
 * file content, which the annotation processor writes via the {@code Filer}, or which
 * {@link #writeAll(List, Path)} writes straight to disk.
 *
 * <p>Loot-table directory note: Minecraft 1.21 renamed the data-pack folder from {@code loot_tables}
 * to the singular {@code loot_table}; this generator targets the 1.21.1 baseline and emits the
 * singular form.
 *
 * <p>RU: Модуль, который <em>устраняет JSON-ад</em>. Для одного {@code @AetheriumBlock} создаёт модель
 * блока ({@code cube_all}), модель предмета (наследует модель блока), blockstate и — если блок выпадает
 * сам — loot-таблицу; для одного {@code @AetheriumItem} — модель {@code item/generated}. Записи lang
 * объединяются в один {@code lang/en_us.json} на mod id. <strong>Строго чистый</strong>: без типов
 * Minecraft/NeoForge, без внешних JSON-библиотек, работает целиком на этапе сборки. Вывод — карта
 * «относительный путь ресурса → содержимое файла».
 */
public final class AssetGenerator {

    private AssetGenerator() {
    }

    /**
     * Generate every resource file for the given entries.
     *
     * @return ordered map of resource-relative path (forward slashes) → UTF-8 file content
     */
    public static Map<String, String> generate(List<ContentEntry> entries) {
        Map<String, String> out = new LinkedHashMap<>();
        // Lang is accumulated per mod id and emitted once at the end (sorted for determinism).
        Map<String, Map<String, String>> langByMod = new LinkedHashMap<>();

        for (ContentEntry e : entries) {
            String mod = e.modId();
            String name = e.name();
            switch (e.kind()) {
                case BLOCK -> {
                    out.put(assetPath(mod, "models/block/" + name + ".json"), blockModel(mod, name));
                    out.put(assetPath(mod, "models/item/" + name + ".json"), blockItemModel(mod, name));
                    out.put(assetPath(mod, "blockstates/" + name + ".json"), blockState(mod, name));
                    if (e.dropSelf()) {
                        out.put(dataPath(mod, "loot_table/blocks/" + name + ".json"),
                                blockLootTable(mod, name));
                    }
                    langByMod.computeIfAbsent(mod, k -> new TreeMap<>())
                            .put("block." + mod + "." + name, e.displayName());
                }
                case ITEM -> {
                    out.put(assetPath(mod, "models/item/" + name + ".json"), simpleItemModel(mod, name));
                    langByMod.computeIfAbsent(mod, k -> new TreeMap<>())
                            .put("item." + mod + "." + name, e.displayName());
                }
            }
        }

        // every mod that declares content gets an auto creative tab titled by this key, so give
        // it a sensible default translation ("mymod" -> "Mymod") instead of showing the raw key in game.
        langByMod.forEach((mod, map) -> map.put("itemGroup." + mod, humanize(mod)));

        langByMod.forEach((mod, map) ->
                out.put(assetPath(mod, "lang/en_us.json"), langFile(map)));
        return out;
    }

    /** Turn a mod id like {@code "red_steel_core"} into a display title {@code "Red Steel Core"}. */
    private static String humanize(String modId) {
        String[] parts = modId.replace('-', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? modId : sb.toString();
    }

    /** Generate and write every resource file under {@code outputRoot} (build-time disk sink). */
    public static List<Path> writeAll(List<ContentEntry> entries, Path outputRoot) {
        Map<String, String> files = generate(entries);
        try {
            java.util.List<Path> written = new java.util.ArrayList<>(files.size());
            for (Map.Entry<String, String> f : files.entrySet()) {
                Path target = outputRoot.resolve(f.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, f.getValue(), StandardCharsets.UTF_8);
                written.add(target);
            }
            return written;
        } catch (IOException io) {
            throw new UncheckedIOException("Failed to write generated assets under " + outputRoot, io);
        }
    }

    // --- path helpers -------------------------------------------------------

    private static String assetPath(String mod, String rel) {
        return "assets/" + mod + "/" + rel;
    }

    private static String dataPath(String mod, String rel) {
        return "data/" + mod + "/" + rel;
    }

    // --- JSON templates -----------------------------------------------------

    private static String blockModel(String mod, String name) {
        return """
                {
                  "parent": "minecraft:block/cube_all",
                  "textures": {
                    "all": "%s:block/%s"
                  }
                }
                """.formatted(mod, name);
    }

    private static String blockItemModel(String mod, String name) {
        return """
                {
                  "parent": "%s:block/%s"
                }
                """.formatted(mod, name);
    }

    private static String simpleItemModel(String mod, String name) {
        return """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "%s:item/%s"
                  }
                }
                """.formatted(mod, name);
    }

    private static String blockState(String mod, String name) {
        return """
                {
                  "variants": {
                    "": { "model": "%s:block/%s" }
                  }
                }
                """.formatted(mod, name);
    }

    private static String blockLootTable(String mod, String name) {
        return """
                {
                  "type": "minecraft:block",
                  "pools": [
                    {
                      "rolls": 1.0,
                      "bonus_rolls": 0.0,
                      "entries": [
                        { "type": "minecraft:item", "name": "%s:%s" }
                      ],
                      "conditions": [
                        { "condition": "minecraft:survives_explosion" }
                      ]
                    }
                  ]
                }
                """.formatted(mod, name);
    }

    private static String langFile(Map<String, String> entries) {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        int last = entries.size() - 1;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            sb.append("  \"").append(Json.escape(e.getKey())).append("\": \"")
              .append(Json.escape(e.getValue())).append('"');
            sb.append(i++ == last ? "\n" : ",\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
