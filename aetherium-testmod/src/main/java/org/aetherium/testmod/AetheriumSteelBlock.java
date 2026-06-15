/*
 * Aetherium Framework — declarative content E2E sample.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.content.AetheriumBlock;

/**
 * The entire definition of a new block — <strong>one annotation, zero JSON, zero registry code</strong>.
 *
 * <p>EN: This single declaration drives the whole pipeline: at build time the annotation processor
 * generates {@code models/block/steel_block.json}, {@code models/item/steel_block.json},
 * {@code blockstates/steel_block.json}, the self-drop {@code loot_table/blocks/steel_block.json}, and a
 * {@code lang/en_us.json} entry; at load time the loader registers the {@code Block} and its
 * {@code BlockItem} to the vanilla registries. The class body is intentionally empty — there is
 * nothing left for the modder to write. No {@code net.minecraft} import appears here.
 *
 * <p>RU: Всё определение нового блока — <strong>одна аннотация, ноль JSON, ноль кода реестра</strong>.
 * Эта декларация запускает весь конвейер: на сборке процессор генерирует модели блока и предмета,
 * blockstate, loot-таблицу самовыпадения и запись lang; на загрузке загрузчик регистрирует
 * {@code Block} и его {@code BlockItem} в ванильных реестрах. Тело класса намеренно пустое.
 */
@AetheriumBlock(name = "steel_block", modId = "aetherium", hardness = 5.0f, resistance = 6.0f,
        requiresTool = true, displayName = "Aetherium Steel Block")
public final class AetheriumSteelBlock {
}
