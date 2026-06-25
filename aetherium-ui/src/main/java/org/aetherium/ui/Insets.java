/*
 * Aetherium Framework — box padding for the UI layer.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * Padding inside a widget's box (CSS order: top/right/bottom/left).
 *
 * <p>EN/RU: pure layout data — отступы внутри бокса виджета.
 */
public record Insets(int top, int right, int bottom, int left) {

    public static final Insets ZERO = new Insets(0, 0, 0, 0);

    /** Equal padding on all four sides. */
    public static Insets all(int p) {
        return new Insets(p, p, p, p);
    }

    /** Vertical (top/bottom) and horizontal (left/right) padding. */
    public static Insets symmetric(int vertical, int horizontal) {
        return new Insets(vertical, horizontal, vertical, horizontal);
    }

    public int horizontal() {
        return left + right;
    }

    public int vertical() {
        return top + bottom;
    }
}
