/*
 * Aetherium Framework — slider widget.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.function.DoubleConsumer;

/**
 * A horizontal slider selecting a value in {@code [min, max]} — a track, a filled portion, and a knob.
 *
 * <p>EN: Paint + input SPI only. {@link #handleClick} maps the click's local x to a value across the track
 * (clamped) and fires {@code onChange}; {@link #paintContent} draws the track, the filled portion up to the
 * value, and the knob. {@link #value(double)} sets state without firing. A degenerate {@code min == max} range
 * simply pins the value.
 * RU: Только paint + input SPI. {@link #handleClick} переводит локальный x клика в значение по дорожке (с
 * ограничением) и вызывает {@code onChange}; {@link #paintContent} рисует дорожку, заполнение до значения и
 * ползунок. {@link #value(double)} — без вызова. Вырожденный диапазон {@code min == max} просто фиксирует значение.
 */
public final class Slider extends Widget<Slider> {

    private static final int DEFAULT_WIDTH = 96;
    private static final int DEFAULT_HEIGHT = 12;

    private final double min;
    private final double max;
    private double value;
    private DoubleConsumer onChange;
    private UiColor track = UiColor.rgb(0x3A3A3C);
    private UiColor fill = UiColor.rgb(0x4C8DFF);
    private UiColor knob = UiColor.WHITE;

    public Slider(double min, double max, double value) {
        if (max < min) {
            throw new IllegalArgumentException("max < min: " + max + " < " + min);
        }
        this.min = min;
        this.max = max;
        this.value = clamp(value);
    }

    public Slider onChange(DoubleConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    public Slider colors(UiColor track, UiColor fill, UiColor knob) {
        this.track = track;
        this.fill = fill;
        this.knob = knob;
        return this;
    }

    /** Set the value programmatically (clamped; does NOT fire {@code onChange}). */
    public Slider value(double newValue) {
        this.value = clamp(newValue);
        return this;
    }

    public double value() {
        return value;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    /** The value as a fraction of the range in {@code [0,1]} (0 for a degenerate range). */
    private double fraction() {
        return max == min ? 0.0 : (value - min) / (max - min);
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        return DEFAULT_WIDTH + padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        return DEFAULT_HEIGHT + padding().vertical();
    }

    @Override
    public boolean interactive() {
        return true;
    }

    @Override
    public boolean handleClick(int localX, int localY, int width, int height) {
        int innerWidth = width - padding().horizontal();
        if (innerWidth <= 0 || max == min) {
            return true;
        }
        double t = Math.max(0.0, Math.min(1.0, (double) (localX - padding().left()) / innerWidth));
        value = clamp(min + t * (max - min));
        if (onChange != null) {
            onChange.accept(value);
        }
        return true;
    }

    @Override
    public void paintContent(UiRenderer renderer, Rect box, UiMetrics metrics) {
        Rect c = box.shrink(padding());
        if (c.width() <= 0 || c.height() <= 0) {
            return;
        }
        renderer.fillRect(c.x(), c.y(), c.width(), c.height(), track.argb());
        int fillWidth = (int) Math.round(c.width() * fraction());
        if (fillWidth > 0) {
            renderer.fillRect(c.x(), c.y(), fillWidth, c.height(), fill.argb());
        }
        int knobSide = c.height();
        int knobX = c.x() + Math.max(0, Math.min(c.width() - knobSide, fillWidth - knobSide / 2));
        renderer.fillRect(knobX, c.y(), knobSide, knobSide, knob.argb());
    }

    @Override
    protected Role defaultRole() {
        return Role.SLIDER;
    }
}
