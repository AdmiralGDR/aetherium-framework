/*
 * Aetherium Framework — typed config store (JSON-over-TreeNode, atomic, hot-reloadable).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.config;

import org.aetherium.network.TreeNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A framework-blessed, typed configuration store — so no mod re-implements JSON loading, validation, atomic
 * writing, and hot-reload ever again (the a downstream mod feedback: ~600 lines removed from every consumer).
 *
 * <p>EN: A {@code ConfigStore<T>} maps a value of the mod's own type {@code T} to a human-editable JSON file
 * through a {@link Codec} (over the hardened {@link TreeNode}). Writes are <em>atomic</em> (temp file +
 * {@code ATOMIC_MOVE}), so a crash mid-save never truncates the config. {@link #watch()} starts a
 * {@code WatchService} daemon with an 80&nbsp;ms settle window; when an admin edits the file, the store
 * reloads, re-validates, and notifies {@link #onReload} listeners — a malformed edit is contained (the old
 * value stays live) rather than crashing the mod. {@link #validate} normalizes/clamps every loaded value.
 *
 * <p>RU: {@code ConfigStore<T>} отображает значение типа мода {@code T} в человекочитаемый JSON через
 * {@link Codec} (поверх устойчивого {@link TreeNode}). Запись атомарна (temp + {@code ATOMIC_MOVE}).
 * {@link #watch()} запускает демон {@code WatchService} с окном стабилизации 80&nbsp;мс; при правке файла
 * стор перечитывает, валидирует и уведомляет слушателей, а битую правку локализует (остаётся старое
 * значение). {@link #validate} нормализует/ограничивает каждое загруженное значение.
 */
public final class ConfigStore<T> implements AutoCloseable {

    /** Bidirectional mapping between the mod's config type and a {@link TreeNode}. */
    public interface Codec<T> {
        TreeNode toTree(T value);

        T fromTree(TreeNode tree);
    }

    private final Path file;
    private final Codec<T> codec;
    private final T defaults;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile UnaryOperator<T> normalizer = UnaryOperator.identity();
    private volatile T current;
    private volatile WatchService watchService;
    private volatile Thread watchThread;
    private volatile boolean running;
    /** True once {@link #watch()} has started; lets {@link #reload()} tell "never watched" from "closed". */
    private volatile boolean watchStarted;

    private ConfigStore(Path file, Codec<T> codec, T defaults) {
        this.file = file;
        this.codec = codec;
        this.defaults = defaults;
    }

