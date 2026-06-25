/*
 * Aetherium Framework — loader-agnostic vertex consumer.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

/**
 * A raw vertex stream — the pure mirror of Minecraft's {@code VertexConsumer}.
 *
 * <p>EN: A fluent {@code vertex().color().uv().normal().endVertex()} builder an animation engine emits
 * geometry into; the loader adapts it over a real {@code VertexConsumer} bound to a {@link RenderLayer}.
 * No Blaze3D type crosses the boundary, so models render through the PAL on any platform.
 * RU: Текучий построитель {@code vertex().color().uv().normal().endVertex()}, в который движок анимации
 * выпускает геометрию; загрузчик адаптирует его над реальным {@code VertexConsumer} для {@link RenderLayer}.
 */
public interface VertexSink {

    /** Begin a vertex at the given position. */
    VertexSink vertex(float x, float y, float z);

    /** Set the vertex color (packed ARGB). */
    VertexSink color(int argb);

    /** Set the vertex texture coordinates. */
    VertexSink uv(float u, float v);

    /** Set the vertex normal. */
    VertexSink normal(float nx, float ny, float nz);

    /** Commit the current vertex. */
    void endVertex();

    /** Convenience: emit a positioned, colored vertex in one call. */
    default void put(Vec3 position, int argb) {
        vertex(position.x(), position.y(), position.z()).color(argb).endVertex();
    }
}
