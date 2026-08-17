/*
 * Aetherium Framework — deterministic simulation-state checksum.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network.sim;

import org.aetherium.core.compute.StructArena;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A deterministic 64-bit fingerprint of a simulation's whole state — the desync detector for lockstep and
 * rollback netcode.
 *
 * <p>EN: Two peers running the <em>same</em> deterministic simulation over the same inputs must reach
 * byte-identical {@link StructArena} state every tick; if their checksums ever differ, they have desynced and
 * must be told (never silently drift apart). This is a plain FNV-1a over the arena's raw bytes — order-stable
 * and dependency-free, so the same bytes always hash the same on any machine. Cheap enough to run per tick.
 * RU: Два узла, исполняющие <em>одну и ту же</em> детерминированную симуляцию на одинаковых входах, обязаны
 * достигать побайтово идентичного состояния {@link StructArena} на каждом тике; если контрольные суммы
 * разошлись — рассинхрон, и об этом нужно сообщить (никогда не расходиться молча). Это простой FNV-1a по
 * сырым байтам арены — стабилен и без зависимостей, одинаковые байты дают одинаковый хеш на любой машине.
 */
public final class StateChecksum {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private StateChecksum() {
    }

    /** FNV-1a 64 over the arena's whole off-heap byte range. */
    public static long of(StructArena arena) {
        MemorySegment segment = arena.segment();
        long size = arena.byteSize();
        long hash = FNV_OFFSET_BASIS;
        for (long i = 0; i < size; i++) {
            hash = (hash ^ (segment.get(ValueLayout.JAVA_BYTE, i) & 0xFF)) * FNV_PRIME;
        }
        return hash;
    }

    /** FNV-1a 64 over a heap byte snapshot (e.g. a reconstructed past state). */
    public static long ofBytes(byte[] bytes) {
        long hash = FNV_OFFSET_BASIS;
        for (byte b : bytes) {
            hash = (hash ^ (b & 0xFF)) * FNV_PRIME;
        }
        return hash;
    }
}
