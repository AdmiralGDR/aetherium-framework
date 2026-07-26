/*
 * Aetherium Framework — sovereign protection audit (verify a shielded artifact denies analysis).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proves, on the <em>shipped</em> bytes, that a jar was actually hardened — the protection counterpart to
 * {@code aetherium-verify}'s {@code verifyJar} for the launch. Where {@link ShieldSelfTest} proves the shield
 * works on a mock class, this audits arbitrary classes a mod author points it at, so "is my jar really
 * protected?" gets a precise, offline answer instead of a hope.
 *
 * <p>EN: For each class it checks the three guarantees an analyst — human or AI — relies on being able to
 * read: (1) <b>strings encrypted</b> — no readable plaintext string constant remains (the encryption pass
 * turns every {@code LDC} into XOR ciphertext whose chars scatter across the whole 16-bit range, so a run of
 * ASCII letters is the unmistakable signature of an <em>un</em>-encrypted literal); (2) <b>debug stripped</b>
 * — no {@code SourceFile}, line numbers, or local-variable names (which hand a decompiler the author's own
 * structure and names); (3) <b>watermark present</b> — the leaked-jar traceability mark. Uses only the
 * framework's own ASM — no external tool, no game.
 * RU: Для каждого класса проверяет три гарантии, на читаемость которых опирается аналитик (человек или ИИ):
 * строки зашифрованы (не осталось читаемого текстового литерала — шифртекст XOR разбросан по всему 16-битному
 * диапазону, поэтому серия ASCII-букв — верный признак НЕзашифрованного литерала); отладка удалена (нет
 * SourceFile, номеров строк, имён локальных переменных); водяной знак на месте. Только свой ASM, без игры.
 */
public final class ShieldAudit {

    private ShieldAudit() {
    }

    /**
     * Per-class audit result. {@link #plaintextSamples} names a few readable <em>code</em> strings that leaked
     * (the ones the shield encrypts); {@link #readableConstants} names readable {@code static final String}
     * constant-field values, which the string-encryption pass does not target — they are compile-time
     * constants javac inlines (and encrypts) at every call site, and are usually public API such as registry
     * ids — so they are reported as an advisory, not counted against analysis-resistance.
     */
    public record Finding(String className, boolean stringsEncrypted, boolean debugStripped,
                          boolean watermarkPresent, List<String> plaintextSamples,
                          List<String> readableConstants) {

        /** A class resists analysis when its code strings are encrypted and its debug metadata is stripped. */
        public boolean analysisResistant() {
            return stringsEncrypted && debugStripped;
        }
    }

    /** Whole-artifact audit. */
    public record Report(List<Finding> findings) {

        /** Every audited class resists analysis. */
        public boolean allProtected() {
            return findings.stream().allMatch(Finding::analysisResistant);
        }

        /** How many classes still leak (readable strings or debug metadata). */
        public int leakyClasses() {
            return (int) findings.stream().filter(f -> !f.analysisResistant()).count();
        }

        /** How many classes carry an author watermark. */
        public int watermarked() {
            return (int) findings.stream().filter(Finding::watermarkPresent).count();
        }

        /** How many classes still expose a readable {@code static final String} constant (advisory). */
        public int classesWithReadableConstants() {
            return (int) findings.stream().filter(f -> !f.readableConstants().isEmpty()).count();
        }
    }

    /** Audit a set of classes keyed by name (binary or internal — used only for reporting). */
    public static Report audit(Map<String, byte[]> classes) {
        List<Finding> out = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            out.add(auditClass(e.getKey(), e.getValue()));
        }
        return new Report(List.copyOf(out));
    }

    /** Audit a single class's bytes. */
    public static Finding auditClass(String name, byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0); // flags 0 → keep debug so we can prove it is gone

        // (1) strings: readable code-string LDCs are the leak the shield closes; readable constant-field
        //     values (ConstantValue) are advisory — javac inlines + encrypts them at call sites, and they are
        //     usually public API (registry ids), so the shield leaves the declaration readable by design.
        List<String> leaks = new ArrayList<>();
        List<String> constantLeaks = new ArrayList<>();
        if (node.fields != null) {
            for (FieldNode f : node.fields) {
                if (f.value instanceof String s && hasReadableRun(s)) {
                    addSample(constantLeaks, s);
                }
            }
        }
        for (MethodNode m : node.methods) {
            if (m.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s && hasReadableRun(s)) {
                    addSample(leaks, s);
                }
            }
        }
        boolean stringsEncrypted = leaks.isEmpty();

        // (2) debug: SourceFile, line numbers, or local-variable tables all hand structure to a decompiler.
        boolean debug = node.sourceFile != null;
        for (MethodNode m : node.methods) {
            if (m.localVariables != null && !m.localVariables.isEmpty()) {
                debug = true;
            }
            if (m.instructions != null) {
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null && !debug; insn = insn.getNext()) {
                    if (insn instanceof LineNumberNode) {
                        debug = true;
                    }
                }
            }
        }

        // (3) watermark: leaked-jar traceability (advisory — reported, not required for analysis-resistance).
        boolean watermark = WatermarkAttribute.extract(bytes) != null;

        return new Finding(name, stringsEncrypted, !debug, watermark, List.copyOf(leaks), List.copyOf(constantLeaks));
    }

    private static void addSample(List<String> leaks, String s) {
        if (leaks.size() >= 6) {
            return;
        }
        String t = s.length() > 48 ? s.substring(0, 48) + "…" : s;
        leaks.add(t);
    }

    /**
     * True when {@code s} contains a run of at least five consecutive ASCII letters — the signature of readable
     * plaintext. The encryption pass XORs every literal against a 16-bit key stream, so a ciphertext char lands
     * in {@code [A-Za-z]} only ~52/65536 of the time; a five-letter run in ciphertext is statistically
     * impossible, so this cleanly separates a leaked literal from encrypted salad without a fuzzy heuristic.
     */
    static boolean hasReadableRun(String s) {
        int run = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                if (++run >= 5) {
                    return true;
                }
            } else {
                run = 0;
            }
        }
        return false;
    }
}
