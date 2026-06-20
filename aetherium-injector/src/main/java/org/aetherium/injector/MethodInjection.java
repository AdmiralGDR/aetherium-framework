/*
 * Aetherium Framework — fluent per-method injection builder.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import org.objectweb.asm.tree.InsnList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The fluent surface for editing one target method — mirrors {@link BytecodeCursor} but records each
 * step instead of executing it immediately.
 *
 * <p>EN: Every navigation/edit returns {@code this}, so a whole injection reads as one chained
 * sentence; each call appends a {@link Consumer}{@code <BytecodeCursor>} to the rule, replayed later
 * against the live method. Hook insertions take an {@link AetheriumHook} (e.g. {@code MyMod::asyncTick}),
 * register it with the parent injector to obtain a dense hook ID, and record the {@code O(1)}
 * {@code invokedynamic} lowering. Call {@link #commit()} to finalize the rule and return to the
 * {@link AetheriumInjector} for further chaining.
 *
 * <p>RU: Текучая поверхность для правки одного целевого метода — повторяет {@link BytecodeCursor}, но
 * записывает шаги, а не выполняет сразу. Каждый ход возвращает {@code this}; каждый вызов добавляет
 * {@link Consumer}{@code <BytecodeCursor>} в правило для последующего воспроизведения. Вставки хуков
 * принимают {@link AetheriumHook}, регистрируют его в родительском инжекторе ради плотного ID и
 * записывают понижение {@code invokedynamic} ({@code O(1)}). Вызовите {@link #commit()} для
 * финализации правила.
 */
public final class MethodInjection {

    private final AetheriumInjector injector;
    private final String classInternalName;
    private final String methodName;
    private final String methodDesc;
    private final List<Consumer<BytecodeCursor>> ops = new ArrayList<>();

    MethodInjection(AetheriumInjector injector, String classInternalName, String methodName, String methodDesc) {
        this.injector = injector;
        this.classInternalName = classInternalName;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
    }

    // --- navigation ---------------------------------------------------------

    public MethodInjection toStart() {
        return record(BytecodeCursor::toStart);
    }

    public MethodInjection toEnd() {
        return record(BytecodeCursor::toEnd);
    }

    public MethodInjection next() {
        return record(BytecodeCursor::next);
    }

    public MethodInjection previous() {
        return record(BytecodeCursor::previous);
    }

    public MethodInjection jumpTo(int index) {
        return record(c -> c.jumpTo(index));
    }

    public MethodInjection findOpcode(int opcode) {
        return record(c -> c.findOpcode(opcode));
    }

    public MethodInjection findReturn() {
        return record(BytecodeCursor::findReturn);
    }

    // --- raw edits ----------------------------------------------------------

    /** Insert a copy of {@code instructions} before the cursor. The list is captured by reference. */
    public MethodInjection insertBefore(InsnList instructions) {
        Objects.requireNonNull(instructions, "instructions");
        return record(c -> c.insertBefore(instructions));
    }

    public MethodInjection insertAfter(InsnList instructions) {
        Objects.requireNonNull(instructions, "instructions");
        return record(c -> c.insertAfter(instructions));
    }

    public MethodInjection replace(InsnList instructions) {
        Objects.requireNonNull(instructions, "instructions");
        return record(c -> c.replace(instructions));
    }

    public MethodInjection delete() {
        return record(BytecodeCursor::delete);
    }

    // --- hook lowering (O(1) invokedynamic) ---------------------------------

    /** Inject a call to {@code hook} immediately before the cursor. */
    public MethodInjection insertHookBefore(AetheriumHook hook) {
        int id = injector.registerHook(hook);
        return record(c -> c.insertHookBefore(id));
    }

    /** Inject a call to {@code hook} immediately after the cursor. */
    public MethodInjection insertHookAfter(AetheriumHook hook) {
        int id = injector.registerHook(hook);
        return record(c -> c.insertHookAfter(id));
    }

    /** Replace the instruction under the cursor with a call to {@code hook}. */
    public MethodInjection replaceWithHook(AetheriumHook hook) {
        int id = injector.registerHook(hook);
        return record(c -> c.replaceWithHook(id));
    }

    // --- context hooks (this/args + cancellation) ---------------------------

    /**
     * Inject a context-aware hook before the cursor. The hook receives {@code this} and can cancel the
     * vanilla method via {@link HookContext#cancel()}/{@link HookContext#cancel(Object)}. Arguments are
     * <em>not</em> captured (no primitive boxing) — use {@link #insertContextHookBefore(ContextualHook, boolean)}
     * to opt into argument capture.
     */
    public MethodInjection insertContextHookBefore(ContextualHook hook) {
        return insertContextHookBefore(hook, false);
    }

    /**
     * Inject a context-aware hook before the cursor, choosing whether to capture (box) the method's
     * arguments into the {@link HookContext}.
     */
    public MethodInjection insertContextHookBefore(ContextualHook hook, boolean captureArguments) {
        int id = injector.registerContextHook(hook);
        return record(c -> c.insertContextHookBefore(id, captureArguments));
    }

    /** Inject a context-aware hook after the cursor (see {@link #insertContextHookBefore(ContextualHook, boolean)}). */
    public MethodInjection insertContextHookAfter(ContextualHook hook, boolean captureArguments) {
        int id = injector.registerContextHook(hook);
        return record(c -> c.insertContextHookAfter(id, captureArguments));
    }

    // --- DAG-ordered merged hook groups (the Semantic Merger) ---------------

    /**
     * Begin a DAG-ordered, semantically merged hook group at {@code anchor}. Unlike the free-form
     * cursor ops above, every hook in the group shares one {@link HookContext} and a single
     * cancellation epilogue, so multiple {@code ctx.cancel()} calls compose instead of conflict.
     * Order is declared with {@code runBefore}/{@code runAfter}, never integer priorities. See
     * {@link MergedHookBuilder}.
     */
    public MergedHookBuilder at(InjectionAnchor anchor) {
        return new MergedHookBuilder(injector, classInternalName, methodName, methodDesc, anchor);
    }

    // --- finalize -----------------------------------------------------------

    /** Finalize this method's rule and return to the injector for more chaining. */
    public AetheriumInjector commit() {
        injector.addRule(new InjectionRule(classInternalName, methodName, methodDesc, ops));
        return injector;
    }

    private MethodInjection record(Consumer<BytecodeCursor> op) {
        ops.add(op);
        return this;
    }
}
