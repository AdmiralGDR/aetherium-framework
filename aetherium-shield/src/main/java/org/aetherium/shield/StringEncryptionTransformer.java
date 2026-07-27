/*
 * Aetherium Framework — shield pass: string-literal encryption.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.TransformResult;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces every string literal with encrypted ciphertext that is only decoded at runtime.
 *
 * <p>EN: Plaintext string constants are the single biggest leak in a mod jar — {@code grep} finds them
 * instantly and an AI reads them as free semantic labels ("Insufficient essence", "faction.admin",
 * URLs, keys). This pass XOR-encrypts each literal with a per-literal key, stores only the ciphertext in the
 * constant pool, and rewrites the {@code LDC} into {@code ldc <cipher>; ldc <key>; invokestatic decode} — so
 * the readable text never exists in the file, only in memory for an instant during use. This is the layer
 * most targeted at <em>automated</em> analysis: an LLM decompiling the class sees opaque byte-salad and a
 * decode call, not the author's words. The decoder is a tiny synthetic method added to the class.
 * RU: Строковые литералы — крупнейшая утечка в jar мода: {@code grep} находит их мгновенно, а ИИ читает как
 * бесплатные семантические подсказки. Этот проход XOR-шифрует каждый литерал ключом, кладёт в пул только
 * шифртекст и переписывает {@code LDC} в {@code ldc <шифр>; ldc <ключ>; invokestatic decode} — читаемый
 * текст никогда не существует в файле, лишь на миг в памяти при использовании. Слой нацелен именно на
 * автоматический (ИИ) анализ.
 */
public final class StringEncryptionTransformer implements ClassTransformer {

    /** Name of the synthetic decode method added to each protected class (in-bytecode mode). */
    static final String DECODE_METHOD = "$aeth$x";
    static final String DECODE_DESC = "(Ljava/lang/String;I)Ljava/lang/String;";
    /** Shared runtime decoder used in native mode (the routine leaves the protected class entirely). */
    static final String RUNTIME_OWNER = "org/aetherium/shield/ShieldRuntime";
    static final String RUNTIME_DECODE = "decode";

    private final int order;
    private final boolean nativeDecrypt;

    public StringEncryptionTransformer(int order) {
        this(order, false);
    }

