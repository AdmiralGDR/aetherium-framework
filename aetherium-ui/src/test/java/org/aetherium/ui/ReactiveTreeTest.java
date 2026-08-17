/*
 * Aetherium Framework — reactive memoized-layout (ReactiveTree) tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import org.aetherium.ui.reactive.Signal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReactiveTreeTest {

    private static final Rect VIEWPORT = new Rect(0, 0, 100, 100);

    private static ReactiveTree screenOf(Signal<String> label) {
        return new ReactiveTree(() -> new Container(FlexDirection.COLUMN).children(new Text(label.get())));
    }

    @Test
    void idleFramesReuseTheCachedLayout() {
        ReactiveTree screen = screenOf(Signal.of("A"));
        screen.layout(VIEWPORT, UiMetrics.DEFAULT);
        assertEquals(1, screen.layoutCount(), "first layout runs");
        screen.layout(VIEWPORT, UiMetrics.DEFAULT);
        screen.layout(VIEWPORT, UiMetrics.DEFAULT);
        assertEquals(1, screen.layoutCount(), "idle frames reuse the cached layout — zero relayout work");
    }

    @Test
    void aSignalChangeTriggersExactlyOneRelayout() {
        Signal<String> label = Signal.of("A");
        ReactiveTree screen = screenOf(label);
        screen.layout(VIEWPORT, UiMetrics.DEFAULT);
        assertEquals(1, screen.layoutCount());

        label.set("B"); // state change -> the reactive tree rebuilds
        screen.layout(VIEWPORT, UiMetrics.DEFAULT);
        assertEquals(2, screen.layoutCount(), "a state change causes exactly one relayout");

        screen.layout(VIEWPORT, UiMetrics.DEFAULT);
        assertEquals(2, screen.layoutCount(), "then it is cached again");
    }

    @Test
    void unchangedSignalValueDoesNotRelayout() {
        Signal<String> label = Signal.of("A");
        ReactiveTree screen = screenOf(label);
        screen.layout(VIEWPORT, UiMetrics.DEFAULT);
        label.set("A"); // same value -> no rebuild (equality-dampened) -> no relayout
        screen.layout(VIEWPORT, UiMetrics.DEFAULT);
        assertEquals(1, screen.layoutCount(), "setting the same value must not relayout");
    }

    @Test
    void aViewportChangeRelayouts() {
        ReactiveTree screen = screenOf(Signal.of("A"));
        screen.layout(VIEWPORT, UiMetrics.DEFAULT);
        screen.layout(new Rect(0, 0, 200, 100), UiMetrics.DEFAULT);
        assertEquals(2, screen.layoutCount(), "a resized viewport forces a relayout");
    }

    @Test
    void renderLaysOutAndPaints() {
        ReactiveTree screen = new ReactiveTree(() -> new Text("hi"));
        RecordingUiRenderer r = new RecordingUiRenderer();
        screen.render(VIEWPORT, UiMetrics.DEFAULT, r);
        assertTrue(r.textCount() >= 1, () -> "the reactive screen paints its text: " + r.commands());
        assertEquals(1, screen.layoutCount());
    }
}
