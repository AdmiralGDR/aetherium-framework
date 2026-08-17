/*
 * Aetherium Framework — headless rasterizer (PixelCanvas/PngWriter/UiPreview) tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.raster;

import org.aetherium.ui.Container;
import org.aetherium.ui.FlexDirection;
import org.aetherium.ui.Text;
import org.aetherium.ui.UiColor;
import org.aetherium.ui.UiMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RasterTest {

    @Test
    void opaqueFillReplacesAndTransparentFillKeeps() {
        PixelCanvas c = new PixelCanvas(10, 10, 0xFF000000);
        c.fillRect(0, 0, 5, 5, 0xFFFF0000); // opaque red
        assertEquals(0xFFFF0000, c.pixel(2, 2), "opaque fill replaces");
        assertEquals(0xFF000000, c.pixel(7, 7), "untouched pixel keeps the background");

        c.fillRect(0, 0, 10, 10, 0x00FFFFFF); // fully transparent
        assertEquals(0xFFFF0000, c.pixel(2, 2), "a transparent fill changes nothing");
    }

    @Test
    void semiTransparentScrimBlendsSourceOver() {
        PixelCanvas c = new PixelCanvas(4, 4, 0xFF000000);
        c.fillRect(0, 0, 4, 4, 0x80FFFFFF); // 50% white over opaque black -> mid grey
        assertEquals(0xFF808080, c.pixel(1, 1));
    }

    @Test
    void clipConfinesFills() {
        PixelCanvas c = new PixelCanvas(10, 10, 0xFF000000);
        c.pushClip(0, 0, 5, 5);
        c.fillRect(0, 0, 10, 10, 0xFF00FF00); // green, but clipped to 5x5
        c.popClip();
        assertEquals(0xFF00FF00, c.pixel(2, 2), "inside the clip is filled");
        assertEquals(0xFF000000, c.pixel(7, 7), "outside the clip is untouched");
    }

    @Test
    void toRgbaBytesPacksInOrder() {
        PixelCanvas c = new PixelCanvas(1, 1, 0xFF112233);
        assertArrayEquals(new byte[] {0x11, 0x22, 0x33, (byte) 0xFF}, c.toRgbaBytes());
    }

    @Test
    void pngIsValidAndDeterministic() {
        PixelCanvas c = new PixelCanvas(8, 6, 0xFF2F6FED);
        byte[] png = PngWriter.encode(c.toRgbaBytes(), 8, 6);

        // Signature.
        byte[] sig = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        for (int i = 0; i < sig.length; i++) {
            assertEquals(sig[i], png[i], "PNG signature byte " + i);
        }
        // IHDR width/height (big-endian at offsets 16 and 20).
        assertEquals(8, readInt(png, 16));
        assertEquals(6, readInt(png, 20));

        byte[] again = PngWriter.encode(c.toRgbaBytes(), 8, 6);
        assertArrayEquals(png, again, "same pixels encode to identical PNG bytes (golden-stable)");
    }

    @Test
    void uiPreviewRendersADeterministicPng() {
        var root = new Container(FlexDirection.COLUMN)
                .background(UiColor.rgb(0x2F6FED))
                .children(new Text("Settings"));
        byte[] a = UiPreview.renderPng(root, 120, 80, UiMetrics.DEFAULT, 0xFF1C1C1E);
        byte[] b = UiPreview.renderPng(root, 120, 80, UiMetrics.DEFAULT, 0xFF1C1C1E);

        assertEquals((byte) 137, a[0], "starts with the PNG signature");
        assertEquals(120, readInt(a, 16));
        assertEquals(80, readInt(a, 20));
        assertTrue(a.length > 100, "a rendered screen is a non-trivial PNG");
        assertArrayEquals(a, b, "the preview is deterministic");
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }
}