    /**
     * @param nativeDecrypt when true, lowered call sites target {@link ShieldRuntime#decode} (native XOR via
     *                      the Zig guard, pure-Java fallback) and NO in-class {@code $aeth$x} decoder is
     *                      emitted — so the decode routine is not in the protected bytecode at all.
     */
    public StringEncryptionTransformer(int order, boolean nativeDecrypt) {
        this.order = order;
        this.nativeDecrypt = nativeDecrypt;
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
        String owner = context.internalName();
        int seed = owner.hashCode() * 0x9E3779B1;
        int counter = 0;
        boolean any = false;

        for (MethodNode method : node.methods) {
            if (method.instructions == null || DECODE_METHOD.equals(method.name)) {
                continue;
            }
            List<LdcInsnNode> targets = new ArrayList<>();
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String) {
                    targets.add(ldc);
                }
            }
            for (LdcInsnNode ldc : targets) {
                int key = seed ^ (counter++ * 0x85EBCA77);
                String plain = (String) ldc.cst;
                ldc.cst = encode(plain, key); // constant pool now holds ciphertext only
                InsnList decode = new InsnList();
                decode.add(new LdcInsnNode(key));
                if (nativeDecrypt) {
                    // Route to the shared native/pure-Java decoder — the XOR routine is NOT in this class.
                    decode.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME_OWNER, RUNTIME_DECODE, DECODE_DESC, false));
                } else {
                    decode.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, DECODE_METHOD, DECODE_DESC, false));
                }
                method.instructions.insert(ldc, decode);
                any = true;
            }
        }

        // Also encrypt static-final String CONSTANT fields (ConstantValue). javac inlines these at call sites
        // (where the method pass above already encrypts them), but the declaring class still carries the
        // plaintext in its constant pool — the one readable-string leak `harden-check` reported as an advisory.
        // Move it to a <clinit> decode: putstatic to a final field is legal only from <clinit>, exactly where
        // we emit it, and it is valid for classes and interfaces alike. Deterministic key → still reproducible.
        boolean anyField = false;
        InsnList clinitPrologue = new InsnList();
        for (FieldNode field : node.fields) {
            if (!(field.value instanceof String plainConst)) {
                continue; // only String ConstantValue fields; ints/longs/etc. are left to ConstantObfuscator
            }
            int key = seed ^ (counter++ * 0x85EBCA77);
            field.value = null; // drop the ConstantValue attribute; the class no longer holds the plaintext
            clinitPrologue.add(new LdcInsnNode(encode(plainConst, key)));
            clinitPrologue.add(new LdcInsnNode(key));
            clinitPrologue.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    nativeDecrypt ? RUNTIME_OWNER : owner,
                    nativeDecrypt ? RUNTIME_DECODE : DECODE_METHOD, DECODE_DESC, false));
            clinitPrologue.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, field.name, "Ljava/lang/String;"));
            anyField = true;
        }
        if (anyField) {
            findOrCreateClinit(node).instructions.insert(clinitPrologue); // decode before any other <clinit> code
        }

        if (!any && !anyField) {
            return new TransformResult.Skipped("no string literals");
        }
        // The in-bytecode decoder is needed if EITHER a method LDC or a constant field routes to it.
        if (!nativeDecrypt && (any || anyField)) {
            node.methods.add(buildDecoder());
        }
        return new TransformResult.Applied(node);
    }

    /** Find the class's {@code <clinit>}, or create an empty one (just {@code RETURN}) and add it. */
    private static MethodNode findOrCreateClinit(org.objectweb.asm.tree.ClassNode node) {
        for (MethodNode m : node.methods) {
            if ("<clinit>".equals(m.name) && "()V".equals(m.desc)) {
                return m;
            }
        }
        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(clinit);
        return clinit;
    }

    /** XOR each char with a key stream; symmetric, so the same routine decodes at runtime. */
    static String encode(String plain, int key) {
        char[] a = plain.toCharArray();
        for (int j = 0; j < a.length; j++) {
            a[j] = (char) (a[j] ^ ((key + j * 7) & 0xFFFF));
        }
        return new String(a);
    }

    /**
     * Build {@code private static String $aeth$x(String c, int k)} that reverses {@link #encode}:
     * {@code char[] a=c.toCharArray(); for(j) a[j]^=((k+j*7)&0xFFFF); return new String(a);}
     */
    private static MethodNode buildDecoder() {
        MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                DECODE_METHOD, DECODE_DESC, null, null);
        InsnList in = m.instructions;
        LabelNode loop = new LabelNode();
        LabelNode end = new LabelNode();
        // char[] a = c.toCharArray();  (locals: 0=c, 1=k, 2=a, 3=j)
        in.add(new VarInsnNode(Opcodes.ALOAD, 0));
        in.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false));
        in.add(new VarInsnNode(Opcodes.ASTORE, 2));
        in.add(new InsnNode(Opcodes.ICONST_0));
        in.add(new VarInsnNode(Opcodes.ISTORE, 3));
        in.add(loop);
        in.add(new VarInsnNode(Opcodes.ILOAD, 3));
        in.add(new VarInsnNode(Opcodes.ALOAD, 2));
        in.add(new InsnNode(Opcodes.ARRAYLENGTH));
        in.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        // a[j] = (char)(a[j] ^ ((k + j*7) & 0xFFFF));
        in.add(new VarInsnNode(Opcodes.ALOAD, 2));      // arrayref
        in.add(new VarInsnNode(Opcodes.ILOAD, 3));      // index
        in.add(new VarInsnNode(Opcodes.ALOAD, 2));
        in.add(new VarInsnNode(Opcodes.ILOAD, 3));
        in.add(new InsnNode(Opcodes.CALOAD));           // a[j]
        in.add(new VarInsnNode(Opcodes.ILOAD, 1));      // k
        in.add(new VarInsnNode(Opcodes.ILOAD, 3));
        in.add(new IntInsnNode(Opcodes.BIPUSH, 7));
        in.add(new InsnNode(Opcodes.IMUL));             // j*7
        in.add(new InsnNode(Opcodes.IADD));             // k + j*7
        in.add(new LdcInsnNode(0xFFFF));
        in.add(new InsnNode(Opcodes.IAND));             // (k+j*7)&0xFFFF
        in.add(new InsnNode(Opcodes.IXOR));             // a[j] ^ mask
        in.add(new InsnNode(Opcodes.I2C));
        in.add(new InsnNode(Opcodes.CASTORE));          // a[j] = ...
        in.add(new org.objectweb.asm.tree.IincInsnNode(3, 1));
        in.add(new JumpInsnNode(Opcodes.GOTO, loop));
        in.add(end);
        in.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW, "java/lang/String"));
        in.add(new InsnNode(Opcodes.DUP));
        in.add(new VarInsnNode(Opcodes.ALOAD, 2));
        in.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false));
        in.add(new InsnNode(Opcodes.ARETURN));
        m.maxStack = 6;
        m.maxLocals = 4;
        return m;
    }

    @Override
    public String id() {
        return "shield/string-encrypt";
    }
}
