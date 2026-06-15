/*
 * Aetherium Framework — NeoForge entity handle (PAL implementation).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.world.entity.Entity;
import org.aetherium.edge.EntityHandle;

import java.util.UUID;

/**
 * The NeoForge-backed implementation of the loader-agnostic {@link EntityHandle}.
 *
 * <p>EN: Wraps a real {@code net.minecraft.world.entity.Entity}. This is the only side of the PAL
 * that touches Minecraft types — the {@code aetherium-edge} module stays pure. Reads use the stable
 * {@code getX/getY/getZ/getUUID}; writes use {@code setPos} and {@code setDeltaMovement}. Aetherium
 * mods push their off-heap-computed results back into the live world through this handle without
 * importing a single game type.
 *
 * <p>RU: Реализация независимого от загрузчика {@link EntityHandle} на базе NeoForge. Оборачивает
 * реальный {@code net.minecraft.world.entity.Entity}. Это единственная сторона PAL, касающаяся типов
 * Minecraft — модуль {@code aetherium-edge} остаётся чистым. Чтение — стабильные
 * {@code getX/getY/getZ/getUUID}; запись — {@code setPos} и {@code setDeltaMovement}.
 */
final class NeoForgeEntityHandle implements EntityHandle {

    private final Entity entity;

    NeoForgeEntityHandle(Entity entity) {
        this.entity = entity;
    }

    @Override
    public UUID id() {
        return entity.getUUID();
    }

    @Override
    public double x() {
        return entity.getX();
    }

    @Override
    public double y() {
        return entity.getY();
    }

    @Override
    public double z() {
        return entity.getZ();
    }

    @Override
    public void setPosition(double x, double y, double z) {
        entity.setPos(x, y, z);
    }

    @Override
    public void addVelocity(double dx, double dy, double dz) {
        entity.setDeltaMovement(entity.getDeltaMovement().add(dx, dy, dz));
    }
}
