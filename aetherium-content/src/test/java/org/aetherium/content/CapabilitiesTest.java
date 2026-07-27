/*
 * Aetherium Framework — FFM capability-helper tests (). Hosted here because core carries no JUnit.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import org.aetherium.core.Capabilities;
import org.aetherium.core.CapabilitiesSelfTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CapabilitiesTest {

    @Test
    void selfTestPasses() {
        CapabilitiesSelfTest.Result r = CapabilitiesSelfTest.run();
        assertTrue(r.passed(), () -> "capabilities self-test failed: " + r.notes());
    }

    @Test
    void ffmCatchesErrorNotJustException() {
        // The crux of an Error (not a RuntimeException) must still degrade to the fallback.
        assertEquals("fallback", Capabilities.ffm(
                () -> { throw new UnsupportedClassVersionError("Preview features are not enabled"); },
                () -> "fallback"));
        assertEquals("preview", Capabilities.ffm(() -> "preview", () -> "fallback"));
        assertFalse(Capabilities.available(() -> { throw new NoClassDefFoundError("StructArena"); }));
        assertTrue(Capabilities.available(() -> { /* loads fine */ }));
    }
}
