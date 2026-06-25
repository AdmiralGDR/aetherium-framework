/*
 * Aetherium Framework — loader-agnostic renderable model (animation-engine hook).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

/**
 * A model that emits geometry into a {@link VertexSink} given a {@link PoseStack} — the hook a skeletal
 * animation engine implements to render through the PAL.
 *
 * <p>EN: The loader calls {@link #render} each frame with a {@link PoseStack} already positioned at the
 * entity, a {@link VertexSink} bound to the chosen {@link RenderLayer}, and the partial tick. A GeckoLib-
 * style engine builds a {@link Skeleton}, poses it from its keyframes, and emits per-bone geometry — all
 * without importing a Blaze3D type. Register one via {@link ModelRegistry}.
 * RU: Загрузчик вызывает {@link #render} каждый кадр с {@link PoseStack}, уже позиционированным на
 * сущности, {@link VertexSink} для выбранного {@link RenderLayer} и частичным тиком. Движок в духе
 * GeckoLib строит {@link Skeleton}, позиционирует его по кейфреймам и выпускает покостную геометрию.
 */
@FunctionalInterface
public interface AetheriumModel {

    /** Emit this model's geometry for the current frame. */
    void render(PoseStack pose, VertexSink sink, RenderLayer layer, float partialTick);
}
