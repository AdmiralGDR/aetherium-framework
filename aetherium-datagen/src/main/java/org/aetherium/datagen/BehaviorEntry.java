/*
 * Aetherium Framework — a declarative content-behavior descriptor.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

import java.util.Objects;

/**
 * One {@code @AetheriumBlock/@AetheriumItem(behavior = …)} binding — the compile-time → run-time record
 * that tells the loader to wire a behavior class to a piece of content.
 *
 * <p>EN: Pure data (no Minecraft type). When {@link #machineLogic()} is true and the owner is a block, the
 * loader auto-registers a ticking {@code BlockEntity} driven by the behavior's
 * {@code AetheriumMachineLogic.tick}. Carried in the behavior index alongside the asset index.
 * RU: Чистые данные (без типов Minecraft). Когда {@link #machineLogic()} истинно и владелец — блок,
 * загрузчик авто-регистрирует тикающую {@code BlockEntity}, управляемую {@code AetheriumMachineLogic.tick}.
 *
 * @param kind          BLOCK or ITEM (the owning content kind)
 * @param modId         owning mod id
 * @param ownerName     registry path of the block/item
 * @param behaviorClass fully-qualified behavior class
 * @param machineLogic  whether {@code behaviorClass} implements {@code AetheriumMachineLogic}
 */
public record BehaviorEntry(ContentKind kind, String modId, String ownerName,
                            String behaviorClass, boolean machineLogic) {

    public BehaviorEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(behaviorClass, "behaviorClass");
    }
}
