/*
 * Aetherium Framework — shield pass: control-flow obfuscation (opaque predicates).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.TransformResult;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Inserts opaque predicates that break clean decompilation and structural (AI) analysis.
 *
 * <p>EN: A decompiler — and an LLM reconstructing intent — relies on clean, reducible control flow. This pass
 * guards each method with a branch on a class-level flag that is <em>always</em> false at runtime but that
 * static analysis cannot fold away without whole-program reasoning, jumping to a dead {@code throw} block.
 * The observable behaviour is identical; the recovered structure is not. Combined with string encryption it
 * denies an automated tool both the "what" (strings) and the "how" (clean flow). It runs inside the
 * verification sandbox, so if a method ever resisted frame recomputation the whole class simply reverts —
 * correctness first.
 * RU: Декомпилятор — и ИИ, восстанавливающий замысел — опирается на чистый управляющий поток. Этот проход
 * ставит в начало каждого метода ветвление по флагу класса, который в рантайме <em>всегда</em> ложен, но
 * который статический анализ не может свернуть без анализа всей программы, с переходом в мёртвый блок
 * {@code throw}. Поведение идентично; восстановленная структура — нет.
 */
public final class ControlFlowObfuscator implements ClassTransformer {

    /** The opaque flag field: default-initialized to 0, so the guard branch is never taken. */
    static final String FLAG_FIELD = "$aeth$op";

    private final int order;

    public ControlFlowObfuscator(int order) {
        this.order = order;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public boolean handles(ClassContext context) {
        return true;
    }

    @Override
    public TransformResult apply(ClassContext context) {
        var node = context.node();
        // Interfaces/annotations have no method bodies to guard.
        if ((node.access & Opcodes.ACC_INTERFACE) != 0 || (node.access & Opcodes.ACC_ANNOTATION) != 0) {
            return new TransformResult.Skipped("no method bodies");
        }
        String owner = context.internalName();
        boolean any = false;

        for (MethodNode method : node.methods) {
            if (!eligible(method)) {
                continue;
            }
            InsnList guard = new InsnList();
            LabelNode dead = new LabelNode();
            // if ($aeth$op != 0) throw new IllegalStateException();   (never taken — flag is always 0)
            guard.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, FLAG_FIELD, "I"));
            guard.add(new JumpInsnNode(Opcodes.IFNE, dead));
            method.instructions.insert(guard);

            // Dead block at the very end: reachable only via the (impossible) jump.
            InsnList deadBlock = new InsnList();
            deadBlock.add(dead);
            deadBlock.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
            deadBlock.add(new InsnNode(Opcodes.DUP));
            deadBlock.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException",
                    "<init>", "()V", false));
            deadBlock.add(new InsnNode(Opcodes.ATHROW));
            method.instructions.add(deadBlock);
            method.maxStack = Math.max(method.maxStack, 2);
            any = true;
        }

        if (!any) {
            return new TransformResult.Skipped("no eligible methods");
        }
        ensureFlagField(node);
        return new TransformResult.Applied(node);
    }

    private static boolean eligible(MethodNode method) {
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return false;
        }
        if (method.instructions == null || method.instructions.size() == 0) {
            return false;
        }
        // Skip constructors (uninitialized-this rules) and the string decoder helper (keep it clean).
        return !"<init>".equals(method.name) && !"<clinit>".equals(method.name)
                && !StringEncryptionTransformer.DECODE_METHOD.equals(method.name);
    }

    private static void ensureFlagField(org.objectweb.asm.tree.ClassNode node) {
        for (FieldNode f : node.fields) {
            if (FLAG_FIELD.equals(f.name)) {
                return;
            }
        }
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                FLAG_FIELD, "I", null, null));
    }

    @Override
    public String id() {
        return "shield/control-flow";
    }
}
