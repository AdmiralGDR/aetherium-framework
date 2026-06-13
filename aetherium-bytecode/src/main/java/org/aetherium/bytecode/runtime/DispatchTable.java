package org.aetherium.bytecode.runtime;

import java.lang.invoke.MethodHandle;

/**
 * The runtime dispatch table: a flat array of {@link MethodHandle}s indexed by dense symbol ID.
 *
 * <p>EN: Populated <em>once</em> during the load phase ({@link #install}) by the active loader
 * shim, then read-only. {@link #handle(int)} is a bare array index — the {@code O(1)} runtime path
 * behind every lowered {@code invokedynamic} call site (see {@code ARCHITECTURE.md} ). The array
 * is held in a {@code volatile} field so the single install publishes safely to all threads.
 *
 * <p>RU: Заполняется <em>один раз</em> на фазе загрузки ({@link #install}) активной прослойкой
 * загрузчика, затем только для чтения. {@link #handle(int)} — голый индекс массива, путь
 * {@code O(1)} за каждой пониженной точкой вызова {@code invokedynamic} (см.
 * {@code ARCHITECTURE.md} ). Массив хранится в {@code volatile}-поле, поэтому единственная
 * установка безопасно публикуется во все потоки.
 */
public final class DispatchTable {

    private static volatile MethodHandle[] handles = new MethodHandle[0];

    private DispatchTable() {
    }

    /**
     * Install the dispatch table. EN: intended to be called exactly once at load time; the array
     * is defensively copied so callers cannot mutate it afterwards. RU: предполагается однократный
     * вызов на фазе загрузки; массив защитно копируется, чтобы вызывающие не могли его изменить.
     */
    public static void install(MethodHandle[] resolved) {
        handles = resolved.clone();
    }

    /** {@code O(1)} lookup. Returns {@code null} for an out-of-range or unbound ID. */
    public static MethodHandle handle(int symbolId) {
        MethodHandle[] snapshot = handles;
        return (symbolId >= 0 && symbolId < snapshot.length) ? snapshot[symbolId] : null;
    }

    /** Number of installed handles. */
    public static int size() {
        return handles.length;
    }
}
