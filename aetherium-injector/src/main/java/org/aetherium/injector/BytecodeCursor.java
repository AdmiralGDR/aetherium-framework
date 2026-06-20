/*
 * Aetherium Framework — fluent bytecode cursor.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

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

    /** Context-aware hook sites (descriptor {@code (LHookContext;)V}) target this bootstrap. */
    private static final Handle CONTEXT_HOOK_BOOTSTRAP = new Handle(
            Opcodes.H_INVOKESTATIC,
            "org/aetherium/injector/HookBootstrap",
            "bootstrapContextHook",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;I)"
                    + "Ljava/lang/invoke/CallSite;",
            false);

    private static final String CONTEXT_INTERNAL = "org/aetherium/injector/HookContext";

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

    // --- context hook lowering (this/args + cancellation) -------------------

    /**
     * Inject a context-aware hook immediately before the current node. The hook receives a
     * {@link HookContext} carrying {@code this} (for an instance method) and — if
     * {@code captureArguments} is true — the method's arguments; if the hook calls
     * {@link HookContext#cancel()}/{@link HookContext#cancel(Object)} the emitted code performs an
     * immediate, frame-correct return, skipping the rest of the vanilla body.
     */
    public BytecodeCursor insertContextHookBefore(int hookId, boolean captureArguments) {
        require();
        method.instructions.insertBefore(current, contextHookInsns(new int[] {hookId}, captureArguments));
        return this;
    }

    /** As {@link #insertContextHookBefore(int, boolean)} but inserted after the current node. */
    public BytecodeCursor insertContextHookAfter(int hookId, boolean captureArguments) {
        require();
        method.instructions.insert(current, contextHookInsns(new int[] {hookId}, captureArguments));
        return this;
    }

    /**
     * The ASM Semantic Merger: inject a whole DAG-ordered group of context hooks as <strong>one</strong>
     * shared-{@link HookContext} block with a <strong>single</strong> cancellation epilogue, before the
     * current node.
     *
     * <p>This is what makes multiple {@code ctx.cancel()} calls compose instead of conflict. A naive
     * "one block per hook" lowering would let the first hook's early {@code return} skip every later
     * hook — so two mods that both sometimes cancel would race on which transform ran last. Here all
     * {@code hookIds} run in DAG order against the same context (each observing the previous hook's
     * writes, including a prior {@code cancel}), and cancellation is decided exactly once at the end.
     */
    public BytecodeCursor insertMergedContextHookBefore(int[] hookIds, boolean captureArguments) {
        require();
        method.instructions.insertBefore(current, contextHookInsns(hookIds, captureArguments));
        return this;
    }

    /**
     * Build the full (possibly merged) context-hook sequence:
     * <pre>
     *   NEW HookContext ; DUP ; &lt;self&gt; ; &lt;args[]&gt; ; INVOKESPECIAL &lt;init&gt; ; ASTORE ctx
     *   ALOAD ctx ; INVOKEDYNAMIC invoke(LHookContext;)V          // hook 1   (O(1) dispatch)
     *   ALOAD ctx ; INVOKEDYNAMIC invoke(LHookContext;)V          // hook 2   ... in DAG order
     *   ...                                                       // hook N
     *   ALOAD ctx ; INVOKEVIRTUAL isCancelled()Z ; IFEQ CONT      // ONE cancellation decision
     *     &lt;cancellation return: RETURN / unbox+xRETURN / CHECKCAST+ARETURN&gt;
     *   CONT:                                                     // original instruction continues here
     * </pre>
     * Every path other than the early return is net-zero on the operand stack, so {@code COMPUTE_FRAMES}
     * recomputes a valid frame at {@code CONT} and the JVM verifier accepts the class.
     */
    private InsnList contextHookInsns(int[] hookIds, boolean captureArguments) {
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        Type[] argTypes = Type.getArgumentTypes(method.desc);
        Type returnType = Type.getReturnType(method.desc);

        // A fresh local slot (beyond every existing local) holds the context across the calls; bumping
        // maxLocals reserves it even though COMPUTE_FRAMES/COMPUTE_MAXS will recompute the final value.
        int ctxSlot = method.maxLocals;
        method.maxLocals += 1;

        InsnList out = new InsnList();
        LabelNode cont = new LabelNode();

        // --- build the HookContext once: new HookContext(self, args) ---
        out.add(new TypeInsnNode(Opcodes.NEW, CONTEXT_INTERNAL));
        out.add(new InsnNode(Opcodes.DUP));
        // self (this) or null for a static method
        out.add(isStatic ? new InsnNode(Opcodes.ACONST_NULL) : new VarInsnNode(Opcodes.ALOAD, 0));
        // args array (empty unless capture was requested)
        if (captureArguments && argTypes.length > 0) {
            out.add(pushInt(argTypes.length));
            out.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
            int local = isStatic ? 0 : 1;
            for (int i = 0; i < argTypes.length; i++) {
                Type arg = argTypes[i];
                out.add(new InsnNode(Opcodes.DUP));
                out.add(pushInt(i));
                out.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), local));
                boxIfPrimitive(out, arg);
                out.add(new InsnNode(Opcodes.AASTORE));
                local += arg.getSize();
            }
        } else {
            out.add(pushInt(0));
            out.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        }
        out.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, CONTEXT_INTERNAL, "<init>",
                "(Ljava/lang/Object;[Ljava/lang/Object;)V", false));
        out.add(new VarInsnNode(Opcodes.ASTORE, ctxSlot));

        // --- every hook call in DAG order (O(1) invokedynamic, bound to the context-hook table) ---
        for (int hookId : hookIds) {
            out.add(new VarInsnNode(Opcodes.ALOAD, ctxSlot));
            out.add(new InvokeDynamicInsnNode("aetheriumContextHook",
                    "(L" + CONTEXT_INTERNAL + ";)V", CONTEXT_HOOK_BOOTSTRAP, hookId));
        }

        // --- single cancellation check: if (ctx.isCancelled()) return [value]; ---
        out.add(new VarInsnNode(Opcodes.ALOAD, ctxSlot));
        out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTEXT_INTERNAL, "isCancelled", "()Z", false));
        out.add(new JumpInsnNode(Opcodes.IFEQ, cont)); // not cancelled -> continue the vanilla body
        emitCancellationReturn(out, returnType, ctxSlot);

        out.add(cont);
        return out;
    }

    /** Emit the early return for a cancelled call: {@code RETURN} for void, unboxed {@code xRETURN}
     *  for a primitive, or {@code CHECKCAST}+{@code ARETURN} for a reference/array return type. */
    private static void emitCancellationReturn(InsnList out, Type returnType, int ctxSlot) {
        if (returnType.getSort() == Type.VOID) {
            out.add(new InsnNode(Opcodes.RETURN));
            return;
        }
        // load the boxed return value supplied to ctx.cancel(value)
        out.add(new VarInsnNode(Opcodes.ALOAD, ctxSlot));
        out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTEXT_INTERNAL, "returnValue",
                "()Ljava/lang/Object;", false));
        switch (returnType.getSort()) {
            case Type.BOOLEAN -> unbox(out, "java/lang/Boolean", "booleanValue", "()Z", Opcodes.IRETURN);
            case Type.BYTE -> unbox(out, "java/lang/Byte", "byteValue", "()B", Opcodes.IRETURN);
            case Type.CHAR -> unbox(out, "java/lang/Character", "charValue", "()C", Opcodes.IRETURN);
            case Type.SHORT -> unbox(out, "java/lang/Short", "shortValue", "()S", Opcodes.IRETURN);
            case Type.INT -> unbox(out, "java/lang/Integer", "intValue", "()I", Opcodes.IRETURN);
            case Type.LONG -> unbox(out, "java/lang/Long", "longValue", "()J", Opcodes.LRETURN);
            case Type.FLOAT -> unbox(out, "java/lang/Float", "floatValue", "()F", Opcodes.FRETURN);
            case Type.DOUBLE -> unbox(out, "java/lang/Double", "doubleValue", "()D", Opcodes.DRETURN);
            case Type.ARRAY -> {
                out.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getDescriptor()));
                out.add(new InsnNode(Opcodes.ARETURN));
            }
            default -> { // OBJECT
                out.add(new TypeInsnNode(Opcodes.CHECKCAST, returnType.getInternalName()));
                out.add(new InsnNode(Opcodes.ARETURN));
            }
        }
    }

    private static void unbox(InsnList out, String boxed, String method, String desc, int returnOpcode) {
        out.add(new TypeInsnNode(Opcodes.CHECKCAST, boxed));
        out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, boxed, method, desc, false));
        out.add(new InsnNode(returnOpcode));
    }

    private static void boxIfPrimitive(InsnList out, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN -> out.add(box("java/lang/Boolean", "(Z)Ljava/lang/Boolean;"));
            case Type.BYTE -> out.add(box("java/lang/Byte", "(B)Ljava/lang/Byte;"));
            case Type.CHAR -> out.add(box("java/lang/Character", "(C)Ljava/lang/Character;"));
            case Type.SHORT -> out.add(box("java/lang/Short", "(S)Ljava/lang/Short;"));
            case Type.INT -> out.add(box("java/lang/Integer", "(I)Ljava/lang/Integer;"));
            case Type.LONG -> out.add(box("java/lang/Long", "(J)Ljava/lang/Long;"));
            case Type.FLOAT -> out.add(box("java/lang/Float", "(F)Ljava/lang/Float;"));
            case Type.DOUBLE -> out.add(box("java/lang/Double", "(D)Ljava/lang/Double;"));
            default -> { /* reference/array: already an Object on the stack */ }
        }
    }

    private static MethodInsnNode box(String boxed, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, boxed, "valueOf", desc, false);
    }

    /** Smallest instruction that pushes {@code value} (used for array index/length constants). */
    private static AbstractInsnNode pushInt(int value) {
        if (value >= -1 && value <= 5) {
            return new InsnNode(Opcodes.ICONST_0 + value); // ICONST_M1..ICONST_5 are contiguous from ICONST_0-1
        }
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return new IntInsnNode(Opcodes.BIPUSH, value);
        }
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            return new IntInsnNode(Opcodes.SIPUSH, value);
        }
        return new org.objectweb.asm.tree.LdcInsnNode(value);
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
