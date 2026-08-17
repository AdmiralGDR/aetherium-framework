/*
 * Aetherium Framework — UI design-token theme (semantic colors + spacing scale).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.theme;

import org.aetherium.ui.UiColor;

import java.util.Objects;

/**
 * A palette of <strong>semantic</strong> design tokens — the colors and spacing a screen is built from, named
 * by role (not by hue) so light/dark and per-mod themes swap cleanly.
 *
 * <p>EN: Instead of scattering {@code UiColor.rgb(0x2F6FED)} through a screen, a widget reads {@code
 * theme.primary()} / {@code theme.text()} / {@code theme.space(2)}; changing the theme (or shipping {@link
 * #dark()} vs {@link #light()}) restyles everything with no per-widget edits. Immutable and global-free — a
 * screen is handed the theme it should use, so two mods can render with different themes in the same game. The
 * spacing scale ({@link #space(int)}) keeps gaps/paddings on a consistent rhythm. Zero-dependency.
 * RU: Вместо разбросанных {@code UiColor.rgb(...)} виджет читает {@code theme.primary()}/{@code theme.text()}/
 * {@code theme.space(2)}; смена темы (или {@link #dark()} против {@link #light()}) перекрашивает всё без правок
 * виджетов. Неизменяемая, без глобального состояния — экрану передаётся его тема, поэтому два мода могут
 * рисоваться с разными темами в одной игре. Шкала отступов держит ритм. Без зависимостей.
 *
 * @param background   the screen/window backdrop
 * @param surface      a raised panel/card surface
 * @param surfaceMuted a subtle surface (a track, a disabled fill)
 * @param border       dividers and outlines
 * @param primary      the accent color (active controls, focus)
 * @param onPrimary    text/icons drawn on top of {@code primary}
 * @param text         primary text
 * @param textMuted    secondary/placeholder text
 * @param danger       errors and destructive actions
 * @param success      positive/confirmation state
 * @param spacingUnit  the base spacing step in px ({@link #space(int)} multiplies it)
 * @param radius       the corner-radius token in px
 */
public record Theme(
        UiColor background,
        UiColor surface,
        UiColor surfaceMuted,
        UiColor border,
        UiColor primary,
        UiColor onPrimary,
        UiColor text,
        UiColor textMuted,
        UiColor danger,
        UiColor success,
        int spacingUnit,
        int radius) {

    public Theme {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(surfaceMuted, "surfaceMuted");
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(onPrimary, "onPrimary");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(textMuted, "textMuted");
        Objects.requireNonNull(danger, "danger");
        Objects.requireNonNull(success, "success");
        if (spacingUnit < 1) {
            throw new IllegalArgumentException("spacingUnit must be >= 1: " + spacingUnit);
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0: " + radius);
        }
    }

    /** The default light theme. */
    public static Theme light() {
        return new Theme(
                UiColor.rgb(0xF5F5F7), UiColor.rgb(0xFFFFFF), UiColor.rgb(0xEBEBED), UiColor.rgb(0xD0D0D5),
                UiColor.rgb(0x2F6FED), UiColor.rgb(0xFFFFFF),
                UiColor.rgb(0x1C1C1E), UiColor.rgb(0x6E6E73),
                UiColor.rgb(0xD93025), UiColor.rgb(0x1E8E3E),
                4, 6);
    }

    /** The default dark theme. */
    public static Theme dark() {
        return new Theme(
                UiColor.rgb(0x1C1C1E), UiColor.rgb(0x2C2C2E), UiColor.rgb(0x3A3A3C), UiColor.rgb(0x48484A),
                UiColor.rgb(0x4C8DFF), UiColor.rgb(0xFFFFFF),
                UiColor.rgb(0xF5F5F7), UiColor.rgb(0x9A9AA0),
                UiColor.rgb(0xFF6B60), UiColor.rgb(0x63D68A),
                4, 6);
    }

    /** {@code steps} spacing units in px (clamped to &ge; 0), keeping gaps/paddings on one rhythm. */
    public int space(int steps) {
        return Math.max(0, steps) * spacingUnit;
    }

    /** This theme with a different accent color (the common brand customization). */
    public Theme withPrimary(UiColor newPrimary) {
        return new Theme(background, surface, surfaceMuted, border, newPrimary, onPrimary,
                text, textMuted, danger, success, spacingUnit, radius);
    }

    /** This theme with a different base spacing step. */
    public Theme withSpacingUnit(int newSpacingUnit) {
        return new Theme(background, surface, surfaceMuted, border, primary, onPrimary,
                text, textMuted, danger, success, newSpacingUnit, radius);
    }
}