    /**
     * Open (or create) the config at {@code file}. If the file is missing it is written with {@code defaults};
     * otherwise it is read and decoded.
     */
    public static <T> ConfigStore<T> open(Path file, Codec<T> codec, T defaults) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(defaults, "defaults");
        ConfigStore<T> store = new ConfigStore<>(file, codec, defaults);
        store.current = store.loadOrCreate();
        return store;
    }

    /** The current value (never null). Updated on {@link #reload()} and {@link #set}. */
    public T get() {
        return current;
    }

    /** Replace the current value (normalized) and persist it atomically. */
    public void set(T value) {
        current = normalizer.apply(Objects.requireNonNull(value, "value"));
        save();
    }

    /** Persist the current value atomically to the config file. */
    public void save() {
        writeAtomic(TreeJson.write(codec.toTree(current)));
    }

    /**
     * The outcome of a {@link #reload()} — success, or a structured diagnostic. a direct caller
     * (e.g. an admin command) behaves the same as the watch thread — neither throws on a malformed file.
     */
    public record ReloadResult(boolean ok, java.util.Optional<org.aetherium.core.Diagnostic> diagnostic) {
        static ReloadResult success() {
            return new ReloadResult(true, java.util.Optional.empty());
        }

        static ReloadResult failed(org.aetherium.core.Diagnostic diagnostic) {
            return new ReloadResult(false, java.util.Optional.of(diagnostic));
        }
    }

    /**
     * Re-read the file, normalize, publish, and notify {@link #onReload} listeners. <strong>Never throws</strong>
     * — a malformed file leaves the last-good value live and returns a failed {@link ReloadResult}.
     */
    public ReloadResult reload() {
        final T loaded;
        try {
            loaded = normalizer.apply(codec.fromTree(TreeJson.parse(readString())));
        } catch (org.aetherium.core.AetheriumException e) {
            return ReloadResult.failed(e.diagnostic());
        } catch (RuntimeException e) {
            return ReloadResult.failed(org.aetherium.core.Diagnostic.error("AE-CONFIG-RELOAD",
                    "Failed to reload " + file + ": " + e.getMessage()));
        }
        current = loaded;
        // a store that has been closed must never call out. The current value may still update
        // (a direct reload() caller reads get()), but listener dispatch is the barrier close() promises: once
        // watch() has run and close() has flipped running false, no onReload listener fires again. A store
        // that never called watch() (running stays false) dispatches on a direct reload() as before — see
        // watchStarted. This is the second guard behind watchLoop's post-settle re-check.
        if (watchStarted && !running) {
            return ReloadResult.success();
        }
        for (Consumer<T> l : listeners) {
            try {
                l.accept(loaded);
            } catch (Throwable ignored) {
                // A listener failure must never break reload or the watch thread.
            }
        }
        return ReloadResult.success();
    }

    /**
     * Install a normalizer/validator applied to every loaded value (clamp ranges, fill defaults). Applied
     * immediately to the current value too.
     */
    public ConfigStore<T> validate(UnaryOperator<T> normalizer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.current = normalizer.apply(current);
        return this;
    }

    /** Register a listener fired on every hot-reload. */
    public ConfigStore<T> onReload(Consumer<T> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    /** Start watching the file for external edits and hot-reloading it (idempotent). */
    public ConfigStore<T> watch() {
        if (running) {
            return this;
        }
        try {
            WatchService ws = file.getFileSystem().newWatchService();
            Path dir = file.toAbsolutePath().getParent();
            dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            this.watchService = ws;
            this.watchStarted = true;
            this.running = true;
            this.watchThread = new Thread(this::watchLoop, "aetherium-config-" + file.getFileName());
            this.watchThread.setDaemon(true);
            this.watchThread.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start config watch for " + file, e);
        }
        return this;
    }

    private void watchLoop() {
        final String target = file.getFileName().toString();
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
                return;
            }
            boolean touched = key.pollEvents().stream()
                    .anyMatch(ev -> ev.context() != null && target.equals(ev.context().toString()));
            key.reset();
            if (touched) {
                sleepQuietly(80); // settle window: coalesce editor's write burst
                // close() interrupts us and sets running=false, but sleepQuietly swallows the
                // interrupt and returns normally. Without this re-check a close() that lands inside the settle
                // window still delivers one final reload() — invoking a closed store's listeners over live
                // state a *different* store installed. close() is a hard barrier; honour it here.
                if (!running) {
                    return;
                }
                // reload() never throws; a malformed hand-edit returns a failed result and keeps last-good.
                reload();
            }
        }
    }

    /**
     * Stop watching and release the {@code WatchService}. <strong>A hard barrier (): after
     * {@code close()} returns, no {@link #onReload} listener will ever be invoked again</strong> — not even by
     * a reload already in flight inside the settle window. Consumers rely on this to hand ownership of the live
     * state to a freshly-opened store without a late callback from the old one overwriting it. Idempotent.
     */
    @Override
    public void close() {
        running = false;
        WatchService ws = watchService;
        if (ws != null) {
            try {
                ws.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
        Thread t = watchThread;
        if (t != null) {
            t.interrupt();
        }
    }

    // --- internals ------------------------------------------------------------------------------

    private T loadOrCreate() {
        if (Files.isRegularFile(file)) {
            return codec.fromTree(TreeJson.parse(readString()));
        }
        writeAtomic(TreeJson.write(codec.toTree(defaults)));
        return defaults;
    }

    private String readString() {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read config " + file, e);
        }
    }

    private void writeAtomic(String content) {
        Path tmp = null;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // Per-write UNIQUE temp in the same directory (same filesystem ⇒ ATOMIC_MOVE applies). A fixed
            // "<name>.tmp" is shared by every concurrent writer — save() is public and unsynchronized, so two
            // threads (or two stores on one file) would interleave content into that one temp and the second
            // move would throw NoSuchFile once the first consumes it. A unique name makes each save land
            // independently and atomically; the finally block guarantees no temp is leaked on failure.
            tmp = file.resolveSibling(file.getFileName() + "." + Long.toHexString(
                    ThreadLocalRandom.current().nextLong()) + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null; // published — nothing to clean up
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write config " + file, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp); // never leave a half-written temp behind
                } catch (IOException ignored) {
                    // best-effort cleanup; a stale unique temp is harmless and never shadows the real file
                }
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
