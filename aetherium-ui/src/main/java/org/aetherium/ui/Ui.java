/*
 * Aetherium Framework — declarative widget factory (the UI DSL entry point).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * The declarative entry point — `React`/Flexbox-style factories for building a screen tree concisely.
 *
 * <pre>{@code
 * Widget<?> ui = Ui.column().padding(8).gap(4).background(UiColor.rgb(0x202020))
 *     .align(AlignItems.STRETCH)
 *     .children(
 *         Ui.label("Faction: Iron Vanguard").color(UiColor.WHITE),
 *         Ui.row().gap(4).children(
 *             Ui.button("Deposit", this::deposit).grow(1),
 *             Ui.button("Close", this::close).grow(1)),
 *         Ui.spacer().grow(1));
 * }</pre>
 */
public final class Ui {

    private Ui() {
    }

    /** A vertical flex container (children top→bottom). */
    public static Container column() {
        return new Container(FlexDirection.COLUMN);
    }

    /** A horizontal flex container (children left→right). */
    public static Container row() {
        return new Container(FlexDirection.ROW);
    }

    /** A text label. */
    public static Text label(String text) {
        return new Text(text);
    }

    /** A clickable button with an {@code onClick} callback. */
    public static Button button(String text, Runnable onClick) {
        return new Button(text, onClick);
    }

    /** Flexible empty space (give it {@code grow}). */
    public static Spacer spacer() {
        return new Spacer();
    }
}
