/*
 * Aetherium Framework — PAL entity access.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Loader-agnostic access to the world's entities.
 *
 * <p>EN: Lets an Aetherium mod look up and iterate vanilla entities as {@link EntityHandle}s — to
 * read state into off-heap compute and write results back — without touching the loader's world/level
 * API. The loader provides the concrete implementation backed by the running server/level.
 *
 * <p>RU: Позволяет моду Aetherium находить и обходить ванильные сущности как {@link EntityHandle} —
 * чтобы читать состояние в off-heap вычисления и записывать результаты обратно — не касаясь API
 * мира/уровня загрузчика. Загрузчик предоставляет конкретную реализацию на базе работающего
 * сервера/уровня.
 */
public interface EntityAccess {

    /** Find an entity by UUID, if present in the active world. */
    Optional<EntityHandle> byId(UUID id);

    /** Visit every loaded entity (main thread). */
    void forEach(Consumer<EntityHandle> action);

    /** Number of loaded entities. */
    int count();
}
