/*
 * Aetherium Framework — runtime context handed to machine block-entity logic.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

/**
 * The per-tick context a {@link AetheriumMachineLogic} receives — loader-agnostic, no Minecraft type.
 *
 * <p>EN: Exposes the block-entity age, side (client/server), its block position, and its persistent
 * {@link MachineState}. The loader implements this over the real {@code BlockEntity}/{@code Level}. It is
 * intentionally minimal: enough to write smelting/energy/timer logic and unit-test it without the game.
 * RU: Предоставляет возраст блок-сущности, сторону (клиент/сервер), позицию блока и его сохраняемое
 * {@link MachineState}. Загрузчик реализует это поверх реального {@code BlockEntity}/{@code Level}.
 */
public interface MachineContext {

    /** Ticks since this block entity was created. */
    long ticks();

    /** True on the client side (skip authoritative simulation there). */
    boolean isClient();

    /** Block X. */
    int x();

    /** Block Y. */
    int y();

    /** Block Z. */
    int z();

    /** The block entity's persistent state. */
    MachineState state();
}
