/*
 * Aetherium Framework — a text label widget.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/** A single-line text label. Intrinsic size comes from {@link UiMetrics}. */
public final class Text extends Widget<Text> {

    private final String text;
    private UiColor color = UiColor.WHITE;

    public Text(String text) {
        this.text = text == null ? "" : text;
    }

    public Text color(UiColor color) {
        this.color = color;
        return this;
    }

    public String text() {
        return text;
    }

    public UiColor color() {
        return color;
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        return metrics.textWidth(text) + padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        return metrics.lineHeight() + padding().vertical();
    }
}
