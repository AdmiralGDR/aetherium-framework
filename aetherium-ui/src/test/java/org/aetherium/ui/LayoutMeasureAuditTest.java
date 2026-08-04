/*
 * Aetherium Framework — /: measure honours child size specs, and audit catches sibling overlap.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer's exact repro (a downstream mod ): a progress bar inside a HUD card. A bar row of explicit
 * {@code height(4)} used to measure as 0 (a {@link Spacer}'s intrinsic height is its padding), so its parent
 * card reserved no row for it and the bar overdrew the label above it — and {@link UiRuntime#audit} reported
 * <em>zero</em> violations because the bar was still <em>inside</em> the card.
 *
 * <p>: measuring now takes {@code max(spec, intrinsic)} so the card reserves the bar's row. : the audit
 * gained a sibling-overlap rule so the broken variant is caught, not silently passed.
 */
final class LayoutMeasureAuditTest {

    /** Build the feedback's card: a label, then a two-segment progress bar row of a fixed height. */
    private static Container hudCard(int barHeight, boolean sizeBar) {
        Container bar = Ui.row().height(barHeight);
        // Two segments of the bar. When sizeBar is true they are sized by size()/height (the case that used
        // to measure as 0); the row itself has an explicit height either way.
        Spacer filled = Ui.spacer().background(UiColor.rgb(0xE74C3C));
        Spacer track = Ui.spacer().background(UiColor.rgba(0x2A, 0x2A, 0x30, 0xFF));
        if (sizeBar) {
            filled.size(43, barHeight);
            track.size(53, barHeight);
        } else {
            filled.width(43).height(barHeight);
            track.width(53).height(barHeight);
        }
        bar.add(filled).add(track);
        return Ui.column().padding(Insets.all(2)).children(Ui.label("Focus 45/100"), bar);
    }

    @Test
    void cardReservesTheBarRowSoMeasureAndPlaceAgree() {
        // : the card's intrinsic height must now include the bar's explicit height, not treat it as 0.
        Container card = hudCard(4, true);
        int labelH = Ui.label("Focus 45/100").intrinsicHeight(UiMetrics.DEFAULT);
        int cardH = card.intrinsicHeight(UiMetrics.DEFAULT);
        assertTrue(cardH >= labelH + 4 + 2 * 2,
                () -> "card height " + cardH + " must reserve the label + the 4px bar row + padding");
    }

    @Test
    void auditCatchesTheOverlapWhenMeasureIsWrong() {
        // Simulate the pre-fix defect directly: a bar row whose measured extent is 0 while it is placed at
        // full height. A raw column with a zero-height row and a real label reproduces the overlap geometry;
        // the audit's new third rule must flag two siblings sharing pixels.
        Rect viewport = new Rect(0, 0, 200, 100);
        // Two children deliberately given intersecting boxes via absolute layout of a hand-built tree:
        // a label at the top and a bar drawn over it (what the broken measure produced on the real client).
        LaidOut label = new LaidOut(Ui.label("Focus 45/100"), new Rect(0, 0, 120, 12), List.of());
        LaidOut bar = new LaidOut(Ui.spacer().size(96, 4), new Rect(0, 8, 96, 4), List.of());
        LaidOut root = new LaidOut(Ui.column(), viewport, List.of(label, bar));

        List<String> violations = UiRuntime.audit(root, UiMetrics.DEFAULT);
        assertFalse(violations.isEmpty(), "the audit must flag overlapping siblings");
        assertTrue(violations.stream().anyMatch(v -> v.contains("overlapping siblings")),
                () -> "expected a sibling-overlap violation, got: " + violations);
    }

    @Test
    void correctlyMeasuredCardHasNoOverlapAtAnyViewport() {
        // + together: with the fix, the real laid-out card tiles the label and the bar on separate rows,
        // so the audit is clean at all three of the consumer's test viewports.
        Container card = hudCard(4, true);
        for (Rect viewport : List.of(new Rect(0, 0, 320, 240),
                new Rect(0, 0, 420, 400), new Rect(0, 0, 640, 480))) {
            LaidOut laid = FlexLayout.layout(card, viewport, UiMetrics.DEFAULT);
            List<String> violations = UiRuntime.audit(laid, UiMetrics.DEFAULT);
            assertTrue(violations.isEmpty(),
                    () -> "correctly measured card must have no violations at " + viewport + ": " + violations);
        }
    }
}
