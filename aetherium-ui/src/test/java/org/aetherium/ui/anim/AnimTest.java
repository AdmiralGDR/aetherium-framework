/*
 * Aetherium Framework — animation (easing/tween/spring) tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.anim;

import org.aetherium.core.tick.FrameClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnimTest {

    @Test
    void easingCurvesHaveTheExpectedShape() {
        for (Easing e : new Easing[] {Easing.LINEAR, Easing.EASE_IN, Easing.EASE_OUT, Easing.EASE_IN_OUT}) {
            assertEquals(0.0, e.ease(0.0), 1e-9, "every easing starts at 0");
            assertEquals(1.0, e.ease(1.0), 1e-9, "every easing ends at 1");
        }
        assertEquals(0.5, Easing.LINEAR.ease(0.5), 1e-9);
        assertEquals(0.125, Easing.EASE_IN.ease(0.5), 1e-9, "cubic ease-in at 0.5 is 0.5^3");
        assertEquals(0.875, Easing.EASE_OUT.ease(0.5), 1e-9);
        assertEquals(0.5, Easing.EASE_IN_OUT.ease(0.5), 1e-9, "ease-in-out is symmetric about the midpoint");
    }

    @Test
    void tweenProgressIsClampedAndEased() {
        Tween t = new Tween(1_000L, 1_000L, Easing.LINEAR);
        assertEquals(0.0, t.progress(1_000L), 1e-9, "at the start, progress is 0");
        assertEquals(0.5, t.progress(1_500L), 1e-9, "halfway, linear progress is 0.5");
        assertEquals(1.0, t.progress(2_000L), 1e-9, "at the end, progress is 1");
        assertEquals(0.0, t.progress(500L), 1e-9, "before the start, clamped to 0");
        assertEquals(1.0, t.progress(9_999L), 1e-9, "after the end, clamped to 1 (holds the final frame)");
        assertFalse(t.done(1_500L));
        assertTrue(t.done(2_000L));
    }

    @Test
    void zeroDurationTweenIsInstantlyComplete() {
        Tween t = new Tween(1_000L, 0L, Easing.EASE_IN_OUT);
        assertEquals(1.0, t.progress(1_000L), 1e-9);
        assertTrue(t.done(1_000L));
    }

    @Test
    void interpolationHelpers() {
        assertEquals(5.0, Tween.lerp(0.0, 10.0, 0.5), 1e-9);
        assertEquals(50, Tween.lerpInt(0, 100, 0.5));
        assertEquals(0, Tween.lerpInt(0, 100, 0.0));
        assertEquals(100, Tween.lerpInt(0, 100, 1.0));
        // Black -> white at the midpoint is mid-grey, alpha preserved.
        assertEquals(0xFF808080, Tween.lerpArgb(0xFF000000, 0xFFFFFFFF, 0.5));
        assertEquals(0xFF000000, Tween.lerpArgb(0xFF000000, 0xFFFFFFFF, 0.0));
        assertEquals(0xFFFFFFFF, Tween.lerpArgb(0xFF000000, 0xFFFFFFFF, 1.0));
    }

    @Test
    void tweenRejectsNegativeDuration() {
        assertThrows(IllegalArgumentException.class, () -> new Tween(0L, -1L, Easing.LINEAR));
    }

    @Test
    void criticalSpringConvergesToTargetWithoutOvershoot() {
        FrameClock.Manual clock = new FrameClock.Manual();
        Spring spring = Spring.critical(0.0, 120.0);
        spring.setTarget(100.0);

        double maxValue = Double.NEGATIVE_INFINITY;
        // Integrate ~2 s at 60 Hz.
        for (int i = 0; i < 120; i++) {
            clock.advanceMillis(16L);
            double v = spring.step(16.0 / 1000.0);
            maxValue = Math.max(maxValue, v);
        }
        assertTrue(spring.settled(0.5), "the spring settles at its target");
        assertEquals(100.0, spring.value(), 0.5);
        assertTrue(maxValue <= 101.0, "critical damping does not meaningfully overshoot the target: " + maxValue);
    }

    @Test
    void springRedirectPreservesMomentum() {
        Spring spring = Spring.critical(0.0, 80.0);
        spring.setTarget(50.0);
        for (int i = 0; i < 10; i++) {
            spring.step(0.016);
        }
        assertTrue(spring.value() > 0.0 && spring.value() < 50.0, "mid-flight toward the first target");
        spring.setTarget(0.0); // redirect back
        for (int i = 0; i < 200; i++) {
            spring.step(0.016);
        }
        assertEquals(0.0, spring.value(), 0.5, "after a redirect it settles at the new target");
    }

    @Test
    void springValidatesConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new Spring(0.0, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Spring(0.0, 1.0, -1.0));
        assertThrows(IllegalArgumentException.class, () -> Spring.critical(0.0, 10.0).step(-0.1));
    }
}
