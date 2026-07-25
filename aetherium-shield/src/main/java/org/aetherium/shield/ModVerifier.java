/*
 * Aetherium Framework — runtime integrity verifier (the active half of the Shield).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies loaded classes against a Shield {@code shield-integrity.txt} manifest at runtime — turning the
 * static, ship-time checksum into an active tamper check (feedback notes the manifest exists; this makes
 * it enforceable).
 *
 * <p>EN: The Shield records a SHA-256 per protected class at build time. At runtime {@link #loadManifest}
 * reads every {@code META-INF/aetherium/shield-integrity.txt} on the classpath (one per protected mod) and
 * merges them; {@link #verifyClass} re-hashes a class's bytes from the same class loader and compares. A
 * mismatch means the class was patched after protection — a cracked jar, an injected backdoor, a defeated
 * check. The loader can then <em>refuse</em> the mod (enforcing), and the in-game inspector shows it red.
 * RU: Щит записывает SHA-256 на каждый защищённый класс при сборке. В рантайме {@link #loadManifest} читает
 * все {@code shield-integrity.txt} на classpath и сливает; {@link #verifyClass} пере-хеширует байты класса и
 * сравнивает. Несовпадение = класс правили после защиты. Загрузчик может отказать моду (enforce), инспектор
 * подсветит красным.
 */
public final class ModVerifier {

    /** The verdict for one class. */
    public enum Verdict {
        /** Bytes match the recorded digest — the class is protected and unmodified. */
        INTACT,
        /** The class is in the manifest but its bytes differ — it was tampered with. */
        TAMPERED,
        /** The class is in the manifest but could not be read from the class loader. */
        MISSING,
        /** The class is not in any manifest — it was shipped without the Shield. */
        UNSIGNED
    }

    /** One class's verdict. */
    public record ClassResult(String binaryName, Verdict verdict) {
    }

    private ModVerifier() {
    }

    /** Merge every {@code shield-integrity.txt} on {@code loader}'s classpath into one manifest. */
    public static IntegrityManifest loadManifest(ClassLoader loader) {
        Map<String, String> merged = new LinkedHashMap<>();
        try {
            Enumeration<URL> resources = loader.getResources("META-INF/aetherium/shield-integrity.txt");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream in = url.openStream()) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    merged.putAll(IntegrityManifest.parse(text).asMap());
                }
            }
        } catch (Throwable ignored) {
            // A missing/unreadable manifest just means "unsigned"; never throw during verification.
        }
        return new IntegrityManifest(merged);
    }

    /** Verify a single class (binary name) against {@code manifest}, reading its bytes from {@code loader}. */
    public static Verdict verifyClass(ClassLoader loader, IntegrityManifest manifest, String binaryName) {
        String expected = manifest.digestOf(binaryName);
        if (expected == null) {
            return Verdict.UNSIGNED;
        }
        byte[] bytes = readClassBytes(loader, binaryName);
        if (bytes == null) {
            return Verdict.MISSING;
        }
        return expected.equals(IntegrityManifest.sha256(bytes)) ? Verdict.INTACT : Verdict.TAMPERED;
    }

    /** Verify a set of classes; results preserve input order. */
    public static List<ClassResult> verify(ClassLoader loader, IntegrityManifest manifest,
                                           Iterable<String> binaryNames) {
        List<ClassResult> out = new ArrayList<>();
        for (String name : binaryNames) {
            out.add(new ClassResult(name, verifyClass(loader, manifest, name)));
        }
        return out;
    }

    /** Read a class's bytes from the loader as a resource ({@code a.b.C} → {@code a/b/C.class}). */
    public static byte[] readClassBytes(ClassLoader loader, String binaryName) {
        String path = binaryName.replace('.', '/') + ".class";
        try (InputStream in = loader.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (Throwable t) {
            return null;
        }
    }
}
