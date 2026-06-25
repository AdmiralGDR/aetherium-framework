/*
 * Aetherium Framework — Kotlin DataGen content DSL (declarative blocks/items → resource JSON).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
@file:JvmName("DataGenDsl")

package org.aetherium.ktx

import org.aetherium.datagen.AssetGenerator
import org.aetherium.datagen.ContentEntry
import org.aetherium.datagen.ContentKind

/**
 * EN: Declare a mod's content in a concise Kotlin block and get back the [ContentEntry] list the
 * pure-Java [AssetGenerator] turns into resource/data-pack JSON. No Minecraft types, build-time only.
 * RU: Опишите контент мода в лаконичном Kotlin-блоке и получите список [ContentEntry], который
 * чистый Java-[AssetGenerator] превращает в JSON ресурс-/дата-пака. Без типов Minecraft, только сборка.
 *
 * ```
 * val files = content {
 *     block("mymod", "steel_block", hardness = 5.0f)
 *     item("mymod", "ruby")
 * }.generate()
 * ```
 */
inline fun content(block: ContentScope.() -> Unit): List<ContentEntry> {
    val scope = ContentScope()
    scope.block()
    return scope.entries
}

/** Receiver for `content { ... }`: accumulates declarative block/item entries. */
@AetheriumDsl
class ContentScope @PublishedApi internal constructor() {
    @PublishedApi internal val entries: MutableList<ContentEntry> = mutableListOf()

    /**
     * Declare a block. [resistance] < 0 inherits [hardness]; blank [displayName] is humanized from
     * [name] by the [ContentEntry] constructor.
     */
    fun block(
        modId: String,
        name: String,
        hardness: Float = 1.5f,
        resistance: Float = -1f,
        requiresTool: Boolean = true,
        dropSelf: Boolean = true,
        displayName: String = "",
    ) {
        entries += ContentEntry(
            ContentKind.BLOCK, modId, name, "",
            hardness, resistance, requiresTool, dropSelf, 64, displayName,
        )
    }

    /** Declare an item. Blank [displayName] is humanized from [name]. */
    fun item(
        modId: String,
        name: String,
        maxStackSize: Int = 64,
        displayName: String = "",
    ) {
        entries += ContentEntry(
            ContentKind.ITEM, modId, name, "",
            0f, -1f, false, false, maxStackSize, displayName,
        )
    }
}

/** Generate the resource-relative-path → file-content map for these entries (sugar for AssetGenerator). */
fun List<ContentEntry>.generate(): Map<String, String> = AssetGenerator.generate(this)
