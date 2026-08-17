/*
 * Aetherium Framework — a clickable button widget.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * A clickable button with a centered label and an {@code onClick} callback.
 *
 * <p>EN: The callback is a plain {@link Runnable} — no Minecraft event type. The UI runtime fires it on a
 * hit-tested click. A button carries a default background and a comfortable intrinsic size around its text.
 * RU: Колбэк — обычный {@link Runnable}, без типов событий Minecraft. Среда UI вызывает его по клику с
 * проверкой попадания. Кнопка имеет фон по умолчанию и удобный собственный размер вокруг текста.
 */
public final class Button extends Widget<Button> {

    /** Default button background when none is set. */
    public static final UiColor DEFAULT_BACKGROUND = UiColor.rgb(0x3F3F46);

    private static final int H_PAD = 8;
    private static final int V_PAD = 5;

    private final String text;
    private final Runnable onClick;
    private UiColor color = UiColor.WHITE;

    public Button(String text, Runnable onClick) {
        this.text = text == null ? "" : text;
        this.onClick = onClick;
        background(DEFAULT_BACKGROUND);
    }

    public Button color(UiColor color) {
        this.color = color;
        return this;
    }

    public String text() {
        return text;
    }

    public UiColor color() {
        return color;
    }

    public Runnable onClick() {
        return onClick;
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        return metrics.textWidth(text) + 2 * H_PAD + padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        return metrics.lineHeight() + 2 * V_PAD + padding().vertical();
    }

    @Override
    public void paintContent(UiRenderer renderer, Rect box, UiMetrics metrics) {
        Rect c = box.shrink(padding());
        int tw = metrics.textWidth(text);
        int tx = c.x() + Math.max(0, (c.width() - tw) / 2);
        int ty = c.y() + Math.max(0, (c.height() - metrics.lineHeight()) / 2);
        renderer.drawText(tx, ty, text, color.argb());
    }

    @Override
    public boolean interactive() {
        return onClick != null;
    }

    @Override
    public boolean handleClick(int localX, int localY, int width, int height) {
        if (onClick != null) {
            onClick.run();
            return true;
        }
        return false;
    }

    @Override
    protected Role defaultRole() {
        return Role.BUTTON;
    }

    @Override
    public String accessibleName() {
        return label() != null ? label() : text;
    }
}
