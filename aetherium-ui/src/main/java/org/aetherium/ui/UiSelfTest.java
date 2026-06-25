/*
 * Aetherium Framework — UI framework self-test (layout + paint + click, fully offline).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds a declarative screen and exercises the whole runtime with no game present.
 *
 * <p>EN: Lays out a column/row tree into a fixed viewport, paints it through a {@link RecordingUiRenderer}
 * (asserting the expected fills + text appear), and dispatches a click onto a button (asserting only the
 * hit button's handler runs). This proves the framework end-to-end offline; the loader only adds a thin
 * {@code GuiGraphics} adapter. The CLI {@code ui} command renders the result.
 * RU: Раскладывает дерево column/row в фиксированный вьюпорт, рисует через {@link RecordingUiRenderer} и
 * диспетчеризует клик по кнопке (срабатывает только её обработчик). Доказывает фреймворк end-to-end офлайн.
 */
public final class UiSelfTest {

    private UiSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();
        AtomicInteger okClicks = new AtomicInteger();
        AtomicInteger cancelClicks = new AtomicInteger();

        // A declarative faction-panel-style screen.
        Widget<?> root = Ui.column()
                .padding(8).gap(4)
                .background(UiColor.rgb(0x202024))
                .align(AlignItems.STRETCH)
                .children(
                        Ui.label("Faction: Iron Vanguard").color(UiColor.WHITE),
                        Ui.row().gap(4).children(
                                Ui.button("OK", okClicks::incrementAndGet).grow(1f),
                                Ui.button("Cancel", cancelClicks::incrementAndGet).grow(1f)),
                        Ui.spacer().grow(1f));

        Rect viewport = new Rect(0, 0, 200, 120);
        RecordingUiRenderer renderer = new RecordingUiRenderer();
        LaidOut tree = UiRuntime.render(root, viewport, UiMetrics.DEFAULT, renderer);

        boolean layoutOk = tree.rect().equals(viewport) && tree.children().size() == 3;
        notes.add("laid out root " + tree.rect() + " with " + tree.children().size() + " children");

        // The two buttons share the row's width (grow 1 each); find them in the tree.
        LaidOut okButton = find(tree, w -> w instanceof Button b && b.text().equals("OK"));
        LaidOut cancelButton = find(tree, w -> w instanceof Button b && b.text().equals("Cancel"));
        boolean buttonsLaidOut = okButton != null && cancelButton != null
                && okButton.rect().width() > 0 && cancelButton.rect().width() > 0;
        if (buttonsLaidOut) {
            notes.add("OK button box " + okButton.rect() + ", Cancel box " + cancelButton.rect());
        }

        // Paint produced a background fill for the column + both buttons, and 3 text draws.
        boolean paintOk = renderer.fillCount() >= 3 && renderer.textCount() == 3;
        notes.add("painted " + renderer.fillCount() + " fills, " + renderer.textCount() + " text draws");

        // Click the centre of the OK button → only its handler runs.
        boolean clickOk = false;
        if (okButton != null) {
            Rect r = okButton.rect();
            boolean handled = UiRuntime.click(tree, r.x() + r.width() / 2, r.y() + r.height() / 2);
            UiRuntime.click(tree, viewport.right() + 50, viewport.bottom() + 50); // miss → no-op
            clickOk = handled && okClicks.get() == 1 && cancelClicks.get() == 0;
            notes.add("click dispatch: OK=" + okClicks.get() + " Cancel=" + cancelClicks.get());
        }

        boolean passed = layoutOk && buttonsLaidOut && paintOk && clickOk;
        return new Result(layoutOk, buttonsLaidOut, paintOk, clickOk,
                renderer.fillCount(), renderer.textCount(), notes, passed);
    }

    /** Depth-first search for the first laid-out node whose widget matches {@code predicate}. */
    private static LaidOut find(LaidOut node, java.util.function.Predicate<Widget<?>> predicate) {
        if (predicate.test(node.widget())) {
            return node;
        }
        for (LaidOut child : node.children()) {
            LaidOut hit = find(child, predicate);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** Outcome of the UI self-test, rendered by the CLI {@code ui} command. */
    public record Result(boolean layoutOk, boolean buttonsLaidOut, boolean paintOk, boolean clickOk,
                         int fillCount, int textCount, List<String> notes, boolean passed) {
    }
}
