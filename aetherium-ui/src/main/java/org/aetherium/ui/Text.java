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
    private Justify align = Justify.START;

    public Text(String text) {
        this.text = text == null ? "" : text;
    }

    public Text color(UiColor color) {
        this.color = color;
        return this;
    }

    /**
     * Horizontal alignment of the label within its box (). Reuses {@link Justify}: {@code START}
     * (left, default), {@code CENTER}, {@code END} (right); {@code SPACE_BETWEEN} is treated as {@code START}.
     * Before this, only {@link Button} centred its label, so a value read-out between two buttons looked
     * misaligned.
     */
    public Text align(Justify align) {
        this.align = align == null ? Justify.START : align;
        return this;
    }

    public String text() {
        return text;
    }

    public UiColor color() {
        return color;
    }

    public Justify align() {
        return align;
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
