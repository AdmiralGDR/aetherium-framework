/*
 * Aetherium Framework — in-game verification tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.verify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModVerifyTest {

    @Test
    void inspectorVerifiesAndAnalysesMods() throws Exception {
        ModVerifySelfTest.Result r = ModVerifySelfTest.run();
        assertTrue(r.passed(), () -> "mod-verify self-test failed: " + r.notes());
        assertTrue(r.intactVerdict(), "a mod whose bytes match its manifest must read SIGNED_INTACT");
        assertTrue(r.tamperedVerdict(), "a mod whose bytes differ from its manifest must read TAMPERED");
        assertTrue(r.unsignedVerdict(), "a mod with no manifest must read UNSIGNED");
        assertTrue(r.screenRenders(), "the inspector screen must lay out + paint offline");
    }
}
