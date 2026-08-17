/*
 * Aetherium Framework — accessibility role for a UI widget.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * The semantic role a widget plays — what it <em>is</em> to assistive technology and controller navigation.
 *
 * <p>EN: Paired with an accessible name ({@link Widget#accessibleName()}), a role lets a screen reader announce
 * "Music, switch, on" instead of a mystery rectangle, and lets a future controller/keyboard focus ring skip
 * decorative content. Every built-in interactive widget reports a sensible default role; an author overrides it
 * with {@link Widget#role(Role)} when a widget is repurposed. {@link #NONE} = presentational (not announced).
 * RU: В паре с доступным именем ({@link Widget#accessibleName()}) роль позволяет экранному диктору произнести
 * «Музыка, переключатель, вкл» вместо загадочного прямоугольника и даёт будущей фокус-навигации пропускать
 * декоративное. Каждый встроенный интерактивный виджет сообщает разумную роль по умолчанию; автор меняет её
 * через {@link Widget#role(Role)}. {@link #NONE} — презентационное (не озвучивается).
 */
public enum Role {
    /** Presentational — not announced (a plain box, a spacer, decorative text). */
    NONE,
    BUTTON,
    CHECKBOX,
    SWITCH,
    SLIDER,
    TEXT_FIELD,
    PROGRESS_BAR,
    TAB,
    HEADING,
    IMAGE,
    LINK
}
