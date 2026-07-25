/*
 * Aetherium Framework — shield runtime decode entry (out-of-bytecode string decryption).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

/**
 * The shared runtime decode call site a shielded class references when native string-decrypt is on.
 *
 * <p>EN: With the in-bytecode decoder ({@code $aeth$x}), the XOR routine ships inside every protected class,
 * so an AI/decompiler can read the decode logic even if it can't read the strings. Routing the decode through
 * this shared method instead means the protected class contains only {@code ldc <cipher>; ldc <key>;
 * invokestatic ShieldRuntime.decode} — the decode routine is <strong>not in the class at all</strong>. At
 * runtime {@link NativeGuard} performs the XOR natively (in the Zig guard) when present, or the identical
 * pure-Java routine when it is absent (the degradation path is preserved). Lives in {@code aetherium-shield},
 * which the framework runtime bundles, so a shielded framework mod always has it on the classpath.
 * RU: Общая точка декодирования, на которую ссылается защищённый класс при включённой нативной дешифровке
 * строк. Вместо встроенного декодера ({@code $aeth$x}) в классе остаётся лишь {@code ldc <шифр>; ldc <ключ>;
 * invokestatic ShieldRuntime.decode} — сама процедура декодирования отсутствует в классе. В рантайме
 * {@link NativeGuard} выполняет XOR нативно (в Zig-гарде) или тем же чистым Java при отсутствии .so.
 */
public final class ShieldRuntime {

    private ShieldRuntime() {
    }

    /**
     * Decode a shielded string literal. Referenced by lowered call sites the shield injects; do not call
     * directly from mod code.
     */
    public static String decode(String cipher, int key) {
        return NativeGuard.get().xorDecodeString(cipher, key);
    }
}
