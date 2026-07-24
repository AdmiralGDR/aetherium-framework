/*
 * Aetherium Framework — NeoForge world-scoped persistence (PAL implementation).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.aetherium.network.PayloadSink;
import org.aetherium.network.PayloadSource;
import org.aetherium.network.TreeCodec;
import org.aetherium.network.TreeNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

/**
 * The NeoForge-backed {@link org.aetherium.edge.WorldStore} — atomic, per-world persistence of
 * {@link TreeNode} documents.
 *
 * <p>EN: Documents live under {@code <world>/aetherium/<modId>/<key>.dat}, encoded with the
 * depth/size-hardened {@link TreeCodec}. Writes go to a {@code .tmp} sibling then {@code ATOMIC_MOVE} over the
 * target, so a crash mid-write never corrupts the saved state (the ACID durability rule). The world root
 * comes from {@code server.getWorldPath(LevelResource.ROOT)}, which a pure mod cannot know — exactly why this
 * lives loader-side. {@code modId} and {@code key} are sanitized so a key can never escape the mod's folder.
 *
 * <p>RU: Документы лежат в {@code <world>/aetherium/<modId>/<key>.dat}, кодируются устойчивым
 * {@link TreeCodec}. Запись идёт в {@code .tmp}-сосед, затем {@code ATOMIC_MOVE} поверх цели — сбой посреди
 * записи не портит состояние. Корень мира берётся из {@code server.getWorldPath(LevelResource.ROOT)}.
 */
final class NeoForgeWorldStore implements org.aetherium.edge.WorldStore {

    private final Path baseDir;

    private NeoForgeWorldStore(Path baseDir) {
        this.baseDir = baseDir;
    }

    /** Build a store rooted at the active server's world directory, or {@code null} if no server yet. */
    static NeoForgeWorldStore forServer(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        Path root = server.getWorldPath(LevelResource.ROOT).resolve("aetherium");
        return new NeoForgeWorldStore(root);
    }

    @Override
    public Optional<TreeNode> read(String modId, String key) {
        Path file = fileFor(modId, key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            PayloadSource source = new ByteSource(new DataInputStream(new ByteArrayInputStream(bytes)));
            return Optional.of(TreeCodec.decode(source));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read world store entry " + file, e);
        }
    }

    @Override
    public void write(String modId, String key, TreeNode data) {
        Objects.requireNonNull(data, "data");
        Path file = fileFor(modId, key);
        try {
            Files.createDirectories(file.getParent());
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            TreeCodec.encode(data, new ByteSink(new DataOutputStream(raw)));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, raw.toByteArray());
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                // Some filesystems can't do an atomic replace; fall back to a plain replace.
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write world store entry " + file, e);
        }
    }

    /** Resolve {@code (modId, key)} to a file, sanitizing each segment so it can't escape the base dir. */
    private Path fileFor(String modId, String key) {
        Path dir = baseDir.resolve(sanitizeSegment(Objects.requireNonNull(modId, "modId")));
        String[] parts = Objects.requireNonNull(key, "key").split("/");
        Path p = dir;
        boolean appended = false;
        for (String part : parts) {
            String s = sanitizeSegment(part);
            if (!s.isEmpty()) {
                p = p.resolve(s);
                appended = true;
            }
        }
        if (!appended) {
            p = dir.resolve("data");
        }
        return p.resolveSibling(p.getFileName() + ".dat");
    }

    /** Keep only safe filename characters; collapse traversal attempts to nothing. */
    private static String sanitizeSegment(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            }
        }
        String s = sb.toString();
        return s.equals(".") || s.equals("..") ? "" : s;
    }

    // --- byte-array PayloadSink/Source over Data{Output,Input}Stream ----------------------------

    private record ByteSink(DataOutputStream out) implements PayloadSink {
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
                out.write(source.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private record ByteSource(DataInputStream in) implements PayloadSource {
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
                MemorySegment.copy(MemorySegment.ofArray(b), 0, destination, 0, length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
