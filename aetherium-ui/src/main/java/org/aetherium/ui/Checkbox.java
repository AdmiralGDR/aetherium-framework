/*
 * Aetherium Framework — checkbox widget.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.function.Consumer;

/**
 * A boolean checkbox — an outlined box that fills when checked.
 *
 * <p>EN: Paint + input SPI only. {@link #handleClick} toggles and fires {@code onChange}; {@link #paintContent}
 * draws the box (border) and, when checked, an inner mark. {@link #checked(boolean)} sets state without firing.
 * RU: Только paint + input SPI. {@link #handleClick} переключает и вызывает {@code onChange};
 * {@link #paintContent} рисует рамку и, если отмечено, внутреннюю заливку. {@link #checked(boolean)} — без вызова.
 */
public final class Checkbox extends Widget<Checkbox> {

    private static final int DEFAULT_SIZE = 12;

    private boolean checked;
    private Consumer<Boolean> onChange;
    private UiColor border = UiColor.rgb(0x9A9AA0);
    private UiColor mark = UiColor.rgb(0x4C8DFF);

    public Checkbox(boolean checked) {
        this.checked = checked;
    }

    public Checkbox onChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    public Checkbox colors(UiColor border, UiColor mark) {
        this.border = border;
        this.mark = mark;
        return this;
    }

    /** Set the state programmatically (does NOT fire {@code onChange}). */
    public Checkbox checked(boolean value) {
        this.checked = value;
        return this;
    }

    public boolean checked() {
        return checked;
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        return DEFAULT_SIZE + padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        return DEFAULT_SIZE + padding().vertical();
    }

    @Override
    public boolean interactive() {
        return true;
    }

    @Override
    public boolean handleClick(int localX, int localY, int width, int height) {
        checked = !checked;
        if (onChange != null) {
            onChange.accept(checked);
        }
        return true;
    }

    @Override
    public void paintContent(UiRenderer renderer, Rect box, UiMetrics metrics) {
        Rect c = box.shrink(padding());
        int side = Math.min(c.width(), c.height());
        if (side <= 0) {
            return;
        }
        renderer.fillRect(c.x(), c.y(), side, side, border.argb());
        if (checked) {
            int inset = Math.max(1, side / 4);
            renderer.fillRect(c.x() + inset, c.y() + inset, side - 2 * inset, side - 2 * inset, mark.argb());
        }
    }

    @Override
    protected Role defaultRole() {
        return Role.CHECKBOX;
    }
}
