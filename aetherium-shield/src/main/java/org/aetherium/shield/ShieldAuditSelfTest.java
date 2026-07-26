/*
 * Aetherium Framework — proves the protection audit both passes and fails correctly.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proves {@link ShieldAudit} is a real gate — no game, no framework.
 *
 * <p>EN: It builds a class carrying a readable secret + a {@code SourceFile}, audits it <em>before</em>
 * protection (expecting the audit to REPORT it leaky — plaintext present, debug present), then protects it and
 * audits again (expecting analysis-resistant — strings encrypted, debug stripped, watermark present). An audit
 * that could not fail would be worthless, so proving both directions is the point.
 * RU: Строит класс с читаемым секретом и {@code SourceFile}, проверяет ДО защиты (аудит обязан сообщить об
 * утечке: текст и отладка на месте), затем защищает и проверяет снова (ожидается устойчивость к анализу:
 * строки зашифрованы, отладка удалена, водяной знак на месте). Аудит, который не может провалиться, бесполезен.
 */
public final class ShieldAuditSelfTest {

    private static final String SECRET = "Insufficient essence for the Iron Vanguard";
    private static final String AUTHOR = "a downstream mod";

    private ShieldAuditSelfTest() {
    }

    /** Structured outcome. */
    public record Result(boolean rawIsLeaky, boolean rawNamesThePlaintext, boolean protectedIsResistant,
                         boolean protectedIsWatermarked, List<String> notes) {
        public boolean passed() {
            return rawIsLeaky && rawNamesThePlaintext && protectedIsResistant && protectedIsWatermarked;
        }
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();

        String binary = "com.example.mymod.EssenceGate";
        byte[] raw = generateSample(binary.replace('.', '/'));

        // (1) BEFORE protection: the audit must report the class leaky and name the plaintext.
        ShieldAudit.Finding rawFinding = ShieldAudit.auditClass(binary, raw);
        boolean rawIsLeaky = !rawFinding.analysisResistant();
        boolean rawNamesThePlaintext = rawFinding.plaintextSamples().stream().anyMatch(SECRET::startsWith)
                || rawFinding.plaintextSamples().stream().anyMatch(s -> SECRET.contains(s.replace("…", "")));
        notes.add("raw: analysisResistant=" + rawFinding.analysisResistant()
                + " stringsEncrypted=" + rawFinding.stringsEncrypted()
                + " debugStripped=" + rawFinding.debugStripped()
                + " samples=" + rawFinding.plaintextSamples());

        // (2) AFTER protection: strings encrypted + debug stripped + watermark present.
        Map<String, byte[]> input = new LinkedHashMap<>();
        input.put(binary, raw);
        Shield.Result protectedResult = Shield.protect(input, ShieldOptions.standard(AUTHOR), new KeepList());
        Map<String, byte[]> protectedClasses = protectedResult.protectedClasses();
        ShieldAudit.Report report = ShieldAudit.audit(protectedClasses);
        ShieldAudit.Finding pf = report.findings().get(0);
        boolean protectedIsResistant = report.allProtected();
        boolean protectedIsWatermarked = report.watermarked() == protectedClasses.size() && pf.watermarkPresent();
        notes.add("protected: allProtected=" + report.allProtected()
                + " stringsEncrypted=" + pf.stringsEncrypted()
                + " debugStripped=" + pf.debugStripped()
                + " watermarked=" + pf.watermarkPresent()
                + " leftover-samples=" + pf.plaintextSamples());

        return new Result(rawIsLeaky, rawNamesThePlaintext, protectedIsResistant, protectedIsWatermarked, notes);
    }

    /** A mock mod class: a readable secret string, a small method, and a SourceFile the audit must catch. */
    private static byte[] generateSample(String internalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null,
                "java/lang/Object", null);
        cw.visitSource("EssenceGate.java", null); // debug metadata the audit must flag before, miss after

        MethodVisitor msg = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "message",
                "()Ljava/lang/String;", null, null);
        msg.visitCode();
        msg.visitLdcInsn(SECRET);
        msg.visitInsn(Opcodes.ARETURN);
        msg.visitMaxs(1, 0);
        msg.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
