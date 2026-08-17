/*
 * Aetherium Framework — divider widget (a thin rule).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * A thin horizontal rule that separates content — a named, self-describing separator.
 *
 * <p>EN: Display-only; renders through the paint SPI. Its intrinsic height is the line {@code thickness} plus
 * padding, so it reserves exactly one thin row in a column; give it width via {@code grow(1)} to span the
 * container. Cleaner at a call site than a bare colored {@link Container} of fixed height.
 * RU: Только отображение; рисуется через paint SPI. Собственная высота — толщина линии плюс отступы, поэтому
 * он занимает ровно одну тонкую строку в колонке; ширину дайте {@code grow(1)}. Понятнее, чем цветной
 * {@link Container} фиксированной высоты.
 */
public final class Divider extends Widget<Divider> {

    private UiColor color = UiColor.rgb(0x48484A);
    private int thickness = 1;

    public Divider color(UiColor color) {
        this.color = color;
        return this;
    }

    /** Line thickness in px (clamped to &ge; 1). */
    public Divider thickness(int thickness) {
        this.thickness = Math.max(1, thickness);
        return this;
    }

    public int thickness() {
        return thickness;
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        return padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        return thickness + padding().vertical();
    }

    @Override
    public void paintContent(UiRenderer renderer, Rect box, UiMetrics metrics) {
        Rect c = box.shrink(padding());
        if (c.width() <= 0) {
            return;
        }
        renderer.fillRect(c.x(), c.y(), c.width(), Math.min(thickness, Math.max(0, c.height())), color.argb());
    }
}
