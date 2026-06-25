/*
 * Aetherium Framework — universal-packaging metadata tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gradle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UniversalPackagingTest {

    @Test
    void universalManifestAdvertisesJarInJar() {
        Map<String, String> attrs = AetheriumGradlePlugin.universalManifest("my_mod");
        assertEquals("true", attrs.get("Aetherium-Universal"));
        assertEquals("neoforge,fabric", attrs.get("Aetherium-Loaders"));
        assertEquals("jar-in-jar", attrs.get("Aetherium-Packaging"));
        assertEquals("my_mod", attrs.get("Aetherium-Mod-Id"));
    }

    @Test
    void jijMetadataDescribesEmbeddedJars() {
        String json = AetheriumGradlePlugin.jijMetadataJson(List.of(
                "aetherium-core-1.0.0-SNAPSHOT.jar", "aetherium-loader-1.0.0-SNAPSHOT.jar"));
        assertTrue(json.contains("\"artifact\": \"aetherium-core\""), json);
        assertTrue(json.contains("\"artifact\": \"aetherium-loader\""), json);
        assertTrue(json.contains("\"artifactVersion\": \"1.0.0-SNAPSHOT\""), json);
        assertTrue(json.contains("\"path\": \"META-INF/jarjar/aetherium-core-1.0.0-SNAPSHOT.jar\""), json);
        // No org.aetherium classes flattened — packaging is nested jars only.
        assertTrue(json.contains("\"group\": \"org.aetherium\""), json);
    }

    @Test
    void artifactVersionIsParsedFromJarName() {
        assertArrayEq(new String[]{"aetherium-core", "1.0.0-SNAPSHOT"},
                AetheriumGradlePlugin.parseArtifactVersion("aetherium-core-1.0.0-SNAPSHOT.jar"));
        assertArrayEq(new String[]{"aetherium-gradle-plugin", "2.1.0"},
                AetheriumGradlePlugin.parseArtifactVersion("aetherium-gradle-plugin-2.1.0.jar"));
    }

    @Test
    void serviceFilesAreConcatenatedNotOverwritten() {
        // Two jars both ship META-INF/services/org.aetherium.injector.InjectionProvider.
        Map<String, List<String>> jarA = Map.of(
                "org.aetherium.injector.InjectionProvider", List.of("# header", "modA.ProviderA"));
        Map<String, List<String>> jarB = Map.of(
                "org.aetherium.injector.InjectionProvider", List.of("modB.ProviderB", "modA.ProviderA"));
        Map<String, String> merged = ServiceFileMerger.mergeAll(List.of(jarA, jarB));

        String providers = merged.get("org.aetherium.injector.InjectionProvider");
        assertTrue(providers.contains("modA.ProviderA"), providers);
        assertTrue(providers.contains("modB.ProviderB"), providers);
        // Comment stripped, duplicate collapsed → exactly two providers, A before B (first-seen order).
        assertEquals(List.of("modA.ProviderA", "modB.ProviderB"), providers.strip().lines().toList());
        assertFalse(providers.contains("#"), "comments must be stripped");
    }

    private static void assertArrayEq(String[] expected, String[] actual) {
        assertEquals(List.of(expected), List.of(actual));
    }

    @Test
    void unifiedMetadataIsValidForBothLoaders() {
        String toml = AetheriumGradlePlugin.neoforgeModsToml("ironworks", "Ironworks", "1.2.3");
        assertTrue(toml.contains("modId = \"ironworks\""));
        assertTrue(toml.contains("modLoader = \"javafml\""));
        assertTrue(toml.contains("AGPL-3.0-or-later"));

        String json = AetheriumGradlePlugin.fabricModJson("ironworks", "Ironworks", "1.2.3");
        assertTrue(json.contains("\"id\": \"ironworks\""));
        assertTrue(json.contains("\"fabricloader\""));
        assertTrue(json.contains("1.2.3"));
    }

    @Test
    void modIdIsSanitizedForBothLoaders() {
        // NeoForge forbids hyphens; separators must collapse to '_', and a digit-leading id is fixed.
        assertEquals("my_cool_mod", AetheriumGradlePlugin.sanitizeModId("My-Cool Mod"));
        assertEquals("mod_9lives", AetheriumGradlePlugin.sanitizeModId("9lives"));
        assertTrue(Character.isLetter(AetheriumGradlePlugin.sanitizeModId("123").charAt(0)));
    }
}
