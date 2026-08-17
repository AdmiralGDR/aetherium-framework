/*
 * Aetherium Framework — reactive core (Signal/Effect/Computed) tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.reactive;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReactiveTest {

    @Test
    void signalGetSetUpdatePeek() {
        Signal<Integer> s = Signal.of(1);
        assertEquals(1, s.peek());
        s.set(5);
        assertEquals(5, s.peek());
        s.update(v -> v + 1);
        assertEquals(6, s.peek());
    }

    @Test
    void effectRunsOnceThenOnEveryRealChange() {
        Signal<Integer> s = Signal.of(0);
        AtomicInteger runs = new AtomicInteger();
        Effect.create(() -> {
            s.get();
            runs.incrementAndGet();
        });
        assertEquals(1, runs.get(), "an effect runs once immediately");

        s.set(1);
        assertEquals(2, runs.get(), "a real change re-runs the effect");

        s.set(1); // same value
        assertEquals(2, runs.get(), "setting the same value must not re-run (equality-dampened)");
    }

    @Test
    void effectTracksDynamicDependenciesAndCleansUp() {
        Signal<Boolean> useA = Signal.of(true);
        Signal<String> a = Signal.of("a");
        Signal<String> b = Signal.of("b");
        AtomicInteger runs = new AtomicInteger();
        StringBuilder seen = new StringBuilder();

        Effect.create(() -> {
            seen.setLength(0);
            seen.append(useA.get() ? a.get() : b.get());
            runs.incrementAndGet();
        });
        assertEquals(1, runs.get());
        assertEquals("a", seen.toString());
        assertEquals(0, b.subscriberCount(), "b is NOT a dependency yet (its branch was not taken)");

        // While the branch reads `a`, changing `b` must not re-run the effect.
        b.set("B");
        assertEquals(1, runs.get(), "b is not a tracked source, so its change is ignored");

        // Switch the branch to read `b`; now `a` must be dropped as a source.
        useA.set(false);
        assertEquals(2, runs.get());
        assertEquals("B", seen.toString());

        a.set("A2");
        assertEquals(2, runs.get(), "a was un-subscribed when the branch stopped reading it (no stale dep)");

        b.set("B2");
        assertEquals(3, runs.get(), "b is now the tracked source");
    }

    @Test
    void disposeStopsTheEffectAndUnsubscribes() {
        Signal<Integer> s = Signal.of(0);
        AtomicInteger runs = new AtomicInteger();
        Effect e = Effect.create(() -> {
            s.get();
            runs.incrementAndGet();
        });
        assertEquals(1, runs.get());
        assertEquals(1, s.subscriberCount());

        e.dispose();
        assertTrue(e.disposed());
        assertEquals(0, s.subscriberCount(), "dispose unsubscribes from every source");

        s.set(1);
        assertEquals(1, runs.get(), "a disposed effect never runs again");
    }

    @Test
    void computedDerivesAndReDerives() {
        Signal<Integer> a = Signal.of(2);
        Signal<Integer> b = Signal.of(3);
        Computed<Integer> sum = Computed.of(() -> a.get() + b.get());
        assertEquals(5, sum.peek());

        a.set(10);
        assertEquals(13, sum.peek(), "changing a source re-derives the computed");
    }

    @Test
    void computedIsObservableAndEqualityDampens() {
        Signal<Integer> n = Signal.of(4);
        Computed<Boolean> even = Computed.of(() -> n.get() % 2 == 0);
        AtomicInteger observed = new AtomicInteger();
        Effect.create(() -> {
            even.get();
            observed.incrementAndGet();
        });
        assertEquals(1, observed.get());
        assertTrue(even.peek());

        n.set(6); // still even -> computed value unchanged -> downstream effect must NOT re-run
        assertEquals(1, observed.get(), "an unchanged recomputation does not propagate");

        n.set(7); // now odd -> value changes -> effect re-runs
        assertEquals(2, observed.get());
        assertFalse(even.peek());
    }

    @Test
    void diamondSettlesWithoutRedundantRuns() {
        Signal<Integer> root = Signal.of(1);
        Computed<Integer> left = Computed.of(() -> root.get() + 1);
        Computed<Integer> right = Computed.of(() -> root.get() * 2);
        Computed<Integer> bottom = Computed.of(() -> left.get() + right.get());
        assertEquals(4, bottom.peek()); // (1+1) + (1*2)

        root.set(3);
        assertEquals(10, bottom.peek()); // (3+1) + (3*2)
    }
}
