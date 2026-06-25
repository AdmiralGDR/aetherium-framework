/*
 * Aetherium Framework — loader-agnostic render layer (RenderType abstraction).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

/**
 * The render pass a draw belongs to — the loader-agnostic stand-in for Minecraft's {@code RenderType}.
 *
 * <p>EN: A model declares its layer; the loader maps each constant to the matching real {@code RenderType}
 * (chest/entity translucency, cutout foliage, debug lines, …). No {@code net.minecraft} type required to
 * describe the pass.
 * RU: Модель объявляет свой слой; загрузчик отображает каждую константу на соответствующий реальный
 * {@code RenderType}. Тип {@code net.minecraft} не нужен для описания прохода.
 */
public enum RenderLayer {
    SOLID,
    CUTOUT,
    CUTOUT_MIPPED,
    TRANSLUCENT,
    LINES,
    ENTITY_SOLID,
    ENTITY_CUTOUT,
    ENTITY_TRANSLUCENT
}
