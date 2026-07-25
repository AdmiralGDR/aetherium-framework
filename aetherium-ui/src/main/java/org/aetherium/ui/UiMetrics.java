/*
 * Aetherium Framework — text measurement abstraction for layout.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * Font metrics the layout engine needs — abstracted so the pure module never touches a real font.
 *
 * <p>EN: The loader supplies a metrics backed by the platform {@code Font} (so layout matches the real
 * glyph widths); offline, {@link #DEFAULT} uses the vanilla-like fixed metrics (6px advance, 9px line),
 * which is enough to lay out and test a screen without the game.
 * RU: Загрузчик предоставляет метрики на основе {@code Font} платформы; офлайн {@link #DEFAULT}
 * использует фиксированные ванила-подобные метрики (6px на символ, 9px строка), достаточные для
 * раскладки и тестов без игры.
 */
public interface UiMetrics {

    /** Advance width of {@code text} in pixels. */
    int textWidth(String text);

    /** Height of a single text line in pixels. */
    int lineHeight();

    /**
     * Vanilla-like fixed metrics for offline layout/testing.
     *
     * <p>the flat 6px advance is slightly <em>optimistic</em> for Cyrillic and wide glyphs, so
     * an offline {@code audit(root, metrics)} is marginally more permissive than the real in-game font. It is
     * the right default for headless tests, but a screen that only just fits under {@code DEFAULT} may clip by
     * a pixel in game — prefer the loader-supplied, font-backed metrics for a final layout gate.
     */
    UiMetrics DEFAULT = new UiMetrics() {
        @Override
        public int textWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int lineHeight() {
            return 9;
        }
    };
}
