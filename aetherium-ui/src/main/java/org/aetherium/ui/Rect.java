/*
 * Aetherium Framework — an axis-aligned integer rectangle (computed layout box).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * A laid-out box in screen pixels. Produced by {@link FlexLayout}, consumed by paint + hit-testing.
 */
public record Rect(int x, int y, int width, int height) {

    /** Whether the point {@code (px, py)} falls inside this rectangle. */
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /** This rectangle reduced by {@code insets} on each side (never negative in size). */
    public Rect shrink(Insets insets) {
        return new Rect(
                x + insets.left(),
                y + insets.top(),
                Math.max(0, width - insets.horizontal()),
                Math.max(0, height - insets.vertical()));
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }
}
