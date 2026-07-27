/*
 * Aetherium Framework — proves the FFM capability helper degrades on an Error, not just an Exception.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proves {@link Capabilities} solves the exact trap described — no game, no framework.
 *
 * <p>EN: The whole point is that a preview class throws {@link UnsupportedClassVersionError}, an {@link Error}
 * that {@code catch (RuntimeException)} misses. So the self-test throws a real {@code Error} (a
 * {@code LinkageError}, the family {@code UnsupportedClassVersionError} belongs to) from the preview supplier
 * and asserts: (1) {@link Capabilities#ffm} returns the fallback value, no {@code Throwable} escapes; (2) when
 * the preview works, its value is used; (3) {@link Capabilities#ffmLazy} probes exactly once and then sticks to
 * the winning path; (4) {@link Capabilities#available} reports false for a throwing probe.
 * RU: Смысл в том, что preview-класс бросает {@link UnsupportedClassVersionError} — {@link Error}, который
 * {@code catch (RuntimeException)} не ловит. Тест бросает настоящий {@code Error} из preview и проверяет: ffm
 * возвращает fallback без утечки Throwable; при рабочем preview берётся его значение; ffmLazy пробует ровно
 * один раз; available возвращает false для бросающего зонда.
 */
public final class CapabilitiesSelfTest {

    private CapabilitiesSelfTest() {
    }

    /** Structured outcome. */
    public record Result(boolean errorDegradesToFallback, boolean previewUsedWhenAvailable,
                         boolean lazyProbesOnce, boolean availableReportsFalseOnError,
                         boolean functionFormAndAvailableStable, List<String> notes) {
        public boolean passed() {
            return errorDegradesToFallback && previewUsedWhenAvailable && lazyProbesOnce
                    && availableReportsFalseOnError && functionFormAndAvailableStable;
        }
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();

        // (1) A preview supplier that throws a real Error (the UnsupportedClassVersionError family) degrades.
        String degraded = Capabilities.ffm(
                () -> { throw new LinkageError("simulated preview class not enabled"); },
                () -> "pure-java");
        boolean errorDegrades = "pure-java".equals(degraded);
        notes.add("ffm(previewThrowsError) = '" + degraded + "' (want 'pure-java')");

        // (2) When the preview works, its value wins.
        String preview = Capabilities.ffm(() -> "off-heap", () -> "pure-java");
        boolean previewUsed = "off-heap".equals(preview);
        notes.add("ffm(previewWorks) = '" + preview + "' (want 'off-heap')");

        // (3) ffmLazy probes once: the throwing preview is attempted exactly one time, then fallback sticks.
        AtomicInteger previewCalls = new AtomicInteger();
        java.util.function.Supplier<String> lazy = Capabilities.ffmLazy(
                () -> { previewCalls.incrementAndGet(); throw new LinkageError("nope"); },
                () -> "pure-java");
        String a = lazy.get();
        String b = lazy.get();
        String c = lazy.get();
        boolean lazyOnce = previewCalls.get() == 1 && "pure-java".equals(a) && "pure-java".equals(b)
                && "pure-java".equals(c);
        notes.add("ffmLazy: preview attempted " + previewCalls.get() + " time(s) over 3 gets (want 1), all='"
                + a + "/" + b + "/" + c + "'");

        // (4) available() reports false when the probe throws an Error.
        boolean avail = Capabilities.available(() -> { throw new LinkageError("no preview"); });
        boolean availableFalse = !avail;
        notes.add("available(throwsError) = " + avail + " (want false)");

        // (5) The argument-carrying ffmLazy () probes once, then degrades with the argument.
        AtomicInteger fnCalls = new AtomicInteger();
        java.util.function.Function<Integer, String> fn = Capabilities.ffmLazy(
                slot -> { fnCalls.incrementAndGet(); throw new LinkageError("no ffm"); },
                slot -> "slot-" + slot);
        String s1 = fn.apply(1);
        String s2 = fn.apply(2);
        boolean fnLazyOnce = fnCalls.get() == 1 && "slot-1".equals(s1) && "slot-2".equals(s2);
        notes.add("ffmLazy(Function): preview attempted " + fnCalls.get() + " (want 1), apply(1)='" + s1
                + "', apply(2)='" + s2 + "'");

        // (6) ffmAvailable() is stable across calls (probed once, cached) — the verdict never flips.
        boolean stable = Capabilities.ffmAvailable() == Capabilities.ffmAvailable();
        notes.add("ffmAvailable() stable across calls = " + stable + " (verdict=" + Capabilities.ffmAvailable() + ")");
        boolean functionAndAvailableOk = fnLazyOnce && stable;

        return new Result(errorDegrades, previewUsed, lazyOnce, availableFalse, functionAndAvailableOk, notes);
    }
}
