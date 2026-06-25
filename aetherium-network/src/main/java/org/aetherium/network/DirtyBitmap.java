/*
 * Aetherium Framework — per-row dirty bitmap for delta-sync.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import java.util.Arrays;

/**
 * A compact one-bit-per-row dirty set that drives delta synchronization of a {@link org.aetherium.core.compute.StructArena}.
 *
 * <p>EN: One bit per entity row, packed into {@code long} words. The producer marks a row dirty when it
 * mutates it during a tick; the codec then transmits only the dirty rows. The key operation is
 * {@link #forEachRun}, which coalesces consecutive dirty rows into contiguous {@code (startRow, rowCount)}
 * runs — so the codec ships each run as a single zero-copy off-heap {@code writeSegment}, never a row at a
 * time and never the whole buffer. The bitmap itself is tiny on the wire (one bit per row ≈ 1/512th of a
 * 64-byte struct), so it is sent in full and the receiver reconstructs the identical runs deterministically.
 *
 * <p>RU: Один бит на строку сущности, упакованный в слова {@code long}. Производитель помечает строку
 * «грязной» при её изменении за тик; затем кодек передаёт только грязные строки. Ключевая операция —
 * {@link #forEachRun}, объединяющая подряд идущие грязные строки в непрерывные пробеги
 * {@code (startRow, rowCount)} — поэтому кодек отправляет каждый пробег одним zero-copy off-heap
 * {@code writeSegment}, а не построчно и не весь буфер. Сама битовая карта крошечная на проводе, поэтому
 * передаётся целиком, а получатель детерминированно восстанавливает те же пробеги.
 */
public final class DirtyBitmap {

    private final long[] words;
    private final int rowCapacity;

    public DirtyBitmap(int rowCapacity) {
        if (rowCapacity < 0) {
            throw new IllegalArgumentException("rowCapacity must be >= 0: " + rowCapacity);
        }
        this.rowCapacity = rowCapacity;
        this.words = new long[(rowCapacity + 63) >>> 6];
    }

    /** Wrap pre-existing words (used by the decoder when reconstructing from the wire). */
    public static DirtyBitmap fromWords(long[] words, int rowCapacity) {
        DirtyBitmap b = new DirtyBitmap(rowCapacity);
        System.arraycopy(words, 0, b.words, 0, Math.min(words.length, b.words.length));
        return b;
    }

    public void mark(int row) {
        checkRow(row);
        words[row >>> 6] |= 1L << (row & 63);
    }

    /** Mark every row dirty (a full resync). */
    public void markAll() {
        Arrays.fill(words, -1L);
        // Clear bits past the capacity in the last word so cardinality/iteration stay exact.
        int rem = rowCapacity & 63;
        if (rem != 0 && words.length > 0) {
            words[words.length - 1] = (1L << rem) - 1;
        }
    }

    public boolean isDirty(int row) {
        checkRow(row);
        return (words[row >>> 6] & (1L << (row & 63))) != 0;
    }

    public void clear() {
        Arrays.fill(words, 0L);
    }

    /** Number of dirty rows. */
    public int cardinality() {
        int c = 0;
        for (long w : words) {
            c += Long.bitCount(w);
        }
        return c;
    }

    public int rowCapacity() {
        return rowCapacity;
    }

    public int wordCount() {
        return words.length;
    }

    public long word(int index) {
        return words[index];
    }

    /**
     * EN: Invoke {@code run} once per maximal contiguous block of dirty rows.
     * RU: Вызвать {@code run} один раз на каждый максимальный непрерывный блок грязных строк.
     */
    public void forEachRun(RunConsumer run) {
        int row = 0;
        while (row < rowCapacity) {
            if (!isDirty(row)) {
                row++;
                continue;
            }
            int start = row;
            while (row < rowCapacity && isDirty(row)) {
                row++;
            }
            run.accept(start, row - start);
        }
    }

    private void checkRow(int row) {
        if (row < 0 || row >= rowCapacity) {
            throw new IndexOutOfBoundsException("row " + row + " out of capacity " + rowCapacity);
        }
    }

    /** Receiver of contiguous dirty runs from {@link #forEachRun}. */
    @FunctionalInterface
    public interface RunConsumer {
        void accept(int startRow, int rowCount);
    }
}
