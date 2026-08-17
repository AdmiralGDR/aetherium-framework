/*
 * Aetherium Framework — toggle switch widget.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.function.Consumer;

/**
 * A two-state switch (on/off) — a track whose knob slides to the side that is active.
 *
 * <p>EN: Built entirely on the paint + input SPIs, so it needed no edit to {@link UiRuntime}: {@link
 * #handleClick} flips the state and fires {@code onChange}; {@link #paintContent} draws the track (accent when
 * on) and the knob (left off, right on). The programmatic setter {@link #on(boolean)} does <em>not</em> fire —
 * only a user click does — so state can be synced from a model without a feedback loop.
 * RU: Построен целиком на paint + input SPI — правки {@link UiRuntime} не потребовалось: {@link #handleClick}
 * переключает состояние и вызывает {@code onChange}; {@link #paintContent} рисует дорожку (акцент во «вкл») и
 * ползунок. Программная установка {@link #on(boolean)} не вызывает {@code onChange} — только клик пользователя.
 */
public final class Toggle extends Widget<Toggle> {

    private static final int DEFAULT_WIDTH = 28;
    private static final int DEFAULT_HEIGHT = 16;

    private boolean on;
    private Consumer<Boolean> onChange;
    private UiColor onColor = UiColor.rgb(0x4C8DFF);
    private UiColor offColor = UiColor.rgb(0x48484A);
    private UiColor knobColor = UiColor.WHITE;

    public Toggle(boolean on) {
        this.on = on;
    }

    public Toggle onChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    public Toggle colors(UiColor onColor, UiColor offColor, UiColor knobColor) {
        this.onColor = onColor;
        this.offColor = offColor;
        this.knobColor = knobColor;
        return this;
    }

    /** Set the state programmatically (does NOT fire {@code onChange}). */
    public Toggle on(boolean value) {
        this.on = value;
        return this;
    }

    public boolean on() {
        return on;
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
        on = !on;
        if (onChange != null) {
            onChange.accept(on);
        }
        return true;
    }

    @Override
    public void paintContent(UiRenderer renderer, Rect box, UiMetrics metrics) {
        Rect c = box.shrink(padding());
        if (c.width() <= 0 || c.height() <= 0) {
            return;
        }
        renderer.fillRect(c.x(), c.y(), c.width(), c.height(), (on ? onColor : offColor).argb());
        int knob = c.height();
        int knobX = on ? c.right() - knob : c.x();
        renderer.fillRect(knobX, c.y(), knob, knob, knobColor.argb());
    }

    @Override
    protected Role defaultRole() {
        return Role.SWITCH;
    }
}
