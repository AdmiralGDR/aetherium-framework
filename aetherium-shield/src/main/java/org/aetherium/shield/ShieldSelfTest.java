/*
 * Aetherium Framework — shield end-to-end self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proves the shield end-to-end with no game and no framework present.
 *
 * <p>EN: It generates a mock class carrying a secret string and a small computation, protects it with every
 * layer, then asserts the sovereign guarantees: (1) the secret string is <em>gone</em> from the raw bytes
 * (an AI/grep sees only ciphertext); (2) debug metadata is stripped; (3) the class was renamed to an opaque
 * name yet <strong>still loads and runs</strong>, decoding the string correctly at runtime; (4) the integrity
 * manifest detects a one-byte tamper; (5) the author watermark is extractable; and (6) protecting an
 * unprotectable input (garbage bytes) <strong>reverts cleanly</strong> with a diagnostic and never crashes —
 * correctness dominates protection.
 * RU: Генерирует мок-класс с секретной строкой и вычислением, защищает всеми слоями и проверяет суверенные
 * гарантии: секретная строка исчезла из байтов (ИИ/grep видит только шифртекст); отладка удалена; класс
 * переименован, но всё ещё загружается и работает, декодируя строку в рантайме; манифест целостности ловит
 * правку одного байта; водяной знак автора извлекаем; защита негодного ввода откатывается без краха.
 */
public final class ShieldSelfTest {

    private static final String SECRET = "TOP_SECRET_ESSENCE_KEY_9F3A";
    /** A {@code static final String} CONSTANT (): its plaintext must leave the pool too, not just LDCs. */
    private static final String CONSTANT_SECRET = "AnomalyCoreConstantLabel_7B2";
    private static final String AUTHOR = "a downstream mod";

    private ShieldSelfTest() {
    }

    /** Structured outcome. */
    public record Result(boolean stringHidden,
                         boolean debugStripped,
                         boolean renamedButRuns,
                         boolean secretDecodedAtRuntime,
                         int computeResult,
                         boolean tamperDetected,
                         boolean watermarkTraceable,
                         boolean brokenInputReverts,
                         boolean decoderOutOfBytecode,
                         boolean constantsObfuscated,
                         String opaqueName,
                         List<String> notes) {
        public boolean passed() {
            return stringHidden && debugStripped && renamedButRuns && secretDecodedAtRuntime
                    && computeResult == 41 && tamperDetected && watermarkTraceable && brokenInputReverts
                    && decoderOutOfBytecode && constantsObfuscated;
        }
    }

    public static Result run() throws ReflectiveOperationException {
        List<String> notes = new java.util.ArrayList<>();

        // 1) A mock mod class: String secret() -> SECRET, int compute(int) -> x*2+1.
        String binary = "com.example.mymod.SecretLogic";
        byte[] original = generateSample(binary.replace('.', '/'));
        boolean originalHasSecret = contains(original, SECRET);
        notes.add("original class: " + original.length + " bytes, contains secret plaintext=" + originalHasSecret);

        // 2) Protect with every layer + an author watermark.
        Map<String, byte[]> input = new LinkedHashMap<>();
        input.put(binary, original);
        Shield.Result protectedResult = Shield.protect(input, ShieldOptions.standard(AUTHOR), new KeepList());

        // The (single) protected class under its new opaque binary name.
        Map.Entry<String, byte[]> only = protectedResult.protectedClasses().entrySet().iterator().next();
        String opaqueName = only.getKey();
        byte[] protectedBytes = only.getValue();
        notes.add("protected class renamed '" + binary + "' -> '" + opaqueName + "' ("
                + protectedBytes.length + " bytes)");

        // (1) The secret string must be GONE from the raw bytes — both the method LDC and the constant field.
        boolean stringHidden = !contains(protectedBytes, SECRET) && !contains(protectedBytes, CONSTANT_SECRET);
        notes.add("method-secret present=" + contains(protectedBytes, SECRET) + ", constant-field secret present="
                + contains(protectedBytes, CONSTANT_SECRET) + " (want both false)");

        // (2) Debug metadata stripped.
        boolean debugStripped = sourceFileOf(protectedBytes) == null;
        notes.add("SourceFile attribute after strip=" + sourceFileOf(protectedBytes) + " (want null)");

        // (3) Renamed class still loads and runs; string decodes correctly at runtime.
        ByteClassLoader loader = new ByteClassLoader(ShieldSelfTest.class.getClassLoader());
        Class<?> loaded = loader.define(opaqueName, protectedBytes);
        String decoded = (String) loaded.getMethod("secret").invoke(null);
        int computed = (int) loaded.getMethod("compute", int.class).invoke(null, 20);
        // The constant field's value must decode correctly too — the <clinit> decode ran at class init.
        String labelValue = (String) loaded.getField("LABEL").get(null);
        boolean renamedButRuns = !opaqueName.equals(binary);
        boolean secretDecoded = SECRET.equals(decoded) && CONSTANT_SECRET.equals(labelValue);
        notes.add("runtime: secret()='" + decoded + "', LABEL='" + labelValue + "' (both decoded OK="
                + secretDecoded + "), compute(20)=" + computed);

        // (3b) Native string-decrypt (): the decode routine must have LEFT the bytecode — the class
        //      calls the shared ShieldRuntime.decode and carries no in-class $aeth$x XOR loop.
        boolean decoderOutOfBytecode = contains(protectedBytes, "org/aetherium/shield/ShieldRuntime")
                && !contains(protectedBytes, "$aeth$x");
        notes.add("native string-decrypt: routes to ShieldRuntime=" + contains(protectedBytes, "ShieldRuntime")
                + ", in-class $aeth$x present=" + contains(protectedBytes, "$aeth$x") + " (want false)");

        // (3c) Numeric constant obfuscation: compute()'s magic number 21 must no longer be a BIPUSH literal —
        //      it is rewritten as (21 ^ K) ^ $aeth$k, reading the opaque key field. The compute() == 41
        //      assertion below proves the rewrite preserved the value.
        boolean constantsObfuscated = computeConstantHidden(protectedBytes);
        notes.add("constant obfuscation: compute() BIPUSH 21 gone + reads opaque key=" + constantsObfuscated);

        // (4) Integrity manifest detects a one-byte tamper.
        boolean intactVerifies = protectedResult.integrity().verify(opaqueName, protectedBytes);
        byte[] tampered = protectedBytes.clone();
        tampered[tampered.length / 2] ^= 0x01;
        boolean tamperRejected = !protectedResult.integrity().verify(opaqueName, tampered);
        boolean tamperDetected = intactVerifies && tamperRejected;
        notes.add("integrity: intact verifies=" + intactVerifies + ", tampered rejected=" + tamperRejected);

        // (5) Author watermark is extractable.
        WatermarkAttribute wm = WatermarkAttribute.extract(protectedBytes);
        boolean watermarkTraceable = wm != null && AUTHOR.equals(wm.author());
        notes.add("watermark author='" + (wm == null ? "<none>" : wm.author()) + "'");

        // (6) Graceful degradation: protecting unprotectable input reverts cleanly (no crash, no rename).
        Map<String, byte[]> garbageInput = new LinkedHashMap<>();
        byte[] garbage = new byte[]{(byte) 0xCA, (byte) 0xFE, 0x00, 0x01, 0x02, 0x03};
        garbageInput.put("Garbage", garbage);
        Shield.Result garbageResult = Shield.protect(garbageInput, ShieldOptions.minimal(), new KeepList());
        boolean reverted = java.util.Arrays.equals(garbage, garbageResult.protectedClasses().get("Garbage"))
                && garbageResult.revertedClasses() >= 1;
        notes.add("broken input: reverted-to-original=" + reverted
                + ", diagnostics=" + garbageResult.revertedClasses() + " (no crash)");

        return new Result(stringHidden, debugStripped, renamedButRuns, secretDecoded, computed,
                tamperDetected, watermarkTraceable, reverted, decoderOutOfBytecode, constantsObfuscated,
                opaqueName, notes);
    }

