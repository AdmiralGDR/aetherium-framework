/*
 * Aetherium Framework — PAL block position (pure value type).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

/**
 * An immutable block coordinate — the loader-agnostic stand-in for {@code net.minecraft.core.BlockPos}.
 *
 * <p>EN: A pure {@code (x, y, z)} value with no Minecraft dependency, so optimization mods can address
 * blocks (and pass coordinates into off-heap compute) without importing a single game type. The small
 * navigation helpers ({@link #above()}, {@link #offset(int, int, int)}, …) cover the common neighbour
 * math the Block PAL needs.
 *
 * <p>RU: Неизменяемая координата {@code (x, y, z)} без зависимости от Minecraft, чтобы моды-оптимизаторы
 * адресовали блоки (и передавали координаты в off-heap вычисления) без импорта игровых типов.
 * Небольшие помощники навигации ({@link #above()}, {@link #offset(int, int, int)}, …) покрывают
 * типичную арифметику соседей, нужную Block PAL.
 *
 * @param x block X
 * @param y block Y
 * @param z block Z
 */
public record BlockPos(int x, int y, int z) {

    /** A new position offset by the given deltas. */
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    /** The position one block up (+Y). */
    public BlockPos above() {
        return new BlockPos(x, y + 1, z);
    }

    /** The position one block down (-Y). */
    public BlockPos below() {
        return new BlockPos(x, y - 1, z);
    }
}
