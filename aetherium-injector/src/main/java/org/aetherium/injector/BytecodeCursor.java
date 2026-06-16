/*
 * Aetherium Framework — fluent bytecode cursor.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Objects;

/**
 * A strongly-typed, navigable cursor over a method's instruction list — the core of the fluent
 * injection API and Aetherium's answer to Mixin.
 *
 * <p>EN: There is <strong>no string-based matching</strong> here (no {@code @At("HEAD")}); you
 * navigate the real ASM instruction graph with typed moves — {@link #toStart()}, {@link #toEnd()},
 * {@link #findOpcode(int)}, {@link #findReturn()}, {@link #next()}, {@link #jumpTo(int)} — and edit it
 * with {@link #insertBefore(InsnList)}, {@link #insertAfter(InsnList)}, {@link #replace(InsnList)} and
 * {@link #delete()}. To route control flow into a high-performance Aetherium API you call
 * {@link #insertHookBefore(int)} / {@link #insertHookAfter(int)} / {@link #replaceWithHook(int)};
 * these emit an {@code invokedynamic} (descriptor {@code ()V}) bound to {@link HookBootstrap} — the
 * {@code O(1)} dispatch path — never a brittle static call. Navigation that cannot be satisfied throws
 * {@link CursorException}, which the {@link InjectorTransformer} contains (revert to original).
 *
 * <p>RU: Здесь <strong>нет сопоставления по строкам</strong> (никакого {@code @At("HEAD")}): вы
 * перемещаетесь по реальному графу инструкций ASM типобезопасными ходами — {@link #toStart()},
 * {@link #toEnd()}, {@link #findOpcode(int)}, {@link #findReturn()}, {@link #next()},
 * {@link #jumpTo(int)} — и правите его через {@link #insertBefore(InsnList)},
 * {@link #insertAfter(InsnList)}, {@link #replace(InsnList)} и {@link #delete()}. Чтобы направить
 * поток управления в высокопроизводительный API, вызываются {@link #insertHookBefore(int)} и др.;
 * они порождают {@code invokedynamic} ({@code ()V}), привязанный к {@link HookBootstrap} — путь
 * {@code O(1)} — а не хрупкий статический вызов. Неосуществимая навигация бросает
 * {@link CursorException}, который локализует {@link InjectorTransformer}.
 */
public final class BytecodeCursor {

    /** Every injected hook site targets this bootstrap (mirrors the engine's dispatch bootstrap). */
    private static final Handle HOOK_BOOTSTRAP = new Handle(
            Opcodes.H_INVOKESTATIC,
            "org/aetherium/injector/HookBootstrap",
            "bootstrapHook",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;I)"
                    + "Ljava/lang/invoke/CallSite;",
            false);

    private final MethodNode method;
    private AbstractInsnNode current;

    public BytecodeCursor(MethodNode method) {
        this.method = Objects.requireNonNull(method, "method");
        this.current = method.instructions.getFirst();
    }

    // --- navigation (strongly typed; no string descriptors) -----------------

    /** Move to the first instruction. */
    public BytecodeCursor toStart() {
        current = method.instructions.getFirst();
        return this;
    }

    /** Move to the last instruction. */
    public BytecodeCursor toEnd() {
        current = method.instructions.getLast();
        return this;
    }

    /** Advance one node. Throws if already past the end. */
    public BytecodeCursor next() {
        require();
        current = current.getNext();
        return this;
    }

    /** Step back one node. Throws if already before the start. */
    public BytecodeCursor previous() {
        require();
        current = current.getPrevious();
        return this;
    }

    /** Move to the instruction at {@code index} (bounds-checked). */
    public BytecodeCursor jumpTo(int index) {
        if (index < 0 || index >= method.instructions.size()) {
            throw new CursorException("jumpTo(" + index + ") out of range [0," + method.instructions.size() + ")");
        }
        current = method.instructions.get(index);
        return this;
    }

    /** Move forward to the next instruction with {@code opcode} (inclusive of the current node). */
    public BytecodeCursor findOpcode(int opcode) {
        for (AbstractInsnNode insn = current; insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == opcode) {
                current = insn;
                return this;
            }
        }
        throw new CursorException("opcode " + opcode + " not found from current position in "
                + method.name + method.desc);
    }

    /** Move to the first {@code *RETURN}/{@code RETURN} at or after the current node. */
    public BytecodeCursor findReturn() {
        for (AbstractInsnNode insn = current; insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
                current = insn;
                return this;
            }
        }
        throw new CursorException("no return instruction found in " + method.name + method.desc);
    }

    /** Non-throwing variant of {@link #findOpcode(int)}; returns whether a match was found. */
    public boolean tryFindOpcode(int opcode) {
        for (AbstractInsnNode insn = current; insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == opcode) {
                current = insn;
                return true;
            }
        }
        return false;
    }

    // --- raw edits ----------------------------------------------------------

    /** Insert instructions immediately before the current node. */
    public BytecodeCursor insertBefore(InsnList instructions) {
        require();
        method.instructions.insertBefore(current, instructions);
        return this;
    }

    /** Insert instructions immediately after the current node. */
    public BytecodeCursor insertAfter(InsnList instructions) {
        require();
        method.instructions.insert(current, instructions);
        return this;
    }

    /** Replace the current node with the given instructions; the cursor lands on the last inserted. */
    public BytecodeCursor replace(InsnList instructions) {
        require();
        AbstractInsnNode anchor = current;
        AbstractInsnNode landing = instructions.getLast();
        method.instructions.insertBefore(anchor, instructions);
        method.instructions.remove(anchor);
        current = landing != null ? landing : current;
        return this;
    }

    /** Delete the current node; the cursor advances to the following node. */
    public BytecodeCursor delete() {
        require();
        AbstractInsnNode next = current.getNext();
        method.instructions.remove(current);
        current = next;
        return this;
    }

    // --- hook lowering (the O(1) invokedynamic path) ------------------------

    /** Inject a hook call immediately before the current node. */
    public BytecodeCursor insertHookBefore(int hookId) {
        require();
        method.instructions.insertBefore(current, hookInsn(hookId));
        return this;
    }

    /** Inject a hook call immediately after the current node. */
    public BytecodeCursor insertHookAfter(int hookId) {
        require();
        method.instructions.insert(current, hookInsn(hookId));
        return this;
    }

    /** Replace the current node with a hook call; the cursor lands on the injected site. */
    public BytecodeCursor replaceWithHook(int hookId) {
        require();
        InvokeDynamicInsnNode hook = hookInsn(hookId);
        method.instructions.insertBefore(current, hook);
        method.instructions.remove(current);
        current = hook;
        return this;
    }

    private static InvokeDynamicInsnNode hookInsn(int hookId) {
        // Descriptor ()V → zero net stack effect, so COMPUTE_FRAMES recomputes the method cleanly.
        return new InvokeDynamicInsnNode("aetheriumHook", "()V", HOOK_BOOTSTRAP, hookId);
    }

    // --- accessors ----------------------------------------------------------

    /** The instruction currently under the cursor (may be {@code null} past the end). */
    public AbstractInsnNode current() {
        return current;
    }

    /** The method being edited. */
    public MethodNode method() {
        return method;
    }

    /** Index of the current node, or {@code -1} if past the end. */
    public int index() {
        return current == null ? -1 : method.instructions.indexOf(current);
    }

    private void require() {
        if (current == null) {
            throw new CursorException("cursor is past the end of " + method.name + method.desc);
        }
    }
}
