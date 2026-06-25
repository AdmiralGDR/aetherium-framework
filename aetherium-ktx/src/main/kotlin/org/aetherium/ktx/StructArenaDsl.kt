/*
 * Aetherium Framework — Kotlin StructArena/StructLayout DSL (off-heap, zero-GC, zero-overhead).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
@file:JvmName("StructArenaDsl")

package org.aetherium.ktx

import org.aetherium.core.compute.StructArena
import org.aetherium.core.compute.StructField
import org.aetherium.core.compute.StructLayout

/**
 * EN: Build a [StructLayout] from a concise field block. `inline`, so it lowers to the same fluent
 * `StructLayout.builder()...build()` calls with no wrapper object retained.
 * RU: Строит [StructLayout] из лаконичного блока полей. `inline`, поэтому понижается к тем же
 * текучим вызовам `StructLayout.builder()...build()` без удержания объекта-обёртки.
 *
 * ```
 * val layout = structLayout { floats("x"); floats("vx") }
 * ```
 */
inline fun structLayout(block: StructLayoutScope.() -> Unit): StructLayout {
    val builder = StructLayout.builder()
    StructLayoutScope(builder).block()
    return builder.build()
}

/** Receiver for `structLayout { ... }`: declares fields in order with natural alignment. */
@AetheriumDsl
class StructLayoutScope @PublishedApi internal constructor(
    @PublishedApi internal val builder: StructLayout.Builder,
) {
    fun ints(name: String) {
        builder.ints(name)
    }

    fun longs(name: String) {
        builder.longs(name)
    }

    fun floats(name: String) {
        builder.floats(name)
    }

    fun doubles(name: String) {
        builder.doubles(name)
    }
}

/**
 * EN: Allocate a [StructArena] of [count] elements, run [block] against it, and deterministically free
 * it (`use`) even on exception. `inline` so neither the lambda nor a try/finally wrapper survives.
 * RU: Выделяет [StructArena] из [count] элементов, выполняет [block] над ним и детерминированно
 * освобождает (`use`) даже при исключении. `inline` — ни лямбда, ни обёртка try/finally не остаются.
 *
 * ```
 * structArena(layout, 4_096) {
 *     val x = field("x"); val vx = field("vx")
 *     for (i in 0 until count()) setFloat(i, x, getFloat(i, x) + getFloat(i, vx))
 * }
 * ```
 */
inline fun <R> structArena(layout: StructLayout, count: Long, block: StructArena.() -> R): R =
    StructArena.allocate(layout, count).use(block)

/** O(1) field lookup by name, off the arena's own layout (sugar for `layout().field(name)`). */
fun StructArena.field(name: String): StructField = layout().field(name)
