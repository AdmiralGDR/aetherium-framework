/*
 * Aetherium Framework — NeoForge level context (Block PAL implementation).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.aetherium.edge.BlockEntityAccess;
import org.aetherium.edge.BlockHandle;
import org.aetherium.edge.BlockPos;
import org.aetherium.edge.LevelContext;

import java.util.Optional;

/**
 * NeoForge-backed implementation of the loader-agnostic {@link LevelContext} — the world-side bridge of
 * the Block PAL.
 *
 * <p>EN: Wraps a {@code net.minecraft.world.level.Level} and routes the PAL's block queries/mutations to
 * the stable level API: {@code getBlockState}/{@code getBlockEntity}/{@code isLoaded}, placement via
 * {@code setBlockAndUpdate} (registry id parsed through {@code BuiltInRegistries.BLOCK}), and neighbour
 * updates via {@code updateNeighborsAt}. Mod code drives all of this with {@link BlockPos} and plain
 * strings — never a Minecraft type.
 *
 * <p>RU: Реализация {@link LevelContext} на базе NeoForge — мировая сторона Block PAL. Оборачивает
 * {@code net.minecraft.world.level.Level} и направляет запросы/изменения блоков PAL к стабильному API
 * уровня: {@code getBlockState}/{@code getBlockEntity}/{@code isLoaded}, установка через
 * {@code setBlockAndUpdate} (id реестра разбирается через {@code BuiltInRegistries.BLOCK}) и обновления
 * соседей через {@code updateNeighborsAt}. Код мода работает с {@link BlockPos} и строками — без типов
 * Minecraft.
 */
final class NeoForgeLevelContext implements LevelContext {

    private final Level level;

    NeoForgeLevelContext(Level level) {
        this.level = level;
    }

    private static net.minecraft.core.BlockPos mc(BlockPos pos) {
        return new net.minecraft.core.BlockPos(pos.x(), pos.y(), pos.z());
    }

    @Override
    public String dimension() {
        return level.dimension().location().toString();
    }

    @Override
    public boolean isClientSide() {
        return level.isClientSide();
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return level.isLoaded(mc(pos));
    }

    @Override
    public BlockHandle blockAt(BlockPos pos) {
        net.minecraft.core.BlockPos p = mc(pos);
        return new NeoForgeBlockHandle(level, p, level.getBlockState(p));
    }

    @Override
    public Optional<BlockEntityAccess> blockEntityAt(BlockPos pos) {
        BlockEntity be = level.getBlockEntity(mc(pos));
        return be == null
                ? Optional.empty()
                : Optional.of(new NeoForgeBlockEntityAccess(be, level.registryAccess()));
    }

    @Override
    public void setBlock(BlockPos pos, String blockId) {
        ResourceLocation id = ResourceLocation.parse(blockId);
        Block block = BuiltInRegistries.BLOCK.get(id);
        level.setBlockAndUpdate(mc(pos), block.defaultBlockState());
    }

    @Override
    public void scheduleNeighborUpdate(BlockPos pos) {
        net.minecraft.core.BlockPos p = mc(pos);
        level.updateNeighborsAt(p, level.getBlockState(p).getBlock());
    }
}
