/*
 * Aetherium Framework — AppCDS / zero-parse transformed-class cache.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import org.aetherium.core.io.MappedRegion;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two-layer "zero-parse" caching for the transformation pipeline — the framework's AppCDS strategy.
 *
 * <p>EN: Re-running ASM ({@code ClassReader} → tree → {@code COMPUTE_FRAMES} → {@code CheckClassAdapter}
 * verify) for every modified class on every launch is the dominant load-phase cost. This manager
 * eliminates it across launches:
 *
 * <ol>
 *   <li><b>Memory-mapped transformed-class archive (active here):</b> the final transformed bytes are
 *       persisted to a blob keyed by {@code className + hash(originalBytes)}. On the next launch the blob
 *       is {@code mmap}'d ({@link MappedRegion}) and a hit returns the cached bytes with a single slice
 *       copy — <em>the entire ASM pipeline is skipped</em>. The hash key means a NeoForge/MC update that
 *       changes a class automatically invalidates only the stale entries.</li>
 *   <li><b>JVM AppCDS (.jsa) hook:</b> {@link #writeAppCdsLaunchHints(Path)} emits the class list and the
 *       exact {@code -XX:SharedArchiveFile} / {@code -XX:+AutoCreateSharedArchive} flags so the launcher
 *       can enable the JDK's own Application Class-Data Sharing — letting the JVM memory-map the parsed,
 *       verified class metadata of the whole space and bypass even classfile parsing.</li>
 * </ol>
 *
 * Safe by construction: any I/O problem disables the cache for the run (returns misses, logs once) and
 * never throws into the transform path.
 *
 * <p>RU: Повторный прогон ASM для каждого изменённого класса при каждом запуске — доминирующая
 * стоимость фазы загрузки. Этот менеджер устраняет её между запусками: (1) <b>memory-mapped архив
 * преобразованных классов</b> — итоговые байты сохраняются в blob с ключом
 * {@code имя + hash(исходных байт)}; при следующем запуске blob отображается в память
 * ({@link MappedRegion}), и попадание возвращает кэшированные байты одним копированием среза — <em>весь
 * конвейер ASM пропускается</em>; ключ-хэш автоматически инвалидирует только устаревшие записи при
 * обновлении NeoForge/MC. (2) <b>хук JVM AppCDS (.jsa)</b> — {@link #writeAppCdsLaunchHints(Path)}
 * пишет список классов и точные флаги {@code -XX:SharedArchiveFile}/{@code -XX:+AutoCreateSharedArchive},
 * чтобы запускающий слой включил собственный AppCDS JDK. Безопасен по построению: любая I/O-проблема
 * отключает кэш на запуск и никогда не бросает в путь трансформации.
 */
public final class AppCdsManager {

    // java.util.logging (not SLF4J): AppCdsManager is exercised both in-game (loader) and headlessly by
    // the CLI, where the platform's SLF4J binding is absent — JUL is always present.
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger("Aetherium.AppCDS");
    private static final String INDEX_FILE = "aetherium-cds.idx";
    private static final String BLOB_FILE = "aetherium-cds.blob";
    private static final String FLAGS_FILE = "aetherium-appcds.flags";
    private static final String CLASSLIST_FILE = "aetherium-appcds.classlist";
    private static final String JSA_FILE = "aetherium.jsa";

    private final Path dir;
    private final Path indexPath;
    private final Path blobPath;

    /** key = "internalName#originalHashHex" -> [absoluteOffset, length] */
    private final Map<String, long[]> index = new LinkedHashMap<>();
    private final MappedRegion existingBlob;       // mmap of the prior-run blob (read-only); may be null
    private final long existingBlobSize;
    private final java.io.ByteArrayOutputStream newBlob = new java.io.ByteArrayOutputStream();

    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger misses = new AtomicInteger();
    private final AtomicInteger stores = new AtomicInteger();
    private volatile boolean healthy = true;

    private AppCdsManager(Path dir, MappedRegion existingBlob, long existingBlobSize) {
        this.dir = dir;
        this.indexPath = dir.resolve(INDEX_FILE);
        this.blobPath = dir.resolve(BLOB_FILE);
        this.existingBlob = existingBlob;
        this.existingBlobSize = existingBlobSize;
    }

    /** Open (or create) the cache under {@code dir}. Never throws — returns a disabled manager on error. */
    public static AppCdsManager open(Path dir) {
        try {
            Files.createDirectories(dir);
            Path blob = dir.resolve(BLOB_FILE);
            MappedRegion mapped = null;
            long size = 0;
            if (Files.isRegularFile(blob) && Files.size(blob) > 0) {
                mapped = MappedRegion.mapReadOnly(blob);
                size = mapped.byteSize();
            }
            AppCdsManager m = new AppCdsManager(dir, mapped, size);
            m.loadIndex();
            LOG.info("AppCDS cache at " + dir + " (" + m.index.size()
                    + " cached transformed class(es), " + (size / 1024) + " KiB archive).");
            return m;
        } catch (Throwable io) {
            LOG.warning("AppCDS cache disabled (open failed: " + io + ").");
            AppCdsManager disabled = new AppCdsManager(dir, null, 0);
            disabled.healthy = false;
            return disabled;
        }
    }

