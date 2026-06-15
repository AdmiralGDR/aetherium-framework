/*
 * Aetherium Framework — entity renderer SPI.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

/**
 * Loader-agnostic entity renderer. The loader invokes {@link #render} each frame for the bound entity
 * type, with the pose already translated to the entity's interpolated position (camera-relative), so
 * the renderer only describes geometry via the {@link AetheriumRenderContext} — never importing a
 * {@code net.minecraft}/Blaze3D type.
 */
@FunctionalInterface
public interface AetheriumEntityRenderer {

    /** Shadow radius in blocks (0 = no shadow). */
    default double shadowRadius() {
        return 0.0;
    }

    /** Describe this entity's geometry for the current frame. */
    void render(AetheriumRenderContext ctx, float partialTick);
}
