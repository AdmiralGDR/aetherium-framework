/*
 * Aetherium Framework — a 4x4 affine transform matrix (pure Java; the PoseStack / matrix abstraction).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

import java.util.Arrays;

/**
 * A row-major 4x4 transform matrix — the loader-agnostic stand-in for {@code org.joml.Matrix4f}.
 *
 * <p>EN: Pure float math (no Blaze3D / JOML import) so an animation engine can compute hierarchical bone
 * transforms entirely CPU-side, then hand the result to the loader's real {@code PoseStack}. Translation
 * lives in the last column; {@link #transformPoint} applies the affine transform to a point (w=1). All
 * factory ops return new matrices (immutable-by-use), and {@link #multiply} composes parent×child.
 * RU: Чистая float-математика (без импорта Blaze3D/JOML), чтобы движок анимации вычислял иерархические
 * трансформации костей на CPU, а затем передавал результат реальному {@code PoseStack} загрузчика.
 */
public final class Mat4 {

    // Row-major: m[row * 4 + col].
    private final float[] m;

    private Mat4(float[] m) {
        this.m = m;
    }

    public static Mat4 identity() {
        float[] e = new float[16];
        e[0] = e[5] = e[10] = e[15] = 1f;
        return new Mat4(e);
    }

    public static Mat4 translation(float tx, float ty, float tz) {
        Mat4 r = identity();
        r.m[3] = tx;
        r.m[7] = ty;
        r.m[11] = tz;
        return r;
    }

    public static Mat4 scaling(float sx, float sy, float sz) {
        float[] e = new float[16];
        e[0] = sx;
        e[5] = sy;
        e[10] = sz;
        e[15] = 1f;
        return new Mat4(e);
    }

    public static Mat4 rotationX(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        Mat4 r = identity();
        r.m[5] = c;  r.m[6] = -s;
        r.m[9] = s;  r.m[10] = c;
        return r;
    }

    public static Mat4 rotationY(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        Mat4 r = identity();
        r.m[0] = c;   r.m[2] = s;
        r.m[8] = -s;  r.m[10] = c;
        return r;
    }

    public static Mat4 rotationZ(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        Mat4 r = identity();
        r.m[0] = c;  r.m[1] = -s;
        r.m[4] = s;  r.m[5] = c;
        return r;
    }

    /** Matrix product {@code this * other} (apply {@code other} first, then {@code this}). */
    public Mat4 multiply(Mat4 other) {
        float[] a = this.m;
        float[] b = other.m;
        float[] r = new float[16];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[row * 4 + k] * b[k * 4 + col];
                }
                r[row * 4 + col] = sum;
            }
        }
        return new Mat4(r);
    }

    /** Apply this affine transform to a point (homogeneous w = 1). */
    public Vec3 transformPoint(float x, float y, float z) {
        float nx = m[0] * x + m[1] * y + m[2] * z + m[3];
        float ny = m[4] * x + m[5] * y + m[6] * z + m[7];
        float nz = m[8] * x + m[9] * y + m[10] * z + m[11];
        return new Vec3(nx, ny, nz);
    }

    public Vec3 transformPoint(Vec3 p) {
        return transformPoint(p.x(), p.y(), p.z());
    }

    /** Row-major copy of the 16 elements (for the loader to feed a real matrix). */
    public float[] toArray() {
        return m.clone();
    }

    public float get(int row, int col) {
        return m[row * 4 + col];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Mat4 other && Arrays.equals(m, other.m);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(m);
    }

    @Override
    public String toString() {
        return "Mat4" + Arrays.toString(m);
    }
}
