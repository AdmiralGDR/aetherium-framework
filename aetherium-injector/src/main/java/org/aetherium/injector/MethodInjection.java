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
