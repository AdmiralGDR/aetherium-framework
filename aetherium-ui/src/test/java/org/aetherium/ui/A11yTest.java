/*
 * Aetherium Framework — accessibility (roles/labels + audit lint) tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class A11yTest {

    private static final Rect VIEWPORT = new Rect(0, 0, 200, 200);

    @Test
    void auditFlagsUnlabeledInteractiveWidgets() {
        Container root = new Container(FlexDirection.COLUMN).children(
                new Toggle(false),                     // unlabeled switch  -> flagged
                new Checkbox(false).label("Sound"),    // labeled           -> ok
                new Button("Save", () -> { }),         // named by its text -> ok
                new Slider(0.0, 10.0, 5.0));           // unlabeled slider  -> flagged
        LaidOut laid = FlexLayout.layout(root, VIEWPORT, UiMetrics.DEFAULT);

        List<String> issues = UiRuntime.auditAccessibility(laid);
        assertEquals(2, issues.size(), () -> "unlabeled Toggle + Slider only: " + issues);
        assertTrue(issues.stream().anyMatch(s -> s.contains("Toggle")));
        assertTrue(issues.stream().anyMatch(s -> s.contains("Slider")));
    }

    @Test
    void labeledInteractiveWidgetsAndNamedControlsPass() {
        Container root = new Container(FlexDirection.COLUMN).children(
                new Toggle(false).label("Music"),
                new Slider(0.0, 10.0, 5.0).label("Volume"),
                new Checkbox(true).label("Fullscreen"),
                new TextField().placeholder("Name"),   // placeholder is its accessible name
                new Button("OK", () -> { }));
        LaidOut laid = FlexLayout.layout(root, VIEWPORT, UiMetrics.DEFAULT);
        assertTrue(UiRuntime.auditAccessibility(laid).isEmpty(),
                () -> "every interactive widget is named: " + UiRuntime.auditAccessibility(laid));
    }

    @Test
    void defaultRolesAreSemantic() {
        assertEquals(Role.BUTTON, new Button("x", () -> { }).role());
        assertEquals(Role.SWITCH, new Toggle(false).role());
        assertEquals(Role.CHECKBOX, new Checkbox(false).role());
        assertEquals(Role.SLIDER, new Slider(0.0, 1.0, 0.0).role());
        assertEquals(Role.TEXT_FIELD, new TextField().role());
        assertEquals(Role.PROGRESS_BAR, new ProgressBar(0.0).role());
        assertEquals(Role.NONE, new Text("hi").role());
    }

    @Test
    void roleAndLabelCanBeOverridden() {
        Toggle t = new Toggle(false).role(Role.BUTTON).label("Custom");
        assertEquals(Role.BUTTON, t.role(), "explicit role overrides the default");
        assertEquals("Custom", t.accessibleName());

        assertEquals("Save", new Button("Save", () -> { }).accessibleName(), "a button is named by its text");
        assertEquals("Override", new Button("Save", () -> { }).label("Override").accessibleName(),
                "an explicit label wins over the button's text");
    }
}
