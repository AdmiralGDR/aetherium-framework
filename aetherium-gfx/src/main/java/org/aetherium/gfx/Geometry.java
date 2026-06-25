/*
 * Aetherium Framework — primitive geometry emitters into a VertexSink.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

/**
 * Emits primitive meshes into a {@link VertexSink}, transformed by a {@link Mat4} pose.
 *
 * <p>EN: The reusable building block model code shares — e.g. a skeletal model emits one
 * {@link #emitCuboid cuboid} per bone using that bone's global transform. Positions are run through the
 * pose CPU-side, so the same call works offline (recording) or against a real {@code VertexConsumer}.
 * RU: Переиспользуемый блок для кода моделей — напр. скелетная модель выпускает по одному
 * {@link #emitCuboid кубу} на кость с глобальной трансформацией кости. Позиции прогоняются через позу на
 * CPU, поэтому вызов работает офлайн или против реального {@code VertexConsumer}.
 */
public final class Geometry {

    private Geometry() {
    }

    /** Six faces × four vertices = the quad count of one cuboid. */
    public static final int CUBOID_VERTICES = 24;

    /**
     * Emit an axis-aligned cuboid centered on the pose origin, with the given full sizes and color.
     * Each face's four corners are transformed by {@code pose} and committed as a quad with its normal.
     */
    public static void emitCuboid(VertexSink sink, Mat4 pose, float sizeX, float sizeY, float sizeZ, int argb) {
        float hx = sizeX / 2f;
        float hy = sizeY / 2f;
        float hz = sizeZ / 2f;

        // 8 corners.
        Vec3 c000 = pose.transformPoint(-hx, -hy, -hz);
        Vec3 c001 = pose.transformPoint(-hx, -hy, hz);
        Vec3 c010 = pose.transformPoint(-hx, hy, -hz);
        Vec3 c011 = pose.transformPoint(-hx, hy, hz);
        Vec3 c100 = pose.transformPoint(hx, -hy, -hz);
        Vec3 c101 = pose.transformPoint(hx, -hy, hz);
        Vec3 c110 = pose.transformPoint(hx, hy, -hz);
        Vec3 c111 = pose.transformPoint(hx, hy, hz);

        quad(sink, argb, 0, 0, 1, c001, c101, c111, c011);   // +Z (front)
        quad(sink, argb, 0, 0, -1, c100, c000, c010, c110);  // -Z (back)
        quad(sink, argb, 1, 0, 0, c101, c100, c110, c111);   // +X (right)
        quad(sink, argb, -1, 0, 0, c000, c001, c011, c010);  // -X (left)
        quad(sink, argb, 0, 1, 0, c010, c011, c111, c110);   // +Y (top)
        quad(sink, argb, 0, -1, 0, c000, c100, c101, c001);  // -Y (bottom)
    }

    private static void quad(VertexSink sink, int argb, float nx, float ny, float nz,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        emit(sink, a, argb, 0f, 0f, nx, ny, nz);
        emit(sink, b, argb, 1f, 0f, nx, ny, nz);
        emit(sink, c, argb, 1f, 1f, nx, ny, nz);
        emit(sink, d, argb, 0f, 1f, nx, ny, nz);
    }

    private static void emit(VertexSink sink, Vec3 p, int argb, float u, float v, float nx, float ny, float nz) {
        sink.vertex(p.x(), p.y(), p.z()).color(argb).uv(u, v).normal(nx, ny, nz).endVertex();
    }
}
