/*
 * Aetherium Framework — packed ARGB color for the UI layer.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * An immutable packed-ARGB color — the only color type the pure UI layer knows.
 *
 * <p>EN: Stored as {@code 0xAARRGGBB}; the loader passes {@link #argb()} straight to the platform's
 * {@code GuiGraphics.fill}/text APIs. No Minecraft type involved.
 * RU: Хранится как {@code 0xAARRGGBB}; загрузчик передаёт {@link #argb()} прямо в API
 * {@code GuiGraphics.fill}/текста платформы. Без типов Minecraft.
 */
public record UiColor(int argb) {

    public static final UiColor TRANSPARENT = new UiColor(0x00000000);
    public static final UiColor WHITE = rgb(0xFFFFFF);
    public static final UiColor BLACK = rgb(0x000000);

    /** Opaque color from a {@code 0xRRGGBB} value. */
    public static UiColor rgb(int rgb) {
        return new UiColor(0xFF000000 | (rgb & 0xFFFFFF));
    }

    /** Color from components (0–255 each). */
    public static UiColor rgba(int r, int g, int b, int a) {
        return new UiColor(((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
    }

    public int alpha() {
        return (argb >>> 24) & 0xFF;
    }

    /** True if fully transparent (nothing to paint). */
    public boolean isTransparent() {
        return alpha() == 0;
    }
}
