/*
 * Aetherium Framework — Kotlin extensions over the gameplay PAL (LevelContext / BlockPos parity).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
@file:JvmName("LevelDsl")

package org.aetherium.ktx

import org.aetherium.edge.BlockEntityAccess
import org.aetherium.edge.BlockHandle
import org.aetherium.edge.BlockPos
import org.aetherium.edge.LevelContext

/**
 * EN: Idiomatic Kotlin over the loader-agnostic level PAL: index into a level with `level[pos]`,
 * destructure a [BlockPos] as `(x, y, z)`, shift positions with `pos + (0, 1, 0)`, and read block
 * entities as nullable values instead of `Optional`. Every extension lowers to the exact same PAL
 * call — no extra abstraction layer, no reflection.
 * RU: Идиоматичный Kotlin над загрузчик-независимым PAL уровня: индексирование `level[pos]`,
 * деструктуризация [BlockPos] как `(x, y, z)`, сдвиг позиций `pos + (0, 1, 0)` и чтение блок-сущностей
 * как nullable-значений вместо `Optional`. Каждое расширение понижается к тому же вызову PAL — без
 * лишнего слоя абстракции и рефлексии.
 */

/** `level[pos]` — the block handle at [pos] (same as [LevelContext.blockAt]). */
operator fun LevelContext.get(pos: BlockPos): BlockHandle = blockAt(pos)

/** `level[x, y, z]` — the block handle at the given coordinates. */
operator fun LevelContext.get(x: Int, y: Int, z: Int): BlockHandle = blockAt(BlockPos(x, y, z))

/** `level[pos] = "minecraft:stone"` — place a block by registry id (same as [LevelContext.setBlock]). */
operator fun LevelContext.set(pos: BlockPos, blockId: String) = setBlock(pos, blockId)

/** `level[x, y, z] = "minecraft:stone"` — place a block at the given coordinates. */
operator fun LevelContext.set(x: Int, y: Int, z: Int, blockId: String) = setBlock(BlockPos(x, y, z), blockId)

/** The block entity at [pos], or `null` — `Optional`-free reading. */
fun LevelContext.blockEntityOrNull(pos: BlockPos): BlockEntityAccess? = blockEntityAt(pos).orElse(null)

// --- BlockPos ergonomics ----------------------------------------------------------------------------

/** Destructuring: `val (x, y, z) = pos`. */
operator fun BlockPos.component1(): Int = x()
operator fun BlockPos.component2(): Int = y()
operator fun BlockPos.component3(): Int = z()

/** `pos + Triple(dx, dy, dz)` — offset a position. */
operator fun BlockPos.plus(delta: Triple<Int, Int, Int>): BlockPos =
    offset(delta.first, delta.second, delta.third)

/** A position literal: `blockPos(1, 64, -3)`. */
fun blockPos(x: Int, y: Int, z: Int): BlockPos = BlockPos(x, y, z)
