/*
 * Aetherium Framework — fail-loud Outcome contract tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OutcomeTest {

    private static final Diagnostic REASON = Diagnostic.warn("AE-TEST-SKIP", "no device");

    @Test
    void ranCarriesValueAndClassifies() {
        Outcome<Integer> o = Outcome.ran(42);
        assertTrue(o.ran());
        assertFalse(o.skipped());
        assertFalse(o.failed());
        assertEquals(42, o.value().orElseThrow());
        assertTrue(o.reason().isEmpty());
        assertEquals(42, o.orElse(-1));
        assertEquals(42, o.orElseThrow());
    }

    @Test
    void skippedReportsReasonAndUsesFallback() {
        Outcome<Integer> o = Outcome.skipped(REASON);
        assertTrue(o.skipped());
        assertFalse(o.ran());
        assertTrue(o.value().isEmpty());
        assertSame(REASON, o.reason().orElseThrow());
        assertEquals(-1, o.orElse(-1));
        assertEquals(7, o.orElseGet(() -> 7));
    }

    @Test
    void failedReportsReason() {
        Outcome<Integer> o = Outcome.failed(REASON);
        assertTrue(o.failed());
        assertFalse(o.ran());
        assertSame(REASON, o.reason().orElseThrow());
        assertEquals(0, o.orElse(0));
    }

    @Test
    void orElseThrowRaisesTheDiagnosticWhenNotRun() {
        AetheriumException skipped = assertThrows(AetheriumException.class, () -> Outcome.skipped(REASON).orElseThrow());
        assertSame(REASON, skipped.diagnostic());
        AetheriumException failed = assertThrows(AetheriumException.class, () -> Outcome.failed(REASON).orElseThrow());
        assertSame(REASON, failed.diagnostic());
    }

    @Test
    void mapTransformsRanAndPassesReasonThrough() {
        assertEquals("42", Outcome.ran(42).map(Object::toString).orElseThrow());

        Outcome<String> skipped = Outcome.<Integer>skipped(REASON).map(Object::toString);
        assertTrue(skipped.skipped());
        assertSame(REASON, skipped.reason().orElseThrow());

        Outcome<String> failed = Outcome.<Integer>failed(REASON).map(Object::toString);
        assertTrue(failed.failed());
    }

    @Test
    void callbacksFireOnlyForTheMatchingCase() {
        AtomicReference<Diagnostic> onSkip = new AtomicReference<>();
        AtomicReference<Diagnostic> onFail = new AtomicReference<>();

        Outcome.ran(1).onSkipped(onSkip::set).onFailed(onFail::set);
        assertNull(onSkip.get());
        assertNull(onFail.get());

        Outcome.skipped(REASON).onSkipped(onSkip::set).onFailed(onFail::set);
        assertSame(REASON, onSkip.get());
        assertNull(onFail.get());

        Outcome.failed(REASON).onFailed(onFail::set);
        assertSame(REASON, onFail.get());
    }

    @Test
    void ranOfNullStillCountsAsRun() {
        Outcome<String> o = Outcome.ran(null);
        assertTrue(o.ran(), "a Ran(null) is a run that produced null, not a skip");
        assertTrue(o.value().isEmpty(), "value() is Optional, so a null Ran reads as empty");
        assertNull(o.orElse("fallback"), "orElse returns the ran null, not the fallback");
    }

    @Test
    void exhaustiveSwitchNeedsNoDefault() {
        // Compiles only because the sealed hierarchy is exhaustive (ARCHITECTURE §5 — no defensive default).
        Outcome<Integer> outcome = Outcome.skipped(REASON);
        String label = switch (outcome) {
            case Outcome.Ran<Integer> r -> "ran:" + r.result();
            case Outcome.Skipped<Integer> s -> "skipped:" + s.diagnostic().code();
            case Outcome.Failed<Integer> f -> "failed:" + f.diagnostic().code();
        };
        assertEquals("skipped:AE-TEST-SKIP", label);
    }

    @Test
    void skippedAndFailedRejectNullReason() {
        assertThrows(NullPointerException.class, () -> Outcome.skipped(null));
        assertThrows(NullPointerException.class, () -> Outcome.failed(null));
    }
}
