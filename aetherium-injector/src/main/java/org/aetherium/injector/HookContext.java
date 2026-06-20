/*
 * Aetherium Framework — injection hook context (this / args / cancellation carrier).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

/**
 * The context handed to a {@link ContextualHook}: the intercepted method's {@code this}, its
 * arguments, and the cancellation channel back to the vanilla method.
 *
 * <p>EN: This is what lifts the injector from "run a {@code void} callback" to a genuine Mixin
 * replacement. A hook can read the receiver ({@link #self()}) and the captured arguments
 * ({@link #arg(int)}), and — most importantly — <strong>cancel the original method</strong> with
 * {@link #cancel()} (a {@code void} method) or {@link #cancel(Object)} (returning a value). When the
 * injected site sees {@link #isCancelled()}, the bytecode emitted by {@link BytecodeCursor} executes
 * an immediate, frame-correct {@code RETURN}/{@code xRETURN}, skipping the rest of the vanilla body.
 *
 * <p>Performance: the context object is the only allocation on the hook path, and argument capture is
 * <em>opt-in</em> — the self-and-cancel variant passes an empty argument array, so no primitive is
 * boxed unless a mod explicitly asks to read the arguments. The return value is boxed only on the cold
 * cancellation path (you are returning early and skipping the whole method body, so it is net-positive).
 * The hot, no-context callback ({@link AetheriumHook}) remains entirely allocation-free.
 *
 * <p>RU: Контекст, передаваемый {@link ContextualHook}: {@code this} перехваченного метода, его
 * аргументы и канал отмены к ванильному методу. Именно это превращает инжектор из «вызвать
 * {@code void}-колбэк» в полноценную замену Mixin. Хук может читать получателя ({@link #self()}) и
 * захваченные аргументы ({@link #arg(int)}) и — главное — <strong>отменить исходный метод</strong>
 * через {@link #cancel()} ({@code void}-метод) или {@link #cancel(Object)} (с возвращаемым значением).
 * Увидев {@link #isCancelled()}, байт-код, порождённый {@link BytecodeCursor}, выполняет немедленный
 * корректный по фреймам {@code RETURN}/{@code xRETURN}, пропуская остаток ванильного тела.
 *
 * <p>Производительность: объект контекста — единственная аллокация на пути хука, а захват аргументов
 * <em>опционален</em> — вариант «self + cancel» передаёт пустой массив, поэтому примитивы не
 * упаковываются, пока мод явно не запросит чтение аргументов. Возвращаемое значение упаковывается
 * только на холодном пути отмены. Горячий бесконтекстный колбэк ({@link AetheriumHook}) остаётся без
 * аллокаций.
 */
public final class HookContext {

    private static final Object[] NO_ARGS = new Object[0];

    private final Object self;
    private final Object[] args;
    private boolean cancelled;
    private Object returnValue;

    /**
     * Constructed by the injected bytecode (never by mod code directly).
     *
     * @param self the receiver ({@code this}) for an instance method, or {@code null} for a static one
     * @param args the captured arguments (boxed); {@code null} is treated as an empty array
     */
    public HookContext(Object self, Object[] args) {
        this.self = self;
        this.args = args == null ? NO_ARGS : args;
    }

    /** The intercepted method's receiver, or {@code null} for a static method. */
    public Object self() {
        return self;
    }

    /** Number of captured arguments (0 unless the injection requested argument capture). */
    public int argCount() {
        return args.length;
    }

    /**
     * The argument at {@code index} (boxed for primitives). Out-of-range returns {@code null} rather
     * than throwing, so a hook can never crash the vanilla method by mis-indexing.
     */
    public Object arg(int index) {
        return (index >= 0 && index < args.length) ? args[index] : null;
    }

    /** Cancel a {@code void} method: the vanilla body is skipped and the method returns immediately. */
    public void cancel() {
        this.cancelled = true;
        this.returnValue = null;
    }

    /**
     * Cancel a value-returning method, supplying the value to return. For a primitive return type pass
     * the corresponding boxed value (e.g. {@code ctx.cancel(0)} auto-boxes to {@link Integer}); it must
     * be non-null, since the injected site unboxes it.
     */
    public void cancel(Object returnValue) {
        this.cancelled = true;
        this.returnValue = returnValue;
    }

    /** Whether a hook requested cancellation. Read by the injected bytecode. */
    public boolean isCancelled() {
        return cancelled;
    }

    /** The value supplied to {@link #cancel(Object)} (boxed). Read by the injected bytecode. */
    public Object returnValue() {
        return returnValue;
    }
}
