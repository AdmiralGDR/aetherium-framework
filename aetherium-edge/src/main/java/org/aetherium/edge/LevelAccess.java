/*
 * Aetherium Framework — PAL level access (world lookup).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Loader-agnostic access to the active {@link LevelContext}s — the world-side sibling of
 * {@link EntityAccess}, reached via {@link PlatformBridge#levels()}.
 *
 * <p>EN: A server hosts several dimensions, so this enumerates them. {@link #primary()} returns the
 * overworld (the common case); {@link #byDimension(String)} resolves a specific one; {@link #forEach}
 * visits all loaded levels. Outside a running game the no-op bridge reports none — calls stay safe.
 *
 * <p>RU: Сервер содержит несколько измерений, поэтому здесь они перечисляются. {@link #primary()}
 * возвращает обычный мир (типичный случай); {@link #byDimension(String)} разрешает конкретное;
 * {@link #forEach} обходит все загруженные уровни. Вне игры no-op мост сообщает об отсутствии —
 * вызовы остаются безопасными.
 */
public interface LevelAccess {

    /** The primary (overworld) level, if a game/server is running. */
    Optional<LevelContext> primary();

    /** The level for the given dimension id (e.g. {@code "minecraft:the_nether"}), if loaded. */
    Optional<LevelContext> byDimension(String dimensionId);

    /** Visit every loaded level. */
    void forEach(Consumer<LevelContext> action);

    /** Number of loaded levels. */
    int count();
}
