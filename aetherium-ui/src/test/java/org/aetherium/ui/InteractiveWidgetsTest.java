/*
 * Aetherium Framework — interactive widget tests (Toggle, Checkbox, Slider).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InteractiveWidgetsTest {

    @Test
    void toggleFlipsAndFiresOnClick() {
        AtomicBoolean fired = new AtomicBoolean(false);
        Toggle t = new Toggle(false).onChange(fired::set);
        assertTrue(t.interactive());
        assertFalse(t.on());
        assertTrue(t.handleClick(0, 0, 28, 16));
        assertTrue(t.on(), "a click flips the toggle on");
        assertTrue(fired.get(), "onChange fired with the new state");
        t.handleClick(0, 0, 28, 16);
        assertFalse(t.on(), "a second click flips it back");
    }

    @Test
    void toggleProgrammaticSetDoesNotFire() {
        AtomicInteger fires = new AtomicInteger();
        Toggle t = new Toggle(false).onChange(v -> fires.incrementAndGet());
        t.on(true);
        assertTrue(t.on());
        assertEquals(0, fires.get(), "programmatic on() must not fire onChange");
    }

    @Test
    void togglePaintsTrackAndKnob() {
        RecordingUiRenderer r = new RecordingUiRenderer();
        new Toggle(true).paintContent(r, new Rect(0, 0, 28, 16), UiMetrics.DEFAULT);
        assertEquals(2, r.commands().size(), "track + knob");
    }

    @Test
    void checkboxTogglesOnClick() {
        AtomicBoolean state = new AtomicBoolean();
        Checkbox c = new Checkbox(false).onChange(state::set);
        assertTrue(c.handleClick(0, 0, 12, 12));
        assertTrue(c.checked());
        assertTrue(state.get());
        c.handleClick(0, 0, 12, 12);
        assertFalse(c.checked());
    }

    @Test
    void checkboxPaintsMarkOnlyWhenChecked() {
        RecordingUiRenderer off = new RecordingUiRenderer();
        new Checkbox(false).paintContent(off, new Rect(0, 0, 12, 12), UiMetrics.DEFAULT);
        assertEquals(1, off.commands().size(), "just the box when unchecked");
        RecordingUiRenderer on = new RecordingUiRenderer();
        new Checkbox(true).paintContent(on, new Rect(0, 0, 12, 12), UiMetrics.DEFAULT);
        assertEquals(2, on.commands().size(), "box + mark when checked");
    }

    @Test
    void sliderMapsClickToValueAndClamps() {
        double[] got = {Double.NaN};
        Slider s = new Slider(0.0, 10.0, 0.0).onChange(v -> got[0] = v);
        assertTrue(s.handleClick(50, 0, 100, 12)); // midpoint of the track -> 5
        assertEquals(5.0, s.value(), 1e-9);
        assertEquals(5.0, got[0], 1e-9);

        s.handleClick(200, 0, 100, 12); // past the right end -> clamped to max
        assertEquals(10.0, s.value(), 1e-9);
        s.handleClick(-10, 0, 100, 12); // past the left end -> clamped to min
        assertEquals(0.0, s.value(), 1e-9);
    }

    @Test
    void sliderValueSetterClampsWithoutFiring() {
        AtomicInteger fires = new AtomicInteger();
        Slider s = new Slider(0.0, 10.0, 0.0).onChange(v -> fires.incrementAndGet());
        s.value(20.0);
        assertEquals(10.0, s.value(), 1e-9);
        s.value(-5.0);
        assertEquals(0.0, s.value(), 1e-9);
        assertEquals(0, fires.get(), "programmatic value() must not fire onChange");
    }

    @Test
    void sliderRejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> new Slider(10.0, 0.0, 5.0));
    }
}
