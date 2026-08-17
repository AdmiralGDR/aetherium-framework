/*
 * Aetherium Framework — headless UI preview (widget tree -> PNG).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.raster;

import org.aetherium.ui.FlexLayout;
import org.aetherium.ui.LaidOut;
import org.aetherium.ui.Rect;
import org.aetherium.ui.UiMetrics;
import org.aetherium.ui.UiRuntime;
import org.aetherium.ui.Widget;

/**
 * Renders a widget tree to a PNG with no game and no GPU — the headless preview for design review and
 * byte-stable golden-image regression.
 *
 * <p>EN: Lays the tree out with the real {@link FlexLayout}, paints it with the real {@code UiRuntime.paint}
 * into a {@link PixelCanvas} through a {@link RasterUiRenderer}, and encodes the result with the zero-dependency
 * {@link PngWriter}. Because every step is the same code the game runs, the preview reflects the actual layout;
 * because it is deterministic, the PNG bytes are stable enough to diff in CI. A CLI or a test writes the bytes
 * to a file. Zero-dependency (JDK only).
 * RU: Раскладывает дерево настоящим {@link FlexLayout}, рисует настоящим {@code UiRuntime.paint} в
 * {@link PixelCanvas} через {@link RasterUiRenderer} и кодирует результат {@link PngWriter} без зависимостей.
 * Поскольку это тот же код, что и в игре, превью отражает реальную раскладку; поскольку он детерминирован,
 * байты PNG стабильны для сравнения в CI. Только JDK.
 */
public final class UiPreview {

    private UiPreview() {
    }

    /** Render {@code root} laid out into a {@code width}×{@code height} viewport over {@code backgroundArgb}. */
    public static byte[] renderPng(Widget<?> root, int width, int height, UiMetrics metrics, int backgroundArgb) {
        Rect viewport = new Rect(0, 0, width, height);
        LaidOut laid = FlexLayout.layout(root, viewport, metrics);
        PixelCanvas canvas = new PixelCanvas(width, height, backgroundArgb);
        RasterUiRenderer renderer = new RasterUiRenderer(canvas, metrics);
        UiRuntime.paint(laid, renderer, metrics);
        return PngWriter.encode(canvas.toRgbaBytes(), width, height);
    }
}
