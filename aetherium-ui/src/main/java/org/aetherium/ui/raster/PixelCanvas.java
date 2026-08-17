/*
 * Aetherium Framework — off-screen ARGB pixel canvas (headless rasterizer).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.raster;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * A software ARGB frame buffer with clipped, alpha-blended rectangle fill — the headless drawing surface a UI
 * is rasterized into for a preview or a golden-image test, with no GPU and no platform.
 *
 * <p>EN: Row-major {@code 0xAARRGGBB} pixels. {@link #fillRect} composites source-over within the current clip
 * (a stack, intersected on push), so a scroll panel clips exactly as it does in-game and a translucent scrim
 * darkens what is under it. Fully deterministic — the same draw calls always yield the same pixels — which is
 * what makes a byte-exact PNG regression possible. Zero-dependency (JDK only). Not thread-safe.
 * RU: Пиксели {@code 0xAARRGGBB} по строкам. {@link #fillRect} накладывает source-over в текущем отсечении
 * (стек, пересекается при push), поэтому scroll-панель отсекает как в игре, а полупрозрачный scrim затемняет
 * под собой. Полностью детерминирован — одинаковые вызовы дают одинаковые пиксели, что и делает возможной
 * побайтовую регрессию PNG. Без зависимостей (только JDK). Не потокобезопасен.
 */
public final class PixelCanvas {

    private final int width;
    private final int height;
    private final int[] argb;
    private final Deque<int[]> clipStack = new ArrayDeque<>();
    private int clipX0;
    private int clipY0;
    private int clipX1; // exclusive
    private int clipY1; // exclusive

    public PixelCanvas(int width, int height, int backgroundArgb) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("canvas must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
        Arrays.fill(argb, backgroundArgb);
        resetClip();
    }

    private void resetClip() {
        clipX0 = 0;
        clipY0 = 0;
        clipX1 = width;
        clipY1 = height;
    }

    /** Intersect the clip with {@code (x,y,w,h)} and push the previous clip. */
    public void pushClip(int x, int y, int w, int h) {
        clipStack.push(new int[] {clipX0, clipY0, clipX1, clipY1});
        clipX0 = Math.max(clipX0, x);
        clipY0 = Math.max(clipY0, y);
        clipX1 = Math.min(clipX1, x + w);
        clipY1 = Math.min(clipY1, y + h);
    }

    /** Restore the clip saved by the matching {@link #pushClip}. */
    public void popClip() {
        if (clipStack.isEmpty()) {
            resetClip();
            return;
        }
        int[] c = clipStack.pop();
        clipX0 = c[0];
        clipY0 = c[1];
        clipX1 = c[2];
        clipY1 = c[3];
    }

    /** Composite {@code color} (source-over) over the rectangle, clipped to the current clip and the canvas. */
    public void fillRect(int x, int y, int w, int h, int color) {
        int x0 = Math.max(x, clipX0);
        int y0 = Math.max(y, clipY0);
        int x1 = Math.min(x + w, clipX1);
        int y1 = Math.min(y + h, clipY1);
        for (int py = y0; py < y1; py++) {
            int row = py * width;
            for (int px = x0; px < x1; px++) {
                argb[row + px] = blend(argb[row + px], color);
            }
        }
    }

    /** Source-over composite of {@code src} onto {@code dst}, both {@code 0xAARRGGBB}. */
    static int blend(int dst, int src) {
        int sa = (src >>> 24) & 0xFF;
        if (sa == 0) {
            return dst;
        }
        if (sa == 255) {
            return src;
        }
        int da = (dst >>> 24) & 0xFF;
        int outA = sa + da * (255 - sa) / 255;
        if (outA == 0) {
            return 0;
        }
        int outR = channel((src >> 16) & 0xFF, (dst >> 16) & 0xFF, sa, da, outA);
        int outG = channel((src >> 8) & 0xFF, (dst >> 8) & 0xFF, sa, da, outA);
        int outB = channel(src & 0xFF, dst & 0xFF, sa, da, outA);
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int channel(int sc, int dc, int sa, int da, int outA) {
        int num = sc * sa + dc * da * (255 - sa) / 255;
        return Math.min(255, num / outA);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The pixel at {@code (x,y)} as {@code 0xAARRGGBB} (visibility for tests). */
    public int pixel(int x, int y) {
        return argb[y * width + x];
    }

    /** The frame as tightly-packed RGBA bytes (row-major), ready for a PNG encoder. */
    public byte[] toRgbaBytes() {
        byte[] out = new byte[width * height * 4];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int o = i * 4;
            out[o] = (byte) ((p >> 16) & 0xFF);
            out[o + 1] = (byte) ((p >> 8) & 0xFF);
            out[o + 2] = (byte) (p & 0xFF);
            out[o + 3] = (byte) ((p >>> 24) & 0xFF);
        }
        return out;
    }
}
