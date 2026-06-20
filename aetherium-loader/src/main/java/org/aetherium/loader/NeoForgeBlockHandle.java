/*
 * Aetherium Framework — NeoForge block handle (Block PAL implementation).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.aetherium.edge.BlockHandle;
import org.aetherium.edge.BlockPos;

import java.util.Optional;

/**
 * NeoForge-backed implementation of the loader-agnostic {@link BlockHandle}.
 *
 * <p>EN: Wraps a {@code (Level, BlockPos, BlockState)} triple and translates the block-state facts a
 * mod queries into plain strings/values — the registry id via {@code BuiltInRegistries.BLOCK}, the
 * hardness via {@code getDestroySpeed}, and block-state properties by their string name. Like
 * {@link NeoForgeEntityHandle}, this is one of the few places that touches Minecraft types so the
 * {@code aetherium-edge} module stays pure.
 *
 * <p>RU: Реализация {@link BlockHandle} на базе NeoForge. Оборачивает тройку
 * {@code (Level, BlockPos, BlockState)} и переводит факты состояния блока в простые строки/значения —
 * id реестра через {@code BuiltInRegistries.BLOCK}, твёрдость через {@code getDestroySpeed}, свойства
 * состояния по строковому имени. Одно из немногих мест, касающихся типов Minecraft, чтобы модуль
 * {@code aetherium-edge} оставался чистым.
 */
final class NeoForgeBlockHandle implements BlockHandle {

    private final Level level;
    private final net.minecraft.core.BlockPos mcPos;
    private final BlockState state;

    NeoForgeBlockHandle(Level level, net.minecraft.core.BlockPos mcPos, BlockState state) {
        this.level = level;
        this.mcPos = mcPos;
        this.state = state;
    }

    @Override
    public BlockPos pos() {
        return new BlockPos(mcPos.getX(), mcPos.getY(), mcPos.getZ());
    }

    @Override
    public String blockId() {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    @Override
    public boolean isAir() {
        return state.isAir();
    }

    @Override
    public float destroySpeed() {
        return state.getDestroySpeed(level, mcPos);
    }

    @Override
    public Optional<String> property(String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return Optional.of(valueAsString(property));
            }
        }
        return Optional.empty();
    }

    /** Resolve a property's current value to its canonical string (e.g. {@code "north"}). */
    private <T extends Comparable<T>> String valueAsString(Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
