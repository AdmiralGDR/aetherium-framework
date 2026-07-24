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
 * @param obfuscateControlFlow insert opaque predicates to defeat clean decompilation / structural analysis
 * @param renameClasses        rename non-kept classes to opaque names (references + service files rewritten)
 * @param renamePrivateMembers rename private methods/fields to opaque names within their class
 * @param watermark            embed the author signature as a traceable class attribute
 * @param author               the author signature stamped by {@link #watermark} (may be blank)
 */
public record ShieldOptions(boolean stripDebug,
                            boolean encryptStrings,
                            boolean obfuscateControlFlow,
                            boolean renameClasses,
                            boolean renamePrivateMembers,
                            boolean watermark,
                            String author) {

    public ShieldOptions {
        author = author == null ? "" : author;
    }

    /** Everything on, no watermark author set. */
    public static ShieldOptions standard() {
        return new ShieldOptions(true, true, true, true, true, true, "");
    }

    /** {@link #standard()} plus an author watermark. */
    public static ShieldOptions standard(String author) {
        return new ShieldOptions(true, true, true, true, true, true, author);
    }

    /** Only the zero-risk passes (debug-strip + string encryption) — never touches names or control flow. */
    public static ShieldOptions minimal() {
        return new ShieldOptions(true, true, false, false, false, false, "");
    }

    public ShieldOptions withAuthor(String author) {
        return new ShieldOptions(stripDebug, encryptStrings, obfuscateControlFlow, renameClasses,
                renamePrivateMembers, watermark, author);
    }
}
