/*
 * Aetherium Framework — PAL level (world) context.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.Optional;

/**
 * A loader-agnostic view of one level/world — the block-and-world analogue of {@link EntityAccess}.
 *
 * <p>EN: The single surface through which an optimization mod queries and mutates the world without
 * importing {@code net.minecraft.world.level.Level}: look up a {@link BlockHandle} or
 * {@link BlockEntityAccess} at a {@link BlockPos}, place a block by registry id, test chunk loadedness
 * (vital for thread-safe off-heap reads), and schedule the neighbour/redstone updates a write implies.
 * Reads happen on the main thread or against a loaded snapshot; writes happen during the commit phase.
 *
 * <p>RU: Единая поверхность, через которую мод-оптимизатор запрашивает и изменяет мир без импорта
 * {@code net.minecraft.world.level.Level}: найти {@link BlockHandle} или {@link BlockEntityAccess} в
 * {@link BlockPos}, поставить блок по id реестра, проверить загруженность чанка (важно для
 * потокобезопасного off-heap чтения) и запланировать обновления соседей/редстоуна, подразумеваемые
 * записью. Чтения — на главном потоке или по загруженному снимку; записи — на фазе commit.
 */
public interface LevelContext {

    /** The level's dimension id, e.g. {@code "minecraft:overworld"}. */
    String dimension();

    /** True on a client (render/logical) level, false on a server level. */
    boolean isClientSide();

    /** Whether the chunk containing {@code pos} is loaded (read this before touching the block). */
    boolean isLoaded(BlockPos pos);

    /** A read-only handle to the block at {@code pos} (air handle if unloaded/empty). */
    BlockHandle blockAt(BlockPos pos);

    /** The block entity at {@code pos}, if one exists there. */
    Optional<BlockEntityAccess> blockEntityAt(BlockPos pos);

    /** Place the block with the given registry id (e.g. {@code "minecraft:stone"}) at {@code pos}. */
    void setBlock(BlockPos pos, String blockId);

    /** Schedule the neighbour/redstone updates implied by a change at {@code pos}. */
    void scheduleNeighborUpdate(BlockPos pos);
}
