/*
 * Aetherium Framework — delta-sync packet (dirty rows only).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.aetherium.core.compute.StructArena;

/**
 * A bandwidth-efficient sibling of {@link StructArenaSyncPacket} that ships only the rows that changed.
 *
 * <p>EN: Where {@link StructArenaSyncPacket} sends all {@code rowCount × stride} bytes every tick, this
 * carries a {@link DirtyBitmap} alongside the arena and transmits only the dirty rows' bytes (coalesced
 * into contiguous runs by {@link StructArenaDeltaCodec}). For a world where a handful of entities move per
 * tick, the payload collapses from the whole buffer to a few rows plus a tiny bitmap — the same zero-GC
 * off-heap path, far less wire traffic.
 *
 * <p>RU: Если {@link StructArenaSyncPacket} шлёт все {@code rowCount × stride} байт каждый тик, этот несёт
 * {@link DirtyBitmap} рядом с ареной и передаёт только байты грязных строк (объединённые в непрерывные
 * пробеги в {@link StructArenaDeltaCodec}). Для мира, где за тик двигается горстка сущностей, полезная
 * нагрузка схлопывается с целого буфера до нескольких строк плюс крошечная битовая карта.
 */
public final class StructArenaDeltaPacket implements NetworkPayload {

    public static final String CHANNEL = "aetherium:struct_arena_delta";

    private final StructArena arena;
    private final int rowCount;
    private final DirtyBitmap dirty;

    public StructArenaDeltaPacket(StructArena arena, int rowCount, DirtyBitmap dirty) {
        this.arena = arena;
        this.rowCount = rowCount;
        this.dirty = dirty;
    }

    @Override
    public String channelId() {
        return CHANNEL;
    }

    public StructArena arena() {
        return arena;
    }

    public int rowCount() {
        return rowCount;
    }

    public DirtyBitmap dirty() {
        return dirty;
    }

    /** Off-heap bytes this delta actually transmits ({@code dirtyRows × stride}). */
    public long payloadBytes() {
        return (long) dirty.cardinality() * arena.layout().stride();
    }

    /** Bytes a full {@link StructArenaSyncPacket} would have sent, for savings reporting. */
    public long fullBytes() {
        return (long) rowCount * arena.layout().stride();
    }
}
