/*
 * Aetherium Framework — the platform draw-primitive SPI for the UI layer.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * The two draw primitives the UI runtime needs — implemented by the loader over the platform's
 * {@code GuiGraphics}/{@code DrawContext}, and by a recorder for offline tests.
 *
 * <p>EN: Keeping the SPI to {@code fillRect} + {@code drawText} (plus {@link UiMetrics} for measurement)
 * means the entire declarative framework is loader-agnostic: the loader writes one thin adapter and every
 * Aetherium screen renders. No Minecraft type crosses this boundary.
 * RU: SPI из {@code fillRect} + {@code drawText} (плюс {@link UiMetrics}) делает весь декларативный
 * фреймворк независимым от загрузчика: загрузчик пишет один тонкий адаптер — и любой экран Aetherium
 * рисуется. Ни один тип Minecraft не пересекает эту границу.
 */
public interface UiRenderer {

    /** Fill a rectangle with a packed-ARGB color. */
    void fillRect(int x, int y, int width, int height, int argb);

    /** Draw a single line of text at the top-left {@code (x, y)} in a packed-ARGB color. */
    void drawText(int x, int y, String text, int argb);

    /**
     * Push a scissor/clip rectangle: subsequent draws are confined to it until the matching
     * {@link #popClip()}. Default is a no-op so a pre-existing adapter keeps compiling; a real adapter maps
     * this onto {@code GuiGraphics.enableScissor}. Used by {@link ScrollPanel} so overflowing content is
     * clipped, not painted outside the viewport.
     */
    default void pushClip(int x, int y, int width, int height) {
        // no-op by default (an adapter that supports scrolling overrides this)
    }

    /** Pop the most recent clip pushed by {@link #pushClip} (maps to {@code GuiGraphics.disableScissor}). */
    default void popClip() {
        // no-op by default
    }
}
