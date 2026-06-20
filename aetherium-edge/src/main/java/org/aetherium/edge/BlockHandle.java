/*
 * Aetherium Framework — PAL block handle.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.Optional;

/**
 * A loader-agnostic, read-only view of one block (its state at a position) — the block analogue of
 * {@link EntityHandle}.
 *
 * <p>EN: Exposes the block-state facts an optimization mod actually queries — the block's registry id,
 * whether it is air, its position, and individual block-state properties (e.g. {@code "facing"}) — as
 * plain strings/values, so no {@code net.minecraft} {@code Block}/{@code BlockState} type leaks into mod
 * code. Mutation goes through {@link LevelContext#setBlock(BlockPos, String)}; this handle is a snapshot
 * for reading into compute.
 *
 * <p>RU: Только для чтения; раскрывает факты состояния блока, которые реально запрашивает
 * мод-оптимизатор — id блока в реестре, является ли он воздухом, его позицию и отдельные свойства
 * состояния (напр. {@code "facing"}) — как простые строки/значения, так что ни один тип
 * {@code net.minecraft} {@code Block}/{@code BlockState} не протекает в код мода. Изменение — через
 * {@link LevelContext#setBlock(BlockPos, String)}; этот хэндл — снимок для чтения.
 */
public interface BlockHandle {

    /** Position of this block in its level. */
    BlockPos pos();

    /** The block's registry id, e.g. {@code "minecraft:stone"}. */
    String blockId();

    /** True if this position holds air (the most common hot-path check). */
    boolean isAir();

    /** The block's destroy speed (hardness); {@code -1} for unbreakable. */
    float destroySpeed();

    /** A block-state property value by name (e.g. {@code "facing"} → {@code "north"}), if present. */
    Optional<String> property(String name);
}
