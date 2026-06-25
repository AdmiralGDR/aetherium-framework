/*
 * Aetherium Framework — block-entity behavior contract (auto-wired BlockEntity + ticker).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

/**
 * Behavior for a "machine" block — implement this on a {@code @AetheriumBlock(behavior = …)} class and
 * the framework auto-registers a ticking {@code BlockEntity} for it.
 *
 * <p>EN: Normally a machine block needs a {@code BlockEntityType}, a {@code BlockEntity} subclass, a
 * registered ticker, and NBT save/load — pages of boilerplate. With Aetherium, a block's
 * {@link AetheriumBlock#behavior()} class implements this interface; the annotation processor records it
 * (see the behavior index) and the loader, at registration time, creates the {@code BlockEntityType},
 * binds a server ticker that calls {@link #tick(MachineContext)}, and persists the {@link MachineState}.
 * The logic itself is pure ({@link MachineContext} carries no Minecraft type), so it is unit-testable.
 * RU: Обычно блок-машина требует {@code BlockEntityType}, подкласс {@code BlockEntity},
 * зарегистрированный тикер и NBT save/load — страницы шаблона. С Aetherium класс
 * {@link AetheriumBlock#behavior()} реализует этот интерфейс; процессор записывает его (см. индекс
 * поведений), а загрузчик при регистрации создаёт {@code BlockEntityType}, привязывает серверный тикер,
 * вызывающий {@link #tick(MachineContext)}, и сохраняет {@link MachineState}. Логика чистая и тестируемая.
 */
public interface AetheriumMachineLogic {

    /** Called every server tick for each placed instance of the owning block. */
    void tick(MachineContext ctx);

    /** Called once when the block is placed (default: nothing). */
    default void onPlaced(MachineContext ctx) {
        // default: no-op
    }

    /** Called once when the block is removed (default: nothing). */
    default void onRemoved(MachineContext ctx) {
        // default: no-op
    }
}
