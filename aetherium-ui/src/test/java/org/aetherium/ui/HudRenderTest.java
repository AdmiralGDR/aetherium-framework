/*
 * Aetherium Framework — HUD overlay render test (follow-up).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class HudRenderTest {

    @Test
    void aHudLaysOutAndPaintsHeadless() {
        // A HUD is a plain widget tree — it must lay out and paint through the same engine the screens use,
        // provable with no client. This is the offline proof AetheriumUi.addHud draws something.
        AetheriumHud hud = new AetheriumHud() {
            @Override
            public Widget<?> build(Rect viewport) {
                return Ui.column().padding(Insets.all(4)).children(
                        Ui.label("Essence: 42"),
                        Ui.label("Owner: Steve"));
            }
        };
        assertTrue(hud.visible(), "a HUD is visible by default");

        RecordingUiRenderer renderer = new RecordingUiRenderer();
        Rect viewport = new Rect(0, 0, 320, 180);
        UiRuntime.render(hud.build(viewport), viewport, UiMetrics.DEFAULT, renderer);

        assertTrue(renderer.textCount() >= 2, () -> "the HUD's two labels must paint: " + renderer.commands());
    }
}
