/*
 * Aetherium Framework — shield tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShieldTest {

    @Test
    void endToEndProtection() throws ReflectiveOperationException {
        ShieldSelfTest.Result r = ShieldSelfTest.run();
        assertTrue(r.passed(), () -> "shield self-test failed: " + r.notes());
        assertTrue(r.stringHidden(), "secret string must be absent from protected bytes");
        assertTrue(r.debugStripped(), "debug metadata must be stripped");
        assertTrue(r.renamedButRuns(), "class must be renamed to an opaque name");
        assertTrue(r.secretDecodedAtRuntime(), "encrypted string must decode correctly at runtime");
        assertEquals(41, r.computeResult(), "renamed class must still compute correctly");
        assertTrue(r.tamperDetected(), "integrity manifest must detect a tamper");
        assertTrue(r.watermarkTraceable(), "author watermark must be extractable");
        assertTrue(r.brokenInputReverts(), "unprotectable input must revert cleanly (no crash)");
    }

    @Test
    void protectedOutputIsByteReproducible() {
        // MANIFEST axiom V: protecting the SAME class with the SAME options twice must yield identical bytes,
        // so a shielded mod jar is reproducible. Guards against a wall-clock/random creeping into any pass
        // (the watermark's System.currentTimeMillis() once broke this).
        byte[] sample = reproSample();
        byte[] first = protectOnce(sample);
        byte[] second = protectOnce(sample);
        assertArrayEquals(first, second, "protected bytes must be identical across runs (reproducible builds)");
    }

    private static byte[] protectOnce(byte[] sample) {
        Map<String, byte[]> in = new LinkedHashMap<>();
        in.put("com.example.Repro", sample);
        Shield.Result r = Shield.protect(in, ShieldOptions.standard("ReproAuthor"), new KeepList());
        return r.protectedClasses().values().iterator().next();
    }

    private static byte[] reproSample() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "com/example/Repro", null,
                "java/lang/Object", null);
        MethodVisitor f = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "f", "(I)I", null, null);
        f.visitCode();
        f.visitVarInsn(Opcodes.ILOAD, 0);
        f.visitIntInsn(Opcodes.SIPUSH, 1234); // a magic constant for the constant-obfuscation pass
        f.visitInsn(Opcodes.IADD);
        f.visitInsn(Opcodes.IRETURN);
        f.visitMaxs(2, 1);
        f.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void stringEncryptionRoundTrips() {
        for (String s : new String[]{"", "a", "Insufficient essence!", "минералы", "key=9F3A/secret"}) {
            int key = 0x1234 ^ s.hashCode();
            String cipher = StringEncryptionTransformer.encode(s, key);
            String back = StringEncryptionTransformer.encode(cipher, key); // symmetric
            assertEquals(s, back, "XOR encode/decode must be symmetric for: " + s);
        }
    }
}
