/*
 * Aetherium Framework — render context abstraction.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

/**
 * Loader-agnostic drawing surface handed to an {@link AetheriumEntityRenderer}.
 *
 * <p>EN: A minimal, Blaze3D-free facade: a transform stack, color state, and a single cuboid
 * primitive — enough to position and draw entity geometry without importing {@code PoseStack},
 * {@code VertexConsumer}, or any {@code net.minecraft} type. The loader supplies the concrete
 * implementation that translates these calls into real Blaze3D draw commands.
 *
 * <p>RU: Минимальный фасад без Blaze3D: стек трансформаций, состояние цвета и один примитив-куб —
 * достаточно, чтобы позиционировать и рисовать геометрию сущности, не импортируя {@code PoseStack},
 * {@code VertexConsumer} или любой тип {@code net.minecraft}. Конкретную реализацию даёт загрузчик.
 */
public interface AetheriumRenderContext {

    void pushPose();

    void popPose();

    void translate(double x, double y, double z);

    void scale(float x, float y, float z);

    /** Set the current RGBA color (0..1) for subsequent primitives. */
    void setColor(float r, float g, float b, float a);

    /** Draw an axis-aligned cuboid of the given size, centered on the current transform origin. */
    void drawCuboid(double sizeX, double sizeY, double sizeZ);
}
