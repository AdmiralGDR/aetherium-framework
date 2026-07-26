/*
 * Aetherium Framework — artifact verifier tests (cross-artifact clash).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ArtifactVerifierTest {

    @Test
    void detectsTheCrossArtifactPackageClashThatCrashesTheGame(@TempDir Path dir) throws IOException {
        // The OLD, crashing shape: a fat transformer with LOOSE org/aetherium/core, plus a loader whose
        // nested aetherium-core.jar exports the SAME package — NeoForge would throw ResolutionException.
        Path transformer = jar(dir.resolve("aetherium-transformer.jar"),
                Map.of("org/aetherium/core/Foo.class", stub(), "org/aetherium/transformer/T.class", stub()));
        byte[] nestedCore = jarBytes(Map.of("org/aetherium/core/Foo.class", stub()));
        Path loader = jar(dir.resolve("aetherium-loader.jar"), Map.of(
                "META-INF/jarjar/aetherium-core-1.0.0-SNAPSHOT.jar", nestedCore,
                "org/aetherium/loader/Main.class", stub()));

        List<ArtifactVerifier.Violation> v = ArtifactVerifier.moduleClashes(loader, transformer);
        assertTrue(v.stream().anyMatch(x -> "AE-MODULE-CLASH".equals(x.code())
                        && x.detail().contains("org/aetherium/core")),
                () -> "must flag the org/aetherium/core clash across the two artifacts: " + v);
    }

    @Test
    void relocatedBootLayerHasNoClash(@TempDir Path dir) throws IOException {
        // The FIXED shape: the transformer's embedded copy is shaded to org/aetherium/boot/…, so it no
        // longer exports any package the loader's nested jars export.
        Path transformer = jar(dir.resolve("aetherium-transformer.jar"), Map.of(
                "org/aetherium/boot/core/Foo.class", stub(),
                "org/aetherium/transformer/T.class", stub()));
        byte[] nestedCore = jarBytes(Map.of("org/aetherium/core/Foo.class", stub()));
        Path loader = jar(dir.resolve("aetherium-loader.jar"), Map.of(
                "META-INF/jarjar/aetherium-core-1.0.0-SNAPSHOT.jar", nestedCore,
                "org/aetherium/loader/Main.class", stub()));

        List<ArtifactVerifier.Violation> v = ArtifactVerifier.moduleClashes(loader, transformer);
        assertTrue(v.isEmpty(), () -> "the relocated set must not clash: " + v);
    }

    // The clash check reads entry names only, so a one-byte stub stands in for a class file.
    private static byte[] stub() {
        return new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
    }

    private static Path jar(Path path, Map<String, byte[]> entries) throws IOException {
        Files.write(path, jarBytes(entries));
        return path;
    }

    private static byte[] jarBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : new LinkedHashMap<>(entries).entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static void assertTrue(boolean cond, java.util.function.Supplier<String> msg) {
        org.junit.jupiter.api.Assertions.assertTrue(cond, msg);
    }
}
