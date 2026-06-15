/*
 * Aetherium Framework — PAL event hooks.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.function.Consumer;

/**
 * Loader-agnostic lifecycle/event hooks — the cross-loader event surface.
 *
 * <p>EN: A minimal, stable set of hooks an Aetherium mod registers against, instead of NeoForge's
 * {@code @SubscribeEvent} or Fabric's callbacks. The loader maps each hook onto its native event
 * system. {@code onServerTickEnd} is the natural place to commit Aetherium's parallel-tick results
 * back to the game (after the Sync Barrier).
 *
 * <p>RU: Минимальный стабильный набор хуков, на которые подписывается мод Aetherium, вместо
 * {@code @SubscribeEvent} NeoForge или колбэков Fabric. Загрузчик отображает каждый хук на свою
 * нативную систему событий. {@code onServerTickEnd} — естественное место для коммита результатов
 * параллельного тика Aetherium обратно в игру (после Sync-барьера).
 */
public interface EdgeEvents {

    /** Run {@code hook} at the end of every server tick (after Aetherium's Sync Barrier). */
    void onServerTickEnd(Runnable hook);

    /** Run {@code hook} when an entity is loaded into the world. */
    void onEntityLoad(Consumer<EntityHandle> hook);
}
