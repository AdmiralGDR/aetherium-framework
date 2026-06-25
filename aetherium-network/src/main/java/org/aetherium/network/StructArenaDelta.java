/*
 * Aetherium Framework — per-tick delta computation for a StructArena.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.aetherium.core.compute.StructArena;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Tracks the last-sent image of a {@link StructArena} and computes which rows changed each tick.
 *
 * <p>EN: The server holds one of these per synced arena. After the {@code AetheriumTickEngine} advances
 * entities, {@link #computeDirty(StructArena, int)} compares each row to a confined off-heap "shadow" of
 * what was last transmitted (row equality via {@link MemorySegment#mismatch}), records the changed rows in
 * a {@link DirtyBitmap}, and refreshes the shadow. The result feeds {@link StructArenaDeltaCodec}, so only
 * the bytes that actually changed during the last cycle go on the wire — not the whole buffer. The shadow
 * is off-heap, so diffing thousands of entities allocates nothing on the Java heap.
 *
 * <p>RU: Сервер держит по одному на каждую синхронизируемую арену. После того как
 * {@code AetheriumTickEngine} продвинул сущности, {@link #computeDirty(StructArena, int)} сравнивает
 * каждую строку с ограниченной off-heap «тенью» последнего переданного состояния (равенство строк через
 * {@link MemorySegment#mismatch}), записывает изменённые строки в {@link DirtyBitmap} и обновляет тень.
 * Результат питает {@link StructArenaDeltaCodec}, поэтому на провод уходят только реально изменившиеся
 * за цикл байты — не весь буфер. Тень off-heap, поэтому диф тысяч сущностей не аллоцирует в куче Java.
 */
public final class StructArenaDelta implements AutoCloseable {

    private final Arena shadowArena = Arena.ofShared();
    private final MemorySegment shadow;
    private final long stride;
    private final long rowCapacity;
    private boolean primed;

    public StructArenaDelta(StructArena reference) {
        this.stride = reference.layout().stride();
        this.rowCapacity = reference.count();
        this.shadow = shadowArena.allocate(reference.byteSize() == 0 ? 1 : reference.byteSize());
    }

    /**
     * EN: Compare the first {@code rowCount} rows of {@code current} against the shadow, returning a
     * {@link DirtyBitmap} of the changed rows and updating the shadow to match. The first call (un-primed)
     * marks every row dirty — the initial full state must be sent once.
     * RU: Сравнить первые {@code rowCount} строк {@code current} с тенью, вернув {@link DirtyBitmap}
     * изменённых строк и обновив тень. Первый вызов помечает все строки грязными — начальное полное
     * состояние нужно отправить один раз.
     */
    public DirtyBitmap computeDirty(StructArena current, int rowCount) {
        if (rowCount < 0 || rowCount > rowCapacity) {
            throw new IndexOutOfBoundsException("rowCount " + rowCount + " out of capacity " + rowCapacity);
        }
        DirtyBitmap dirty = new DirtyBitmap(rowCount);
        MemorySegment src = current.segment();
        if (!primed) {
            // First sync: everything is "new". Mark all and snapshot.
            for (int row = 0; row < rowCount; row++) {
                dirty.mark(row);
            }
            if (rowCount > 0) {
                MemorySegment.copy(src, 0L, shadow, 0L, (long) rowCount * stride);
            }
            primed = true;
            return dirty;
        }
        for (int row = 0; row < rowCount; row++) {
            long offset = row * stride;
            MemorySegment curRow = src.asSlice(offset, stride);
            MemorySegment shadowRow = shadow.asSlice(offset, stride);
            if (curRow.mismatch(shadowRow) != -1) {
                dirty.mark(row);
                MemorySegment.copy(curRow, 0L, shadowRow, 0L, stride);
            }
        }
        return dirty;
    }

    public long stride() {
        return stride;
    }

    @Override
    public void close() {
        shadowArena.close();
    }
}
