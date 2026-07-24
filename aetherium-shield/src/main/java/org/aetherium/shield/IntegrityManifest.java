/*
 * Aetherium Framework — shield integrity manifest + verifier.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A checksum ledger over the protected classes — the "negative trust" tamper check.
 *
 * <p>EN: The shield records a SHA-256 of every protected class. At load time (or on demand) a mod/loader can
 * re-hash a class's bytes and compare: a mismatch means the class was patched after protection (a cracked
 * jar, an injected backdoor, a tampered check) and can be refused. Redistributing altered bytecode is thus
 * detectable rather than silent. The manifest is a plain {@code className → hex-digest} map so it serializes
 * trivially and carries no secrets.
 * RU: Щит записывает SHA-256 каждого защищённого класса. При загрузке (или по требованию) мод/загрузчик
 * пере-хеширует байты класса и сравнивает: несовпадение означает, что класс правили после защиты (взломанный
 * jar, внедрённый бэкдор, подделанная проверка) — и его можно отклонить.
 */
public final class IntegrityManifest {

    private final Map<String, String> digests;

    IntegrityManifest(Map<String, String> digests) {
        this.digests = Map.copyOf(digests);
    }

    /** The recorded digest for a class (binary name), or {@code null} if not protected. */
    public String digestOf(String binaryName) {
        return digests.get(binaryName);
    }

    public int size() {
        return digests.size();
    }

    public Map<String, String> asMap() {
        return digests;
    }

    /** True if {@code classBytes} still matches the recorded digest for {@code binaryName}. */
    public boolean verify(String binaryName, byte[] classBytes) {
        String expected = digests.get(binaryName);
        return expected != null && expected.equals(sha256(classBytes));
    }

    /** Serialize as {@code binaryName=hexdigest} lines (stable, sorted). */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        digests.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append('\n'));
        return sb.toString();
    }

    /** Parse a manifest previously produced by {@link #serialize()}. */
    public static IntegrityManifest parse(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            String t = line.trim();
            int eq = t.indexOf('=');
            if (eq > 0) {
                map.put(t.substring(0, eq), t.substring(eq + 1));
            }
        }
        return new IntegrityManifest(map);
    }

    /** Lowercase hex SHA-256 of {@code bytes}. */
    public static String sha256(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
