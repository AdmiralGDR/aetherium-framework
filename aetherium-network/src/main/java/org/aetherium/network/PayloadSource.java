/*
 * Aetherium Framework — network payload source.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import java.lang.foreign.MemorySegment;

/**
 * Loader-agnostic read side of a packet buffer (mirror of {@link PayloadSink}).
 *
 * <p>EN: {@link #readSegment} reads bytes straight into a destination off-heap {@link MemorySegment}
 * (e.g. a client {@code StructArena}'s memory) with no intermediate heap copy — the zero-GC receive path.
 *
 * <p>RU: {@link #readSegment} читает байты прямо в off-heap {@link MemorySegment} назначения (напр.
 * память клиентской {@code StructArena}) без промежуточной кучи — приём без аллокаций.
 */
public interface PayloadSource {

    int readInt();

    long readLong();

    /** Bulk-read {@code length} bytes from the buffer into an off-heap segment (zero-GC). */
    void readSegment(MemorySegment destination, long length);
}
