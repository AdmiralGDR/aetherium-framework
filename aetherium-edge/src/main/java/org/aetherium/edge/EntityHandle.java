/*
 * Aetherium Framework — PAL entity handle.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.UUID;

/**
 * A loader-agnostic handle to a vanilla Minecraft entity — the "edge" where computed results land.
 *
 * <p>EN: Aetherium computes off-heap (e.g. in a {@code StructArena}); this is how those results are
 * pushed <em>back</em> into the live game without the mod importing a single {@code net.minecraft}
 * type. The loader's platform bridge implements this over the real entity. Methods cover the common
 * 99%: identity and position/velocity. Read on the main thread; write during the commit phase.
 *
 * <p>RU: Aetherium вычисляет off-heap (напр. в {@code StructArena}); это способ протолкнуть
 * результаты <em>обратно</em> в живую игру, не импортируя ни одного типа {@code net.minecraft}.
 * Мост платформы загрузчика реализует это поверх реальной сущности. Методы покрывают типичные 99%:
 * идентичность и позиция/скорость. Чтение — на главном потоке; запись — на фазе commit.
 */
public interface EntityHandle {

    /** The entity's stable UUID. */
    UUID id();

    double x();

    double y();

    double z();

    /** Teleport/move the entity to an absolute position. */
    void setPosition(double x, double y, double z);

    /** Add to the entity's velocity (delta movement). */
    void addVelocity(double dx, double dy, double dz);
}
