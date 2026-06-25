/*
 * Aetherium Framework — hierarchical tree-sync self-test (round-trip + hardening).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

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
 * Round-trips a realistic gameplay tree (a faction with members + a skill tree) and proves the codec is
 * hardened against pathological input — all in-memory, no game.
 *
 * <p>EN: Builds a nested {@link TreeNode}, encodes it through {@link TreeSyncCodec} into an in-memory
 * buffer, decodes it back, and asserts structural equality. Then it confirms a tree deeper than
 * {@link TreeCodec#MAX_DEPTH} is rejected (no stack overflow). The CLI {@code tree} command renders it.
 * RU: Строит вложенный {@link TreeNode}, кодирует через {@link TreeSyncCodec} в буфер в памяти,
 * декодирует обратно и проверяет структурное равенство. Затем подтверждает, что дерево глубже
 * {@link TreeCodec#MAX_DEPTH} отвергается (без переполнения стека).
 */
public final class TreeSyncSelfTest {

    private TreeSyncSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();

        // A faction record: scalars + a member list + a nested skill tree + a raw blob.
        TreeNode faction = Tree.object()
                .put("name", "Iron Vanguard")
                .put("level", 7L)
                .put("treasury", 10_500.50)
                .put("open", true)
                .put("members", Tree.list(Tree.of("Steve"), Tree.of("Alex"), Tree.of("Herobrine")))
                .put("skills", Tree.object()
                        .put("mining", 5L)
                        .put("smithing", 3L)
                        .put("combat", Tree.object().put("melee", 8L).put("ranged", 2L).build())
                        .build())
                .put("banner", new byte[]{1, 2, 3, 4, 5})
                .build();

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        TreeSyncCodec codec = new TreeSyncCodec();
        codec.encode(new TreeSyncPacket(faction), new BufferSink(new DataOutputStream(raw)));
        byte[] wire = raw.toByteArray();

        TreeSyncPacket decoded = codec.decode(
                new BufferSource(new DataInputStream(new ByteArrayInputStream(wire))));

        boolean roundTripOk = faction.equals(decoded.root());
        notes.add("encoded faction tree → " + wire.length + " bytes; round-trip equal=" + roundTripOk);

        // Spot-check a couple of decoded values through the typed accessors.
        boolean accessorsOk = false;
        if (decoded.root() instanceof TreeNode.Obj o) {
            long level = o.getLong("level", -1);
            String name = o.getString("name", "?");
            boolean open = o.getBool("open", false);
            accessorsOk = level == 7 && name.equals("Iron Vanguard") && open;
            notes.add("typed accessors: name='" + name + "' level=" + level + " open=" + open);
        }

        // Hardening: a tree deeper than MAX_DEPTH must be rejected, not overflow the stack.
        boolean depthGuarded;
        try {
            TreeNode deep = Tree.of("leaf");
            for (int i = 0; i < TreeCodec.MAX_DEPTH + 50; i++) {
                deep = Tree.object().put("child", deep).build();
            }
            codec.encode(new TreeSyncPacket(deep), new BufferSink(new DataOutputStream(new ByteArrayOutputStream())));
            depthGuarded = false;
        } catch (IllegalStateException expected) {
            depthGuarded = true;
            notes.add("depth guard rejected an over-deep tree: " + expected.getMessage());
        }

        boolean passed = roundTripOk && accessorsOk && depthGuarded;
        return new Result(roundTripOk, accessorsOk, depthGuarded, wire.length, notes, passed);
    }

    /** Outcome of the tree-sync self-test, rendered by the CLI {@code tree} command. */
    public record Result(boolean roundTripOk, boolean accessorsOk, boolean depthGuarded,
                         int wireBytes, List<String> notes, boolean passed) {
    }

    // --- in-memory PayloadSink/Source over Data{Output,Input}Stream ----------------------------

    private record BufferSink(DataOutputStream out) implements PayloadSink {
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
            try {
                byte[] b = source.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE);
                out.write(b);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private record BufferSource(DataInputStream in) implements PayloadSource {
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
            try {
                byte[] b = in.readNBytes((int) length);
                MemorySegment.copy(MemorySegment.ofArray(b), 0L, destination, 0L, length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
