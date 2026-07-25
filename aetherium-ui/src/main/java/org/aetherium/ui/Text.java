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
    private boolean wrap = false;

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

    /**
     * Wrap the label at word boundaries to fit its box width, flowing onto extra lines instead of clipping
     * horizontally (). Wrapping is done by the layout engine — the only place that knows both the
     * assigned box width and the {@link UiMetrics} — so it is exact, and every consumer gets it for free
     * instead of reinventing a character-estimating paragraph splitter.
     */
    public Text wrap(boolean wrap) {
        this.wrap = wrap;
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

    public boolean wrap() {
        return wrap;
    }

    /**
     * Split the label into lines that each fit {@code contentWidth} (box width minus padding), breaking at
     * spaces. A single word wider than the box is left whole on its own line (better an overrun than an
     * infinite loop). Shared by the layout (for height) and the paint (for drawing) so both agree exactly.
     */
    public java.util.List<String> wrapLines(int contentWidth, UiMetrics metrics) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (!wrap || contentWidth <= 0 || text.isEmpty()) {
            lines.add(text);
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && metrics.textWidth(candidate) > contentWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        return metrics.textWidth(text) + padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        return metrics.lineHeight() + padding().vertical();
    }

    @Override
    public int measuredHeight(UiMetrics metrics, int assignedWidth) {
        if (!wrap) {
            return intrinsicHeight(metrics);
        }
        int contentWidth = assignedWidth - padding().horizontal();
        int lineCount = Math.max(1, wrapLines(contentWidth, metrics).size());
        return lineCount * metrics.lineHeight() + padding().vertical();
    }
}
