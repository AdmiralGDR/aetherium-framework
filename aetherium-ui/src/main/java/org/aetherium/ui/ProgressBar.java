/*
 * Aetherium Framework — progress bar widget (display-only).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * A horizontal progress bar: a track with a filled portion showing a fraction in {@code [0,1]}.
 *
 * <p>EN: Display-only (no input), so it needs nothing from the input path — it renders entirely through the
 * paint SPI ({@link #paintContent}). The fraction is clamped, so an out-of-range value never paints past the
 * track. Give it a width via {@code width(...)}/{@code grow(...)} and a thickness via {@code height(...)}.
 * RU: Только отображение (без ввода) — рисуется целиком через paint SPI ({@link #paintContent}). Доля
 * ограничена, поэтому значение вне диапазона не рисуется за пределами дорожки. Ширину задайте {@code width}/
 * {@code grow}, толщину — {@code height}.
 */
public final class ProgressBar extends Widget<ProgressBar> {

    private static final int DEFAULT_WIDTH = 64;
    private static final int DEFAULT_THICKNESS = 6;

    private double fraction;
    private UiColor track = UiColor.rgb(0x3A3A3C);
    private UiColor fill = UiColor.rgb(0x4C8DFF);

    public ProgressBar(double fraction) {
        this.fraction = clamp01(fraction);
    }

    /** Set the filled fraction, clamped to {@code [0,1]}. */
    public ProgressBar fraction(double value) {
        this.fraction = clamp01(value);
        return this;
    }

    public ProgressBar track(UiColor color) {
        this.track = color;
        return this;
    }

    public ProgressBar fill(UiColor color) {
        this.fill = color;
        return this;
    }

    public double fraction() {
        return fraction;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        return DEFAULT_WIDTH + padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        return DEFAULT_THICKNESS + padding().vertical();
    }

    @Override
    public void paintContent(UiRenderer renderer, Rect box, UiMetrics metrics) {
        Rect c = box.shrink(padding());
        if (c.width() <= 0 || c.height() <= 0) {
            return;
        }
        renderer.fillRect(c.x(), c.y(), c.width(), c.height(), track.argb());
        int fillWidth = (int) Math.round(c.width() * fraction);
        if (fillWidth > 0) {
            renderer.fillRect(c.x(), c.y(), fillWidth, c.height(), fill.argb());
        }
    }

    @Override
    protected Role defaultRole() {
        return Role.PROGRESS_BAR;
    }
}
