/*
 * Aetherium Framework — a skeletal bone (animation hook).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

/**
 * One bone of a {@link Skeleton}: a name, an optional parent, and a local TRS transform.
 *
 * <p>EN: The unit a skeletal animation engine (GeckoLib-style) keyframes. Its {@link #localMatrix()} is
 * the standard translate × rotateZ × rotateY × rotateX × scale composition; {@link Skeleton} chains these
 * up the parent hierarchy into global transforms. Pure data + math — no game type — so an engine can run
 * its whole pose computation through the PAL and only hand final matrices to the loader.
 * RU: Единица, которую кеи-фреймит движок скелетной анимации (в духе GeckoLib). {@link #localMatrix()} —
 * стандартная композиция translate × rotateZ × rotateY × rotateX × scale; {@link Skeleton} сцепляет их по
 * иерархии родителей в глобальные трансформации.
 *
 * @param name        unique bone name
 * @param parent      parent bone name, or {@code null} for a root bone
 * @param translation local translation
 * @param rotation    local rotation as Euler angles (radians, X then Y then Z)
 * @param scale       local scale
 */
public record Bone(String name, String parent, Vec3 translation, Vec3 rotation, Vec3 scale) {

    /** A bone at the origin with no rotation and unit scale. */
    public static Bone of(String name, String parent, Vec3 translation) {
        return new Bone(name, parent, translation, Vec3.ZERO, new Vec3(1, 1, 1));
    }

    /** The bone's local transform: {@code T * Rz * Ry * Rx * S}. */
    public Mat4 localMatrix() {
        return Mat4.translation(translation.x(), translation.y(), translation.z())
                .multiply(Mat4.rotationZ(rotation.z()))
                .multiply(Mat4.rotationY(rotation.y()))
                .multiply(Mat4.rotationX(rotation.x()))
                .multiply(Mat4.scaling(scale.x(), scale.y(), scale.z()));
    }
}
