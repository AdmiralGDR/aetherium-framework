/*
 * Aetherium Framework — JUnit coverage for static hook contract verification (Consistency).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.contract;

import org.aetherium.injector.contract.Constraint;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN: Locks in the Consistency pillar — the symbolic sign analyzer flags provable return-contract
 * violations while leaving unprovable returns as unverified (no false alarms).
 * RU: Фиксирует столп согласованности — знаковый анализатор помечает доказуемые нарушения контракта
 * возврата, оставляя недоказуемые как «не проверено» (без ложных тревог).
 */
final class ContractAnalyzerTest {

    @Test
    void selfTestPasses() {
        assertTrue(ContractSelfTest.run().passed(), "contract self-test must pass end-to-end");
    }

    @Test
    void selfTestFlagsExactlyTheTwoProvableViolations() {
        ContractSelfTest.Result r = ContractSelfTest.run();
        assertEquals(2, r.violations(), "return -5 (NON_NEGATIVE) and return 0 (POSITIVE) are the two warnings");
        assertTrue(r.negativeViolated());
        assertTrue(r.zeroUnderPositiveViolated());
        assertTrue(r.variableUnverified(), "a loaded variable is unverified, not a violation");
    }

    @Test
    void signLatticeMatchesTheConstraintSemantics() {
        // NON_NEGATIVE forbids only NEGATIVE.
        assertTrue(Sign.NEGATIVE.violates(Constraint.NON_NEGATIVE));
        assertTrue(Sign.ZERO.violates(Constraint.POSITIVE));
        assertTrue(Sign.POSITIVE.violates(Constraint.NON_POSITIVE));
        // UNKNOWN never *definitely* violates.
        for (Constraint c : EnumSet.allOf(Constraint.class)) {
            assertTrue(!Sign.UNKNOWN.violates(c), "UNKNOWN must never be a definite violation for " + c);
        }
        // INEG on a positive constant flips the sign.
        assertEquals(Sign.NEGATIVE, Sign.POSITIVE.negate());
        assertEquals(Sign.NEGATIVE, Sign.mul(Sign.POSITIVE, Sign.NEGATIVE));
    }
}
