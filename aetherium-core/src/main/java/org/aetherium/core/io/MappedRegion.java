/*
 * Aetherium Framework — memory-mapped region (zero-GC streaming).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.io;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * A memory-mapped file region exposed as an FFM {@link MemorySegment} — zero-GC chunk/asset streaming.
 *
 * <p>EN: Maps a file (or a slice of it) directly into memory with {@link FileChannel#map(FileChannel.MapMode,
 * long, long, Arena)}. The OS pages data in on demand, so streaming a multi-gigabyte chunk/asset file
 * costs no Java heap and produces no GC pressure — reads go straight to the mapped pages. The mapping's
 * lifetime is tied to an {@link Arena}: {@link #close()} unmaps deterministically (no relying on GC to
 * release the mapping). Ideal for region files, texture atlases, and bulk asset loads.
 *
 * <p>RU: Отображает файл (или его срез) прямо в память через {@link FileChannel#map(FileChannel.MapMode,
 * long, long, Arena)}. ОС подгружает страницы по требованию, поэтому потоковая обработка многогигабайтного
 * файла чанков/ассетов не стоит Java-кучи и не создаёт давления на GC — чтения идут прямо в
 * отображённые страницы. Время жизни отображения привязано к {@link Arena}: {@link #close()}
 * детерминированно снимает отображение (без опоры на GC).
 */
public final class MappedRegion implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment segment;
    private final Path path;

    private MappedRegion(Arena arena, MemorySegment segment, Path path) {
        this.arena = arena;
        this.segment = segment;
        this.path = path;
    }

    /** Map the whole file read-only. */
    public static MappedRegion mapReadOnly(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Arena arena = Arena.ofShared();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
            return new MappedRegion(arena, segment, file);
        } catch (IOException | RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /** The mapped data as a {@link MemorySegment}. */
    public MemorySegment segment() {
        return segment;
    }

    public long byteSize() {
        return segment.byteSize();
    }

    public Path path() {
        return path;
    }

    /** Read a single byte at {@code offset} (bounds-checked by FFM). */
    public byte readByte(long offset) {
        return segment.get(ValueLayout.JAVA_BYTE, offset);
    }

    /** Read a big-endian int at {@code offset}. */
    public int readInt(long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset);
    }

    @Override
    public void close() {
        arena.close(); // unmaps the region deterministically
    }
}