    /**
     * True iff compute()'s literal 21 is gone AND the opaque {@code (v^K)^K} rewrite is present. We look for
     * the introduced {@code IXOR} (compute had none originally) rather than the key field's name, because the
     * rename pass gives that synthetic field an opaque name — so a literal-name check would false-negative.
     */
    private static boolean computeConstantHidden(byte[] classBytes) {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(classBytes).accept(node, 0);
        for (org.objectweb.asm.tree.MethodNode m : node.methods) {
            if (!"compute".equals(m.name) || !"(I)I".equals(m.desc)) {
                continue;
            }
            boolean rawLiteral = false;
            boolean hasXor = false;
            for (org.objectweb.asm.tree.AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.BIPUSH
                        && ((org.objectweb.asm.tree.IntInsnNode) insn).operand == 21) {
                    rawLiteral = true;
                }
                if (insn.getOpcode() == Opcodes.IXOR) {
                    hasXor = true;
                }
            }
            return !rawLiteral && hasXor;
        }
        return false;
    }

    // --- mock class generation ------------------------------------------------------------------

    private static byte[] generateSample(String internalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null,
                "java/lang/Object", null);
        cw.visitSource("SecretLogic.java", null); // debug metadata the strip pass must remove

        // A public static final String CONSTANT (ConstantValue) — the encryption pass must move it to a
        // <clinit> decode so the plaintext is gone from the pool, yet it still reads back correctly at runtime.
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "LABEL",
                "Ljava/lang/String;", null, CONSTANT_SECRET).visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        MethodVisitor secret = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "secret",
                "()Ljava/lang/String;", null, null);
        secret.visitCode();
        secret.visitLdcInsn(SECRET);
        secret.visitInsn(Opcodes.ARETURN);
        secret.visitMaxs(1, 0);
        secret.visitEnd();

        // int compute(int x) { return x + 21; }  — 21 is a BIPUSH "magic number" the constant pass must hide.
        MethodVisitor compute = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "compute",
                "(I)I", null, null);
        compute.visitCode();
        compute.visitVarInsn(Opcodes.ILOAD, 0);
        compute.visitIntInsn(Opcodes.BIPUSH, 21);
        compute.visitInsn(Opcodes.IADD);
        compute.visitInsn(Opcodes.IRETURN);
        compute.visitMaxs(2, 1);
        compute.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static boolean contains(byte[] haystack, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= haystack.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (haystack[i + j] != n[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static String sourceFileOf(byte[] classBytes) {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode(Opcodes.ASM9);
        new org.objectweb.asm.ClassReader(classBytes).accept(node, 0);
        return node.sourceFile;
    }

    private static final class ByteClassLoader extends ClassLoader {
        ByteClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }
}
