/*
 * Aetherium Framework — UiRenderer backed by an off-screen PixelCanvas.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.raster;

import org.aetherium.ui.UiMetrics;
import org.aetherium.ui.UiRenderer;

/**
 * A {@link UiRenderer} that draws into a {@link PixelCanvas} — the bridge that lets the very same
 * {@code UiRuntime.paint} that draws in-game rasterize a screen headlessly for a preview or a golden test.
 *
 * <p>EN: Rectangles are drawn faithfully (alpha-blended, clipped), so backgrounds, buttons, toggles, sliders,
 * and progress bars all appear in their real colours and positions. Text is drawn as a thin baseline bar of
 * the run's measured width (a deterministic position marker) — enough to review layout and to catch a text
 * that overflows its box; a real bitmap font is a documented follow-up. Zero-dependency.
 * RU: Прямоугольники рисуются точно (с альфой и отсечением), поэтому фоны, кнопки, переключатели, слайдеры и
 * прогресс-бары видны в реальных цветах и позициях. Текст рисуется тонкой полосой по измеренной ширине
 * (детерминированный маркер позиции) — достаточно, чтобы проверить раскладку и поймать вылезший за бокс текст;
 * настоящий растровый шрифт — задокументированное продолжение. Без зависимостей.
 */
public final class RasterUiRenderer implements UiRenderer {

    private final PixelCanvas canvas;
    private final UiMetrics metrics;

    public RasterUiRenderer(PixelCanvas canvas, UiMetrics metrics) {
        this.canvas = canvas;
        this.metrics = metrics;
    }

    @Override
    public void fillRect(int x, int y, int width, int height, int argb) {
        canvas.fillRect(x, y, width, height, argb);
    }

    @Override
    public void drawText(int x, int y, String text, int argb) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int textWidth = metrics.textWidth(text);
        int barY = y + Math.max(0, metrics.lineHeight() - 2);
        canvas.fillRect(x, barY, textWidth, 2, argb);
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        canvas.pushClip(x, y, width, height);
    }

    @Override
    public void popClip() {
        canvas.popClip();
    }
}
