/*
 * Aetherium Framework — display-widget paint tests (ProgressBar, Divider).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WidgetsTest {

    @Test
    void progressBarPaintsTrackThenFillToFraction() {
        RecordingUiRenderer r = new RecordingUiRenderer();
        new ProgressBar(0.5).paintContent(r, new Rect(0, 0, 100, 10), UiMetrics.DEFAULT);
        List<RecordingUiRenderer.Cmd> cmds = r.commands();
        assertEquals(2, cmds.size(), "track then fill");
        assertEquals(100, cmds.get(0).w(), "the track spans the full inner width");
        assertEquals(10, cmds.get(0).h());
        assertEquals(50, cmds.get(1).w(), "the fill is fraction * width");
    }

    @Test
    void progressBarAtZeroPaintsOnlyTheTrack() {
        RecordingUiRenderer r = new RecordingUiRenderer();
        new ProgressBar(0.0).paintContent(r, new Rect(0, 0, 100, 10), UiMetrics.DEFAULT);
        assertEquals(1, r.commands().size(), "no fill segment when fraction is 0");
    }

    @Test
    void progressBarClampsFraction() {
        assertEquals(1.0, new ProgressBar(5.0).fraction());
        assertEquals(0.0, new ProgressBar(-1.0).fraction());
        assertEquals(0.25, new ProgressBar(0.0).fraction(0.25).fraction());
    }

    @Test
    void dividerPaintsOneRuleOfItsThickness() {
        RecordingUiRenderer r = new RecordingUiRenderer();
        new Divider().thickness(2).paintContent(r, new Rect(0, 0, 80, 2), UiMetrics.DEFAULT);
        List<RecordingUiRenderer.Cmd> cmds = r.commands();
        assertEquals(1, cmds.size());
        assertEquals(80, cmds.get(0).w());
        assertEquals(2, cmds.get(0).h());
    }

    @Test
    void dividerThicknessFloorsAtOne() {
        assertEquals(1, new Divider().thickness(0).thickness());
        assertEquals(3, new Divider().thickness(3).thickness());
    }
}
