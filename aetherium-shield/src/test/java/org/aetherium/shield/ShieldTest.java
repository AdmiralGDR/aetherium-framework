/*
 * Aetherium Framework — shield tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShieldTest {

    @Test
    void endToEndProtection() throws ReflectiveOperationException {
        ShieldSelfTest.Result r = ShieldSelfTest.run();
        assertTrue(r.passed(), () -> "shield self-test failed: " + r.notes());
        assertTrue(r.stringHidden(), "secret string must be absent from protected bytes");
        assertTrue(r.debugStripped(), "debug metadata must be stripped");
        assertTrue(r.renamedButRuns(), "class must be renamed to an opaque name");
        assertTrue(r.secretDecodedAtRuntime(), "encrypted string must decode correctly at runtime");
        assertEquals(41, r.computeResult(), "renamed class must still compute correctly");
        assertTrue(r.tamperDetected(), "integrity manifest must detect a tamper");
        assertTrue(r.watermarkTraceable(), "author watermark must be extractable");
        assertTrue(r.brokenInputReverts(), "unprotectable input must revert cleanly (no crash)");
    }

    @Test
    void stringEncryptionRoundTrips() {
        for (String s : new String[]{"", "a", "Insufficient essence!", "минералы", "key=9F3A/secret"}) {
            int key = 0x1234 ^ s.hashCode();
            String cipher = StringEncryptionTransformer.encode(s, key);
            String back = StringEncryptionTransformer.encode(cipher, key); // symmetric
            assertEquals(s, back, "XOR encode/decode must be symmetric for: " + s);
        }
    }
}
