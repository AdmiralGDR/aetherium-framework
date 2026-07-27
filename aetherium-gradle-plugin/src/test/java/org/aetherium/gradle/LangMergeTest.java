/*
 * Aetherium Framework — lang-merge unit test (feedback ).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gradle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LangMergeTest {

    @Test
    void parsesFlatLangJson() {
        Map<String, String> m = AetheriumGradlePlugin.parseFlatJson(
                "{\n  \"block.mod.a\": \"Anomaly Core\",\n  \"item.mod.b\": \"Steel\"\n}\n");
        assertEquals(2, m.size());
        assertEquals("Anomaly Core", m.get("block.mod.a"));
        assertEquals("Steel", m.get("item.mod.b"));
    }

    @Test
    void nonFlatObjectIsRejected() {
        assertNull(AetheriumGradlePlugin.parseFlatJson("{ \"nested\": { \"x\": 1 } }"),
                "a non-flat lang object must not be merged (returns null → left alone)");
    }

    @Test
    void mergeIsKeyUnionRoundTrippable() {
        // AP contributes one key, the author contributes four — the union must be all five (the scenario).
        Map<String, String> ap = AetheriumGradlePlugin.parseFlatJson("{ \"block.mod.a\": \"Anomaly Core\" }");
        Map<String, String> user = AetheriumGradlePlugin.parseFlatJson(
                "{ \"a\":\"1\",\"b\":\"2\",\"c\":\"3\",\"d\":\"4\" }");
        // Emulate the merge order (AP first, author last so author wins).
        ap.putAll(user);
        assertEquals(5, ap.size());
        String json = AetheriumGradlePlugin.writeFlatJson(ap);
        Map<String, String> round = AetheriumGradlePlugin.parseFlatJson(json);
        assertEquals(ap, round, "written lang JSON must round-trip");
        assertTrue(json.indexOf("\"a\"") < json.indexOf("\"block.mod.a\""), "keys are sorted");
    }

    @Test
    void warnsWhenAGeneratedKeyIsMissingFromAnotherLanguage() {
        // en_us has the AP-generated itemGroup key; ru_ru (shipped) does not → one asymmetry warning ().
        String enRel = "assets/examplemod/lang/en_us.json";
        String ruRel = "assets/examplemod/lang/ru_ru.json";
        Map<String, Map<String, String>> merged = Map.of(
                enRel, Map.of("itemGroup.examplemod", "a downstream mod", "block.x", "Anomaly Core"),
                ruRel, Map.of("block.x", "Аномальное ядро"));
        Map<String, Set<String>> generated = Map.of(
                enRel, Set.of("itemGroup.examplemod"), ruRel, Set.of());

        List<String> warnings = AetheriumGradlePlugin.langAsymmetryWarnings(merged, generated);
        assertEquals(1, warnings.size(), () -> "exactly one asymmetry warning expected: " + warnings);
        assertTrue(warnings.get(0).contains("itemGroup.examplemod") && warnings.get(0).contains("ru_ru.json"),
                () -> "the warning must name the key and the language file: " + warnings.get(0));
    }

    @Test
    void noWarningWhenLanguagesAreSymmetric() {
        String enRel = "assets/mod/lang/en_us.json";
        String ruRel = "assets/mod/lang/ru_ru.json";
        Map<String, Map<String, String>> merged = Map.of(
                enRel, Map.of("itemGroup.mod", "Mod"),
                ruRel, Map.of("itemGroup.mod", "Мод")); // author translated it → no asymmetry
        Map<String, Set<String>> generated = Map.of(enRel, Set.of("itemGroup.mod"), ruRel, Set.of());

        assertTrue(AetheriumGradlePlugin.langAsymmetryWarnings(merged, generated).isEmpty());
    }

    @Test
    void escapesSurviveRoundTrip() {
        Map<String, String> m = AetheriumGradlePlugin.parseFlatJson(
                "{ \"k\": \"line1\\nline2 \\\"q\\\"\" }");
        assertEquals("line1\nline2 \"q\"", m.get("k"));
        assertEquals(m, AetheriumGradlePlugin.parseFlatJson(AetheriumGradlePlugin.writeFlatJson(m)));
    }
}
