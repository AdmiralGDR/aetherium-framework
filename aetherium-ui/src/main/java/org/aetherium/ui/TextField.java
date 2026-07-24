/*
 * Aetherium Framework — a focusable single-line text-entry widget.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.function.Consumer;

/**
 * A focusable single-line text field — the widget that needs the keyboard-input channel.
 *
 * <p>EN: Unlike the stateless {@link Button}, a text field <em>owns editable state</em> (its buffer and
 * focus flag), so a mod keeps one instance in a field and returns it from {@code build()} each frame rather
 * than recreating it. Clicking it focuses it (see {@link UiRuntime#click}); characters routed by
 * {@link UiRuntime#charTyped} append, and {@link UiRuntime#keyPressed} handles backspace. This is the
 * missing piece the feedback called out: with {@code UiRuntime} gaining a key/char channel, a real
 * {@code TextField} becomes possible instead of an on-screen keyboard built from buttons.
 * RU: В отличие от {@link Button}, текстовое поле владеет редактируемым состоянием (буфер + фокус), поэтому
 * мод хранит один экземпляр и возвращает его из {@code build()} каждый кадр. Клик фокусирует поле; символы
 * из {@link UiRuntime#charTyped} добавляются, {@link UiRuntime#keyPressed} обрабатывает Backspace.
 */
public final class TextField extends Widget<TextField> {

    private final StringBuilder buffer = new StringBuilder();
    private String placeholder = "";
    private boolean focused;
    private int maxLength = 256;
    private UiColor textColor = UiColor.WHITE;
    private Consumer<String> onChange;

    public TextField() {
    }

    public TextField(String initial) {
        if (initial != null) {
            buffer.append(initial);
        }
    }

    /** Text shown (dimmed) when the field is empty. */
    public TextField placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    /** Maximum number of characters the field will accept. */
    public TextField maxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        return this;
    }

    public TextField textColor(UiColor color) {
        this.textColor = color;
        return this;
    }

    /** Called whenever the text changes (type/backspace). */
    public TextField onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    // --- editable state (driven by UiRuntime) ---------------------------------------------------

    public String text() {
        return buffer.toString();
    }

    public TextField text(String value) {
        buffer.setLength(0);
        if (value != null) {
            buffer.append(value.length() > maxLength ? value.substring(0, maxLength) : value);
        }
        return this;
    }

    public boolean focused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public String placeholderText() {
        return placeholder;
    }

    public UiColor textColor() {
        return textColor;
    }

    /** Append a typed character (respecting {@link #maxLength}); fires {@code onChange}. */
    void type(char c) {
        if (c >= ' ' && buffer.length() < maxLength) {
            buffer.append(c);
            fireChange();
        }
    }

    /** Delete the last character; fires {@code onChange} if anything was removed. */
    void backspace() {
        if (buffer.length() > 0) {
            buffer.deleteCharAt(buffer.length() - 1);
            fireChange();
        }
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.accept(buffer.toString());
        }
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        String shown = buffer.length() == 0 ? placeholder : buffer.toString();
        return Math.max(metrics.textWidth(shown), metrics.textWidth("MMMMMMMM")) + padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        return metrics.lineHeight() + padding().vertical();
    }
}
