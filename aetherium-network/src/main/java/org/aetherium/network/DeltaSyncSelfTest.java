/*
 * Aetherium Framework — delta-sync networking self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructField;
import org.aetherium.core.compute.StructLayout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves delta-sync sends only changed rows and reconstructs the client arena exactly.
 *
 * <p>EN: Builds a server entity arena, performs an initial full sync (all rows), then mutates only a few
 * rows and performs a delta sync. It checks that the client arena byte-matches the server after each sync,
 * that the delta's payload equals {@code dirtyRows × stride} (far smaller than the full buffer), and that
 * untouched client rows are preserved. The wire is an in-memory {@link PayloadSink}/{@link PayloadSource}.
 * RU: Строит серверную арену сущностей, выполняет начальную полную синхронизацию (все строки), затем
 * меняет лишь несколько строк и делает дельта-синхронизацию. Проверяет, что клиентская арена побайтово
 * совпадает с серверной после каждой синхронизации, что полезная нагрузка дельты равна
 * {@code dirtyRows × stride} (намного меньше полного буфера) и что нетронутые клиентские строки сохранены.
 */
public final class DeltaSyncSelfTest {

    private static final int ENTITIES = 4096;
    private static final int MOVED = 7;

    private DeltaSyncSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();
        StructLayout layout = StructLayout.builder()
                .floats("x").floats("y").floats("vx").floats("vy").build();
        long stride = layout.stride();

        try (StructArena server = StructArena.allocate(layout, ENTITIES);
             StructArena client = StructArena.allocate(layout, ENTITIES);
             StructArenaDelta delta = new StructArenaDelta(server)) {

            StructField x = layout.field("x");
            StructField vx = layout.field("vx");
            for (int i = 0; i < ENTITIES; i++) {
                server.setFloat(i, x, i);
                server.setFloat(i, vx, 1.0f);
            }

            StructArenaDeltaCodec codec = new StructArenaDeltaCodec(client);

            // --- initial full sync -------------------------------------------------------------
            DirtyBitmap firstDirty = delta.computeDirty(server, ENTITIES);
            long fullBytes = sync(server, codec, ENTITIES, firstDirty);
            boolean firstMatch = segmentsEqual(server, client, ENTITIES, stride);
            notes.add("initial sync: " + firstDirty.cardinality() + " rows, " + fullBytes + " bytes (full state)");

            // --- mutate only a few rows, then delta sync ---------------------------------------
            for (int i = 0; i < MOVED; i++) {
                int row = i * 137 % ENTITIES; // scattered rows
                server.setFloat(row, x, 1000.0f + row);
            }
            DirtyBitmap deltaDirty = delta.computeDirty(server, ENTITIES);
            long deltaBytes = sync(server, codec, ENTITIES, deltaDirty);
            boolean deltaMatch = segmentsEqual(server, client, ENTITIES, stride);
            notes.add("delta sync: " + deltaDirty.cardinality() + " dirty rows, " + deltaBytes
                    + " bytes (vs " + fullBytes + " full)");

            int savingsPercent = fullBytes == 0 ? 0 : (int) (100 - (deltaBytes * 100 / fullBytes));
            notes.add("bandwidth saved on the delta tick: " + savingsPercent + "%");

            boolean passed = firstMatch && deltaMatch
                    && deltaDirty.cardinality() == MOVED
                    && deltaBytes < fullBytes
                    && deltaBytes == (long) MOVED * stride;

            return new Result(ENTITIES, fullBytes, deltaDirty.cardinality(), deltaBytes,
                    savingsPercent, firstMatch, deltaMatch, notes, passed);
        }
    }

    /** Encode the current dirty set to an in-memory wire and decode it into the client; returns bytes sent. */
    private static long sync(StructArena server, StructArenaDeltaCodec codec, int rowCount, DirtyBitmap dirty) {
        StructArenaDeltaPacket packet = new StructArenaDeltaPacket(server, rowCount, dirty);
        BufferSink sink = new BufferSink();
        codec.encode(packet, sink);
        byte[] wire = sink.toByteArray();
        codec.decode(new BufferSource(wire));
        return packet.payloadBytes();
    }

    private static boolean segmentsEqual(StructArena a, StructArena b, int rows, long stride) {
        long bytes = (long) rows * stride;
        return a.segment().asSlice(0, bytes).mismatch(b.segment().asSlice(0, bytes)) == -1;
    }

    /** In-memory {@link PayloadSink} over a byte stream (test/CLI harness, not the game wire). */
    private static final class BufferSink implements PayloadSink {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(bytes);

        @Override
        public void writeInt(int value) {
            try {
                out.writeInt(value);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeLong(long value) {
            try {
                out.writeLong(value);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeSegment(MemorySegment source, long length) {
            byte[] chunk = source.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE);
            try {
                out.write(chunk);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }

    /** In-memory {@link PayloadSource} mirror of {@link BufferSink}. */
    private static final class BufferSource implements PayloadSource {
        private final DataInputStream in;

        BufferSource(byte[] wire) {
            this.in = new DataInputStream(new ByteArrayInputStream(wire));
        }

        @Override
        public int readInt() {
            try {
                return in.readInt();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public long readLong() {
            try {
                return in.readLong();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void readSegment(MemorySegment destination, long length) {
            byte[] chunk = new byte[(int) length];
            try {
                in.readFully(chunk);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            MemorySegment.copy(MemorySegment.ofArray(chunk), 0L, destination, 0L, length);
        }
    }

    /** Outcome of the delta-sync self-test, rendered by the CLI {@code delta} command. */
    public record Result(int entities, long fullBytes, int deltaDirtyRows, long deltaBytes,
                         int savingsPercent, boolean firstSyncMatched, boolean deltaSyncMatched,
                         List<String> notes, boolean passed) {
    }
}
