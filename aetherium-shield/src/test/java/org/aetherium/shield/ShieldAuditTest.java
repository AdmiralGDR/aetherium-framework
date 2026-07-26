/*
 * Aetherium Framework — protection-audit tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShieldAuditTest {

    @Test
    void auditGatesRawVersusShielded() {
        ShieldAuditSelfTest.Result r = ShieldAuditSelfTest.run();
        assertTrue(r.passed(), () -> "shield-audit self-test failed: " + r.notes());
        assertTrue(r.rawIsLeaky(), "an unshielded class must be reported leaky");
        assertTrue(r.protectedIsResistant(), "a shielded class must be reported analysis-resistant");
        assertTrue(r.protectedIsWatermarked(), "a shielded class must carry its author watermark");
    }

    @Test
    void readableRunSeparatesPlaintextFromCiphertext() {
        // Plaintext has letter runs; XOR ciphertext (16-bit key stream) statistically never does.
        assertTrue(ShieldAudit.hasReadableRun("Insufficient essence"));
        assertTrue(ShieldAudit.hasReadableRun("faction"));
        assertFalse(ShieldAudit.hasReadableRun("abӲꌝqⱾ1鼺")); // short runs + high code points
        assertFalse(ShieldAudit.hasReadableRun("a1b2c3d4")); // no 5-letter run
    }
}
