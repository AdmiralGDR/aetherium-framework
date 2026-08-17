/*
 * Aetherium Framework — zero-dependency PNG encoder.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.raster;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Encodes an RGBA frame to a valid PNG using only the JDK ({@link Deflater} + {@link CRC32}) — no image
 * library, honouring the zero-dependency mandate.
 *
 * <p>EN: Writes the 8-byte signature, an {@code IHDR} (8-bit RGBA, no interlace), one {@code IDAT} of the
 * zlib-compressed scanlines (each prefixed with filter byte 0 = None), and {@code IEND}; every chunk carries
 * its CRC-32. Deterministic: the same pixels always produce the same bytes within a runtime, so a preview can
 * back a byte-stable golden test. The result is a standard PNG any viewer opens.
 * RU: Пишет 8-байтовую сигнатуру, {@code IHDR} (8-бит RGBA, без интерлейса), один {@code IDAT} со
 * zlib-сжатыми строками (каждая с фильтр-байтом 0 = None) и {@code IEND}; каждый чанк несёт свой CRC-32.
 * Детерминирован: одинаковые пиксели дают одинаковые байты в рамках запуска. Результат — стандартный PNG.
 */
public final class PngWriter {

    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private PngWriter() {
    }

    /** Encode {@code rgba} ({@code width*height*4} bytes, row-major R,G,B,A) as PNG bytes. */
    public static byte[] encode(byte[] rgba, int width, int height) {
        if (rgba.length != width * height * 4) {
            throw new IllegalArgumentException("rgba length " + rgba.length + " != " + (width * height * 4));
        }
        int stride = width * 4;
        byte[] raw = new byte[height * (stride + 1)];
        for (int y = 0; y < height; y++) {
            raw[y * (stride + 1)] = 0; // filter type None
            System.arraycopy(rgba, y * stride, raw, y * (stride + 1) + 1, stride);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE);

        byte[] ihdr = new byte[13];
        writeInt(ihdr, 0, width);
        writeInt(ihdr, 4, height);
        ihdr[8] = 8;  // bit depth
        ihdr[9] = 6;  // color type: RGBA
        ihdr[10] = 0; // compression: deflate
        ihdr[11] = 0; // filter method: standard
        ihdr[12] = 0; // interlace: none
        writeChunk(out, "IHDR", ihdr);
        writeChunk(out, "IDAT", deflate(raw));
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            bos.write(buffer, 0, n);
        }
        deflater.end();
        return bos.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] length = new byte[4];
        writeInt(length, 0, data.length);
        out.writeBytes(length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        writeInt(crcBytes, 0, (int) crc.getValue());
        out.writeBytes(crcBytes);
    }

    private static void writeInt(byte[] b, int off, int value) {
        b[off] = (byte) (value >>> 24);
        b[off + 1] = (byte) (value >>> 16);
        b[off + 2] = (byte) (value >>> 8);
        b[off + 3] = (byte) value;
    }
}
