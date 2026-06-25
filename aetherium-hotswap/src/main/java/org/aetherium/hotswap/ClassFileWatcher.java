/*
 * Aetherium Framework — build-directory watcher that drives live hot-swaps.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Watches a modder's build output directory and hot-swaps each changed {@code .class} into the game.
 *
 * <p>EN: Registers a {@link WatchService} recursively over the compiled-classes root (e.g.
 * {@code build/classes/java/main}). When {@code javac}/Gradle rewrites a {@code .class}, the watcher
 * reads the new bytes and feeds them to {@link HotSwapEngine#redefine(byte[])}; the engine derives the
 * class name from the bytes, so directory layout never has to be mapped to package names by hand. Runs
 * on a single daemon thread, coalesces rapid successive writes with a short settle delay, and reports
 * every outcome through a caller-supplied sink. Closing the watcher stops the thread cleanly.
 * RU: Рекурсивно регистрирует {@link WatchService} над корнем скомпилированных классов (напр.
 * {@code build/classes/java/main}). Когда {@code javac}/Gradle перезаписывает {@code .class}, наблюдатель
 * читает новые байты и передаёт их в {@link HotSwapEngine#redefine(byte[])}; движок выводит имя класса
 * из байт, поэтому раскладку каталогов не нужно вручную сопоставлять с пакетами. Работает на одном
 * демон-потоке, объединяет быстрые последовательные записи коротким окном, и сообщает каждый исход
 * через переданный приёмник. Закрытие наблюдателя чисто останавливает поток.
 */
public final class ClassFileWatcher implements AutoCloseable {

    private final Path root;
    private final HotSwapEngine engine;
    private final Consumer<HotSwapResult> sink;
    private final long settleMillis;

    private volatile WatchService watchService;
    private volatile Thread thread;

    public ClassFileWatcher(Path root, HotSwapEngine engine, Consumer<HotSwapResult> sink) {
        this(root, engine, sink, 80L);
    }

    public ClassFileWatcher(Path root, HotSwapEngine engine, Consumer<HotSwapResult> sink, long settleMillis) {
        this.root = root.toAbsolutePath();
        this.engine = engine;
        this.sink = sink;
        this.settleMillis = settleMillis;
    }

    /** Begin watching on a daemon thread. Idempotent: a second call is a no-op. */
    public synchronized void start() {
        if (thread != null) {
            return;
        }
        try {
            WatchService ws = FileSystems.getDefault().newWatchService();
            Map<WatchKey, Path> keys = new HashMap<>();
            registerRecursively(ws, root, keys);
            this.watchService = ws;
            Thread t = new Thread(() -> runLoop(ws, keys), "aetherium-hotswap-watch");
            t.setDaemon(true);
            this.thread = t;
            t.start();
        } catch (IOException e) {
            throw new UncheckedIOException("could not start hot-swap watcher on " + root, e);
        }
    }

    private void runLoop(WatchService ws, Map<WatchKey, Path> keys) {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = ws.take();
            } catch (InterruptedException | ClosedWatchServiceException stop) {
                Thread.currentThread().interrupt();
                return;
            }
            Path dir = keys.get(key);
            if (dir != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    handle(ws, keys, dir, event);
                }
            }
            if (!key.reset()) {
                keys.remove(key);
            }
        }
    }

    private void handle(WatchService ws, Map<WatchKey, Path> keys, Path dir, WatchEvent<?> event) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            return;
        }
        Path changed = dir.resolve((Path) event.context());
        try {
            // A freshly created subdirectory must be watched too (recursive registration).
            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                registerRecursively(ws, changed, keys);
                return;
            }
        } catch (IOException ignored) {
            // Best-effort: an unwatchable subtree just won't hot-swap.
        }
        if (!changed.getFileName().toString().endsWith(".class")) {
            return;
        }
        swap(changed);
    }

    private void swap(Path classFile) {
        try {
            // Let the writer finish before reading (Gradle/javac may write in two steps).
            Thread.sleep(settleMillis);
            byte[] bytes = Files.readAllBytes(classFile);
            if (bytes.length == 0) {
                return; // mid-write; the next event will carry the complete file
            }
            sink.accept(engine.redefine(bytes));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException io) {
            sink.accept(HotSwapResult.rejected(classFile.toString(), "read failed: " + io.getMessage()));
        }
    }

    private static void registerRecursively(WatchService ws, Path dir, Map<WatchKey, Path> keys)
            throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isDirectory).forEach(d -> {
                try {
                    WatchKey key = d.register(ws,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                    keys.put(key, d);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    @Override
    public synchronized void close() {
        Thread t = thread;
        if (t != null) {
            t.interrupt();
            thread = null;
        }
        WatchService ws = watchService;
        if (ws != null) {
            try {
                ws.close();
            } catch (IOException ignored) {
                // closing best-effort
            }
            watchService = null;
        }
    }
}
