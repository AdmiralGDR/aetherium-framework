/*
 * Aetherium Framework — runtime context handed to machine block-entity logic.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import org.aetherium.edge.LevelContext;
import org.aetherium.edge.PlayerHandle;

import java.util.Optional;

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

    /**
     * The player who placed this machine, if known (). Recording the owner is the single most
     * common thing a placed machine needs, so it belongs on the context. Populated in {@code onPlaced} (and
     * carried afterwards where the loader can recover it); {@link Optional#empty()} on the client, for
     * naturally-generated blocks, or when the placer is otherwise unknown. Default empty so existing impls and
     * unit tests need no change.
     */
    default Optional<PlayerHandle> placer() {
        return Optional.empty();
    }

    /**
     * The world this machine sits in (), so it can read/mutate its surroundings — neighbouring
     * blocks, chunk loadedness, block entities — without re-deriving the position it was just handed via
     * {@link #x()}/{@link #y()}/{@link #z()}. The loader fills it from the block entity's level; default
     * {@link Optional#empty()} so existing impls and unit tests need no change.
     */
    default Optional<LevelContext> level() {
        return Optional.empty();
    }
}
