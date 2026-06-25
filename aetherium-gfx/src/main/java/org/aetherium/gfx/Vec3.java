/*
 * Aetherium Framework — a 3D float vector (pure value type).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

/** An immutable 3-component vector — a point or direction, with no Minecraft dependency. */
public record Vec3(float x, float y, float z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 scale(float s) {
        return new Vec3(x * s, y * s, z * s);
    }
}
