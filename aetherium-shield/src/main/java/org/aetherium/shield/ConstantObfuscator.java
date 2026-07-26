/*
 * Aetherium Framework — shield pass: numeric constant obfuscation (opaque MBA).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.TransformResult;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Hides the "magic number" constants that AI and decompilers use as anchors — without changing behaviour.
 *
 * <p>EN: An automated reverse-engineer (a decompiler, or an LLM reconstructing intent) leans heavily on
 * literal constants: table sizes, opcodes, bit masks, protocol tags. This pass rewrites every non-trivial
 * integer push ({@code BIPUSH}/{@code SIPUSH}/{@code LDC int}) as {@code (v ^ K) ^ K}, where the second
 * {@code K} is read from an <strong>opaque field</strong> {@code $aeth$k} seeded to {@code K} in
 * {@code <clinit>} through the same number-theory identity the control-flow pass trusts ({@code (t²+t)&1}
 * is always 0, so {@code K + 0 == K}, but a tool cannot fold it without proving {@code n²+n} is even). The
 * literal on screen becomes {@code v ^ K}, not {@code v}, and it cannot be constant-folded back because the
 * key is only known at runtime. Purely local and stack-neutral, so it composes with every other pass and
 * runs inside the verification sandbox (revert-on-fail) — correctness first. Deterministic (the key derives
 * from the class name), so protected jars stay byte-reproducible (MANIFEST axiom V). Zero dependency: pure
 * ASM, no runtime helper.
 * RU: Автоматический реверс (декомпилятор или ИИ) опирается на литеральные константы: размеры таблиц, коды
 * операций, битовые маски, теги протокола. Этот проход переписывает каждый нетривиальный int-push как
 * {@code (v ^ K) ^ K}, где второй {@code K} читается из <strong>непрозрачного поля</strong> {@code $aeth$k},
 * засеянного значением {@code K} в {@code <clinit>} через то же тождество, что и обфускатор потока
 * управления ({@code (t²+t)&1} всегда 0). Литерал на экране — {@code v ^ K}, а не {@code v}, и его нельзя
 * свернуть, т.к. ключ известен лишь в рантайме. Локально и стек-нейтрально; в песочнице (revert-on-fail);
 * детерминировано (ключ из имени класса) — воспроизводимость сохраняется. Ноль зависимостей: чистый ASM.
 */
public final class ConstantObfuscator implements ClassTransformer {

    /** The opaque key field: seeded to a per-class {@code K} in {@code <clinit>} via an opaque identity. */
    static final String KEY_FIELD = "$aeth$k";

    private final int order;

    public ConstantObfuscator(int order) {
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
        ClassNode node = context.node();
        if ((node.access & Opcodes.ACC_INTERFACE) != 0 || (node.access & Opcodes.ACC_ANNOTATION) != 0) {
            return new TransformResult.Skipped("no method bodies");
        }
        String owner = context.internalName();
        int key = keyFor(owner);
        int obfuscated = 0;

        for (MethodNode method : node.methods) {
            if (!eligible(method)) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                Integer value = magicIntValue(insn);
                if (value == null) {
                    continue;
                }
                InsnList repl = new InsnList();
                repl.add(new LdcInsnNode(Integer.valueOf(value ^ key)));   // v ^ K  (the visible literal)
                repl.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, KEY_FIELD, "I")); // opaque K
                repl.add(new InsnNode(Opcodes.IXOR));                      // (v ^ K) ^ K = v
                method.instructions.insertBefore(insn, repl);
                method.instructions.remove(insn);
                obfuscated++;
            }
        }

        if (obfuscated == 0) {
            return new TransformResult.Skipped("no magic constants");
        }
        ensureKeyField(node, key);
        return new TransformResult.Applied(node);
    }

    /** The value pushed by a "magic number" instruction, or {@code null} for anything we leave alone. */
    private static Integer magicIntValue(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        // BIPUSH/SIPUSH carry genuine magic numbers; ICONST_m1..5 are trivial + ubiquitous (left as-is to
        // avoid bloating hot loops with obfuscation of 0/1).
        if (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH) {
            return ((IntInsnNode) insn).operand;
        }
        if (op == Opcodes.LDC && insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer i) {
            return i;
        }
        return null;
    }

    private static boolean eligible(MethodNode method) {
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return false;
        }
        if (method.instructions == null || method.instructions.size() == 0) {
            return false;
        }
        // Skip <clinit> (it seeds the key — obfuscating the seed would be circular) and the string decoder.
        return !"<clinit>".equals(method.name)
                && !StringEncryptionTransformer.DECODE_METHOD.equals(method.name);
    }

    /** Deterministic non-zero per-class key so protected jars stay byte-reproducible. */
    private static int keyFor(String internalName) {
        int k = internalName.hashCode();
        return k == 0 ? 0x5bd1e995 : k;
    }

    private static void ensureKeyField(ClassNode node, int key) {
        for (FieldNode f : node.fields) {
            if (KEY_FIELD.equals(f.name)) {
                return;
            }
        }
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                KEY_FIELD, "I", null, null));
        seedKeyInClinit(node, key);
    }

    /**
     * Seed {@code $aeth$k = K + ((int)(t*t + t) & 1)} in {@code <clinit>}, i.e. {@code K + 0 == K} at runtime
     * (since {@code n²+n} is always even) but not statically foldable — so the key stays opaque to tools.
     */
    private static void seedKeyInClinit(ClassNode node, int key) {
        MethodNode clinit = null;
        for (MethodNode m : node.methods) {
            if ("<clinit>".equals(m.name) && "()V".equals(m.desc)) {
                clinit = m;
                break;
            }
        }
        if (clinit == null) {
            clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
            node.methods.add(clinit);
        }
        InsnList seed = new InsnList();
        seed.add(new LdcInsnNode(Integer.valueOf(key)));                                     // K
        seed.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false));
        seed.add(new InsnNode(Opcodes.DUP2));
        seed.add(new InsnNode(Opcodes.DUP2));
        seed.add(new InsnNode(Opcodes.LMUL));   // t (t*t)
        seed.add(new InsnNode(Opcodes.LADD));   // (t*t + t)
        seed.add(new InsnNode(Opcodes.LCONST_1));
        seed.add(new InsnNode(Opcodes.LAND));   // & 1  -> always 0
        seed.add(new InsnNode(Opcodes.L2I));    // 0 (int)
        seed.add(new InsnNode(Opcodes.IADD));   // K + 0 = K
        seed.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, KEY_FIELD, "I"));
        clinit.instructions.insert(seed); // prepend, so the key exists before any obfuscated constant runs
        clinit.maxStack = Math.max(clinit.maxStack, 7);
    }

    @Override
    public String id() {
        return "shield/constants";
    }
}