    /** Cache key for a class: its name plus a fast 64-bit hash of the ORIGINAL bytes (auto-invalidation). */
    static String key(String internalName, byte[] original) {
        long h = 0xcbf29ce484222325L; // FNV-1a 64
        for (byte b : original) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return internalName + "#" + Long.toHexString(h);
    }

    /** Zero-parse lookup: the cached transformed bytes for this exact original, or {@code null} on miss. */
    synchronized byte[] lookup(String internalName, byte[] original) {
        if (!healthy) {
            return null;
        }
        long[] loc = index.get(key(internalName, original));
        if (loc == null) {
            misses.incrementAndGet();
            return null;
        }
        try {
            long offset = loc[0];
            int len = (int) loc[1];
            byte[] out = new byte[len];
            if (offset < existingBlobSize && existingBlob != null) {
                MemorySegment slice = existingBlob.segment().asSlice(offset, len);
                MemorySegment.copy(slice, ValueLayout.JAVA_BYTE, 0, out, 0, len);
            } else {
                // an entry stored earlier in THIS run (not yet flushed to the mmap'd file)
                byte[] buf = newBlob.toByteArray();
                System.arraycopy(buf, (int) (offset - existingBlobSize), out, 0, len);
            }
            hits.incrementAndGet();
            return out;
        } catch (Throwable bad) {
            healthy = false;
            LOG.warning("AppCDS lookup failed; disabling cache for this run (" + bad + ").");
            return null;
        }
    }

    /** Record the transformed bytes for a class so the next launch skips the ASM pipeline. */
    synchronized void record(String internalName, byte[] original, byte[] transformed) {
        if (!healthy) {
            return;
        }
        String k = key(internalName, original);
        if (index.containsKey(k)) {
            return;
        }
        long offset = existingBlobSize + newBlob.size();
        newBlob.writeBytes(transformed);
        index.put(k, new long[] {offset, transformed.length});
        stores.incrementAndGet();
    }

    /** Persist new entries and the AppCDS launch hints. Never throws. */
    synchronized void flush() {
        if (!healthy) {
            return;
        }
        try {
            if (newBlob.size() > 0) {
                Files.write(blobPath, newBlob.toByteArray(),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            }
            StringBuilder idx = new StringBuilder();
            for (Map.Entry<String, long[]> e : index.entrySet()) {
                idx.append(e.getKey()).append('\t')
                   .append(e.getValue()[0]).append('\t')
                   .append(e.getValue()[1]).append('\n');
            }
            Files.writeString(indexPath, idx.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            writeAppCdsLaunchHints(dir);
            LOG.info("AppCDS flush: " + hits.get() + " hit(s), " + misses.get() + " miss(es), "
                    + stores.get() + " stored; archive now " + index.size() + " entries.");
        } catch (Throwable io) {
            LOG.warning("AppCDS flush failed (" + io + ").");
        }
    }

    /**
     * Emit the class list and the exact JVM flags to enable the JDK's own AppCDS on the next launch.
     * The launcher (or the user) adds the printed flags; the JVM then mmaps the parsed/verified class
     * metadata of the whole space, bypassing even classfile parsing.
     */
    void writeAppCdsLaunchHints(Path dir) throws IOException {
        StringBuilder classes = new StringBuilder();
        for (String k : index.keySet()) {
            classes.append(k.substring(0, k.lastIndexOf('#'))).append('\n');
        }
        Files.writeString(dir.resolve(CLASSLIST_FILE), classes.toString());

        Path jsa = dir.resolve(JSA_FILE);
        String flags = "# Aetherium AppCDS — add these JVM flags to bypass classfile parsing on launch:\n"
                + "-XX:+AutoCreateSharedArchive\n"
                + "-XX:SharedArchiveFile=" + jsa.toAbsolutePath() + "\n"
                + "-XX:SharedClassListFile=" + dir.resolve(CLASSLIST_FILE).toAbsolutePath() + "\n";
        Files.writeString(dir.resolve(FLAGS_FILE), flags);
    }

    /** Snapshot for diagnostics/CLI. */
    public Stats stats() {
        return new Stats(dir, index.size(), existingBlobSize, hits.get(), misses.get(), stores.get(), healthy);
    }

    /** The cache directory this manager manages (default {@code ${user.dir}/.aetherium/cds}). */
    public static Path defaultDir() {
        String configured = System.getProperty("aetherium.cds.dir");
        return configured != null
                ? Path.of(configured)
                : Path.of(System.getProperty("user.dir"), ".aetherium", "cds");
    }

    public record Stats(Path dir, int entries, long archiveBytes, int hits, int misses, int stores, boolean healthy) {
        public List<String> lines() {
            return List.of(
                    "cache dir      : " + dir,
                    "cached classes : " + entries,
                    "archive size   : " + (archiveBytes / 1024) + " KiB (mmap'd)",
                    "this run       : " + hits + " hit(s), " + misses + " miss(es), " + stores + " stored",
                    "status         : " + (healthy ? "healthy" : "disabled"));
        }
    }

    private void loadIndex() throws IOException {
        if (!Files.isRegularFile(indexPath)) {
            return;
        }
        for (String line : Files.readAllLines(indexPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t");
            if (parts.length == 3) {
                index.put(parts[0], new long[] {Long.parseLong(parts[1]), Long.parseLong(parts[2])});
            }
        }
    }
}
