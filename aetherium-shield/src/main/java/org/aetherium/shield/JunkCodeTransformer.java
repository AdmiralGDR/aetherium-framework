/*
 * Aetherium Framework — shield pass: junk/decoy code insertion.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.TransformResult;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds synthetic, never-called decoy methods with plausible-but-dead logic — misdirection aimed at automated
 * (AI/LLM) analysis, which summarizes every method it sees as if it mattered.
 *
 * <p>EN: The decoys are valid bytecode (they verify and could run), but nothing references them, so they add
 * zero runtime cost and pure noise to a decompiler's output. Combined with renaming and string encryption an
 * automated tool cannot tell a decoy from a real method by name, strings, or a call graph it can only
 * partially reconstruct. Bounded (a couple of small methods per class) and COMPUTE_FRAMES-safe.
 * RU: Добавляет синтетические, никогда не вызываемые методы-приманки с правдоподобной, но мёртвой логикой —
 * дезинформация против автоматического (ИИ) анализа. Приманки валидны, но на них никто не ссылается: ноль
 * стоимости в рантайме и чистый шум в выводе декомпилятора.
 */
public final class JunkCodeTransformer implements ClassTransformer {

    static final String JUNK_PREFIX = "$aeth$j";

    private final int order;

    public JunkCodeTransformer(int order) {
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
        if ((node.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION)) != 0) {
            return new TransformResult.Skipped("no method bodies");
        }
        for (MethodNode m : node.methods) {
            if (m.name.startsWith(JUNK_PREFIX)) {
                return new TransformResult.Skipped("already has decoys");
            }
        }
        int seed = node.name.hashCode();
        node.methods.add(decoy(JUNK_PREFIX + "0", seed));
        node.methods.add(decoy(JUNK_PREFIX + "1", seed * 31 + 7));
        return new TransformResult.Applied(node);
    }

    /** {@code private static int <name>(int a, int b) { int x = a*31 + b; x ^= x >>> 7; return x + <k>; }} */
    private static MethodNode decoy(String name, int k) {
        MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                name, "(II)I", null, null);
        InsnList in = m.instructions;
        in.add(new VarInsnNode(Opcodes.ILOAD, 0));      // a
        in.add(new IntInsnNode(Opcodes.BIPUSH, 31));
        in.add(new InsnNode(Opcodes.IMUL));             // a*31
        in.add(new VarInsnNode(Opcodes.ILOAD, 1));      // b
        in.add(new InsnNode(Opcodes.IADD));             // a*31 + b
        in.add(new InsnNode(Opcodes.DUP));              // x, x
        in.add(new IntInsnNode(Opcodes.BIPUSH, 7));
        in.add(new InsnNode(Opcodes.IUSHR));            // x, x>>>7
        in.add(new InsnNode(Opcodes.IXOR));             // x ^ (x>>>7)
        in.add(new LdcInsnNode(k));
        in.add(new InsnNode(Opcodes.IADD));             // + k
        in.add(new InsnNode(Opcodes.IRETURN));
        m.maxStack = 3;
        m.maxLocals = 2;
        return m;
    }

    @Override
    public String id() {
        return "shield/junk-code";
    }
}
