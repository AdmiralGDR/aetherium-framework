/*
 * Aetherium Framework — NeoForge block-entity access (Block PAL implementation).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.aetherium.edge.BlockEntityAccess;
import org.aetherium.edge.BlockPos;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * NeoForge-backed implementation of the loader-agnostic {@link BlockEntityAccess}.
 *
 * <p>EN: Bridges the PAL's typed key/value surface onto the platform's NBT. The block entity's tag is
 * materialized lazily (and once) via {@code saveWithoutMetadata}; reads consult that snapshot, and a
 * write mutates it and pushes it back through {@code loadWithComponents} + {@code setChanged()} so the
 * live world reflects the change. The {@code HolderLookup.Provider} (the level's registry access) is
 * required by 1.21's NBT API and supplied by {@link NeoForgeLevelContext}. No {@code CompoundTag} or
 * {@code BlockEntity} type ever escapes to mod code.
 *
 * <p>RU: Связывает типизированную поверхность ключ/значение PAL с NBT платформы. Тег блок-сущности
 * материализуется лениво (и однократно) через {@code saveWithoutMetadata}; чтения обращаются к этому
 * снимку, а запись изменяет его и возвращает через {@code loadWithComponents} + {@code setChanged()},
 * чтобы живой мир отразил изменение. {@code HolderLookup.Provider} (доступ к реестрам уровня) требуется
 * API NBT версии 1.21. Ни один тип {@code CompoundTag}/{@code BlockEntity} не утекает в код мода.
 */
final class NeoForgeBlockEntityAccess implements BlockEntityAccess {

    private final BlockEntity blockEntity;
    private final HolderLookup.Provider registries;
    private CompoundTag tag;

    NeoForgeBlockEntityAccess(BlockEntity blockEntity, HolderLookup.Provider registries) {
        this.blockEntity = blockEntity;
        this.registries = registries;
    }

    private CompoundTag tag() {
        if (tag == null) {
            tag = blockEntity.saveWithoutMetadata(registries);
        }
        return tag;
    }

    @Override
    public BlockPos pos() {
        net.minecraft.core.BlockPos p = blockEntity.getBlockPos();
        return new BlockPos(p.getX(), p.getY(), p.getZ());
    }

    @Override
    public String typeId() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString();
    }

    @Override
    public OptionalInt readInt(String key) {
        return tag().contains(key) ? OptionalInt.of(tag().getInt(key)) : OptionalInt.empty();
    }

    @Override
    public OptionalLong readLong(String key) {
        return tag().contains(key) ? OptionalLong.of(tag().getLong(key)) : OptionalLong.empty();
    }

    @Override
    public Optional<String> readString(String key) {
        return tag().contains(key) ? Optional.of(tag().getString(key)) : Optional.empty();
    }

    @Override
    public void writeInt(String key, int value) {
        tag().putInt(key, value);
        flush();
    }

    @Override
    public void writeLong(String key, long value) {
        tag().putLong(key, value);
        flush();
    }

    @Override
    public void writeString(String key, String value) {
        tag().putString(key, value);
        flush();
    }

    /** Push the mutated tag back into the live block entity and mark it dirty for save/sync. */
    private void flush() {
        blockEntity.loadWithComponents(tag(), registries);
        blockEntity.setChanged();
    }
}
