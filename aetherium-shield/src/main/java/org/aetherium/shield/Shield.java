/*
 * Aetherium Framework — the Sovereign Shield orchestrator.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.aetherium.bytecode.BytecodeEngine;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.CollectingDiagnosticSink;
import org.aetherium.core.Diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sovereign protection orchestrator — hardens a mod author's own classes against reverse-engineering and,
 * deliberately, against automated (AI/LLM) decompilation and analysis.
 *
 * <p>EN: The shield layers independent defences, each aimed at a different capability an analyst — human or
 * machine — relies on:
 * <ul>
 *   <li><b>Debug strip</b> removes line numbers and variable names (denies structure + the author's names);</li>
 *   <li><b>String encryption</b> turns literals into runtime-decoded ciphertext (denies the "what": an AI can
 *       no longer read the author's words, error messages, keys, or URLs out of the file);</li>
 *   <li><b>Control-flow obfuscation</b> inserts opaque predicates (denies the "how": clean, reducible flow an
 *       LLM needs to reconstruct intent);</li>
 *   <li><b>Renaming</b> erases class/package structure and private-member names (denies the primary semantic
 *       map);</li>
 *   <li><b>Integrity manifest</b> makes post-protection tampering detectable;</li>
 *   <li><b>Watermark</b> makes a leaked jar traceable to its author.</li>
 * </ul>
 * Every per-class pass runs inside the {@code aetherium-bytecode} verification sandbox, so a pass that would
 * produce invalid bytecode reverts that one class to its original bytes and the build never breaks —
 * correctness dominates protection.
 *
 * <p>RU: Щит наслаивает независимые защиты, каждая против своей способности аналитика (человека или машины):
 * удаление отладки (структура + имена автора), шифрование строк (что — ИИ больше не читает слова, ключи, URL),
 * обфускация потока (как — чистый поток, нужный LLM для восстановления замысла), переименование (главная
 * семантическая карта), манифест целостности (обнаружение правок), водяной знак (трассируемость утечки).
 * Каждый проход по классу работает внутри песочницы — плохой проход откатывает класс к оригиналу.
 */
public final class Shield {

    private Shield() {
    }

    /** Outcome of protecting a class set. */
    public record Result(Map<String, byte[]> protectedClasses,
                         IntegrityManifest integrity,
                         Map<String, String> classRenames,
                         List<Diagnostic> diagnostics) {

        /** Number of classes whose obfuscation reverted (shipped un-obfuscated but valid). */
        public int revertedClasses() {
            return diagnostics.size();
        }
    }

    /**
     * Protect {@code classes} (keyed by binary name, e.g. {@code com.example.Foo}) with {@code options}.
     *
     * @param verifyLoader class loader used for frame computation/verification (the mod's compile classpath,
     *                     so classes referencing Minecraft still recompute frames); may be {@code null}
     */
    public static Result protect(Map<String, byte[]> classes, ShieldOptions options, KeepList keep,
                                 ClassLoader verifyLoader) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        // 1) Per-class obfuscation chain, run inside the engine's revert-on-failure sandbox.
        BytecodeEngine engine = buildEngine(options, verifyLoader);
        Map<String, byte[]> obfuscated = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            CollectingDiagnosticSink sink = new CollectingDiagnosticSink();
            byte[] out = engine.transformClass(e.getValue(), sink);
            if (!sink.isEmpty()) {
                // The sandbox reverted this class to its original bytes; it still ships, just un-obfuscated.
                diagnostics.addAll(sink.diagnostics());
            }
            obfuscated.put(e.getKey(), out);
        }

        // 2) Batch rename (consistent across all classes + private members), honoring the keep-list.
        Map<String, byte[]> renamed;
        Map<String, String> classRenames;
        if (options.renameClasses() || options.renamePrivateMembers()) {
            Renamer.Result r = Renamer.rename(obfuscated, keep,
                    options.renameClasses() && options.renamePrivateMembers());
            renamed = r.classes();
            classRenames = r.classRenames();
        } else {
            renamed = obfuscated;
            classRenames = identity(obfuscated.keySet());
        }

        // 3) Integrity manifest over the FINAL bytes.
        Map<String, String> digests = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : renamed.entrySet()) {
            digests.put(e.getKey(), IntegrityManifest.sha256(e.getValue()));
        }

        return new Result(renamed, new IntegrityManifest(digests), classRenames, diagnostics);
    }

    /** Convenience overload without a verify loader (java.*-only classes, tests). */
    public static Result protect(Map<String, byte[]> classes, ShieldOptions options, KeepList keep) {
        return protect(classes, options, keep, null);
    }

    private static BytecodeEngine buildEngine(ShieldOptions options, ClassLoader verifyLoader) {
        BytecodeEngine.Builder builder = BytecodeEngine.builder();
        if (options.stripDebug()) {
            builder.transformer(new DebugStripTransformer(10));
        }
        if (options.encryptStrings()) {
            builder.transformer(new StringEncryptionTransformer(20, options.nativeStringDecrypt()));
        }
        if (options.obfuscateControlFlow()) {
            builder.transformer(new ControlFlowObfuscator(30));
        }
        if (options.junkCode()) {
            builder.transformer(new JunkCodeTransformer(35));
        }
        if (options.watermark()) {
            builder.transformer(new WatermarkTransformer(40, options.author()));
        }
        if (verifyLoader != null) {
            builder.classLoader(verifyLoader);
        }
        return builder.build();
    }

    private static Map<String, String> identity(Iterable<String> names) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String n : names) {
            map.put(n, n);
        }
        return map;
    }
}
