/*
 * Aetherium Framework — shield configuration.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

/**
 * What the {@link Shield} does to a mod's classes — each protection is opt-in and independently toggleable.
 *
 * <p>EN: The defaults ({@link #standard}) turn on every protection that is safe for an arbitrary mod:
 * debug-stripping, string encryption, control-flow obfuscation, private-member + class renaming, an
 * integrity manifest, and an author watermark. Each layer targets a different reverse-engineering (and
 * <em>automated AI</em>) capability — see {@link Shield} for the rationale.
 * RU: По умолчанию ({@link #standard}) включены все защиты, безопасные для любого мода: удаление отладочной
 * информации, шифрование строк, обфускация потока управления, переименование приватных членов и классов,
 * манифест целостности и водяной знак автора.
 *
 * @param stripDebug           remove SourceFile, line numbers, and local-variable tables/names
 * @param encryptStrings       replace string literals with runtime-decoded ciphertext
 * @param nativeStringDecrypt  route string decode through the native guard (ShieldRuntime) so the decode
 *                             routine is NOT in the protected bytecode; requires {@code encryptStrings}
 * @param obfuscateControlFlow insert opaque predicates to defeat clean decompilation / structural analysis
 * @param obfuscateConstants   rewrite magic-number int constants as {@code (v^K)^K} against an opaque key,
 *                             so an AI/decompiler loses the literal anchors it relies on
 * @param junkCode             insert synthetic never-called decoy methods (AI/decompiler misdirection)
 * @param renameClasses        rename non-kept classes to opaque names (references + service files rewritten)
 * @param renamePrivateMembers rename private methods/fields to opaque names within their class
 * @param watermark            embed the author signature as a traceable class attribute
 * @param author               the author signature stamped by {@link #watermark} (may be blank)
 */
public record ShieldOptions(boolean stripDebug,
                            boolean encryptStrings,
                            boolean nativeStringDecrypt,
                            boolean obfuscateControlFlow,
                            boolean obfuscateConstants,
                            boolean junkCode,
                            boolean renameClasses,
                            boolean renamePrivateMembers,
                            boolean watermark,
                            String author) {

    public ShieldOptions {
        author = author == null ? "" : author;
    }

    /** Everything on (incl. native string-decrypt, constant + control-flow obfuscation, junk-code, renaming). */
    public static ShieldOptions standard() {
        return new ShieldOptions(true, true, true, true, true, true, true, true, true, "");
    }

    /** {@link #standard()} plus an author watermark. */
    public static ShieldOptions standard(String author) {
        return new ShieldOptions(true, true, true, true, true, true, true, true, true, author);
    }

    /** Only the zero-risk passes (debug-strip + string encryption, in-bytecode decoder) — no names/flow. */
    public static ShieldOptions minimal() {
        return new ShieldOptions(true, true, false, false, false, false, false, false, false, "");
    }

    public ShieldOptions withAuthor(String author) {
        return new ShieldOptions(stripDebug, encryptStrings, nativeStringDecrypt, obfuscateControlFlow,
                obfuscateConstants, junkCode, renameClasses, renamePrivateMembers, watermark, author);
    }
}
