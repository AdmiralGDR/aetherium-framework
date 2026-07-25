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

        // 5) Keyboard input channel: focus a text field by clicking it, then type + backspace.
        TextField field = Ui.textField().placeholder("name…");
        Widget<?> formRoot = Ui.column().padding(6).gap(4).align(AlignItems.STRETCH).children(
                Ui.label("Rename faction:"),
                field.height(12));
        LaidOut form = FlexLayout.layout(formRoot, new Rect(0, 0, 160, 60), UiMetrics.DEFAULT);
        // A keystroke before focus must not reach the field (nothing focused).
        boolean preFocus = !UiRuntime.charTyped(form, 'X') && field.text().isEmpty();
        LaidOut fieldBox = find(form, w -> w instanceof TextField);
        UiRuntime.click(form, fieldBox.rect().x() + 2, fieldBox.rect().y() + 2);
        boolean focused = field.focused();
        UiRuntime.charTyped(form, 'H');
        UiRuntime.charTyped(form, 'i');
        boolean typed = field.text().equals("Hi");
        UiRuntime.keyPressed(form, UiRuntime.KEY_BACKSPACE, 0);
        boolean afterBackspace = field.text().equals("H");
        boolean textInputOk = preFocus && focused && typed && afterBackspace;
        notes.add("keyboard: preFocusBlocked=" + preFocus + ", focused=" + focused
                + ", typed='" + field.text() + "'");

        // 6) Scrolling + clip: a tall list inside a short scroll panel overflows, is clipped, and scrolls.
        Container list = Ui.column().gap(2);
        for (int i = 0; i < 20; i++) {
            list.add(Ui.label("restricted item " + i));
        }
        ScrollPanel panel = Ui.scroll(list);
        Widget<?> scrollRoot = Ui.column().padding(4).align(AlignItems.STRETCH).children(panel.height(40));
        RecordingUiRenderer scrollRenderer = new RecordingUiRenderer();
        LaidOut scrollTree = UiRuntime.render(scrollRoot, new Rect(0, 0, 150, 60), UiMetrics.DEFAULT, scrollRenderer);
        LaidOut panelBox = find(scrollTree, w -> w instanceof ScrollPanel);
        LaidOut listBox = panelBox.children().get(0);
        boolean overflow = listBox.rect().height() > panelBox.rect().height(); // content taller than view
        boolean clipped = scrollRenderer.clipCount() >= 1;                      // clip was pushed
        int topBefore = listBox.rect().y();
        UiRuntime.scroll(scrollTree, panelBox.rect().x() + 2, panelBox.rect().y() + 2, 15);
        // Re-layout to observe the new offset (a real screen re-renders each frame).
        LaidOut scrolled = FlexLayout.layout(scrollRoot, new Rect(0, 0, 150, 60), UiMetrics.DEFAULT);
        int topAfter = find(scrolled, w -> w instanceof ScrollPanel).children().get(0).rect().y();
        boolean scrolledUp = topAfter < topBefore && panel.scrollOffset() > 0;
        boolean scrollOk = overflow && clipped && scrolledUp;
        notes.add("scroll: overflow=" + overflow + ", clips=" + scrollRenderer.clipCount()
                + ", offset=" + panel.scrollOffset() + " (list top " + topBefore + "→" + topAfter + ")");

        // 7) Flex-shrink (): a 4-button action bar that overflows must shrink to fit + audit clean at 320px.
        Widget<?> bar = Ui.row().gap(4).align(AlignItems.STRETCH).children(
                Ui.button("Deposit Essence", () -> { }),
                Ui.button("Withdraw Essence", () -> { }),
                Ui.button("Claim Territory", () -> { }),
                Ui.button("Disband Faction", () -> { }));
        LaidOut narrow = UiRuntime.render(bar, new Rect(0, 0, 320, 40), UiMetrics.DEFAULT, new RecordingUiRenderer());
        java.util.List<String> auditClean = UiRuntime.audit(narrow);
        boolean shrinkOk = auditClean.isEmpty();
        notes.add("flex-shrink: 4-button bar into 320px → audit violations=" + auditClean.size());

        // 7b) Audit must CATCH a deliberately non-shrinkable overflow (shrink(0) each).
        Widget<?> rigid = Ui.row().gap(4).children(
                Ui.button("Deposit Essence", () -> { }).shrink(0),
                Ui.button("Withdraw Essence", () -> { }).shrink(0),
                Ui.button("Claim Territory", () -> { }).shrink(0),
                Ui.button("Disband Faction", () -> { }).shrink(0));
        LaidOut overfull = FlexLayout.layout(rigid, new Rect(0, 0, 320, 40), UiMetrics.DEFAULT);
        boolean auditCatches = !UiRuntime.audit(overfull).isEmpty();
        notes.add("audit catches rigid overflow=" + auditCatches);

        // 8) Scroll position survives a rebuild (): a saved offset restores onto a FRESH panel.
        Container list2 = Ui.column().gap(2);
        for (int i = 0; i < 20; i++) {
            list2.add(Ui.label("row " + i));
        }
        ScrollPanel fresh = Ui.scroll(list2);
        fresh.setScrollOffset(30);                    // requested BEFORE any layout has measured extents
        Widget<?> freshRoot = Ui.column().padding(4).align(AlignItems.STRETCH).children(fresh.height(40));
        FlexLayout.layout(freshRoot, new Rect(0, 0, 150, 60), UiMetrics.DEFAULT);
        boolean scrollRestoreOk = fresh.scrollOffset() > 0 && fresh.maxScroll() > 0;
        notes.add("scroll restore on fresh panel: offset=" + fresh.scrollOffset() + " (max " + fresh.maxScroll() + ")");

        // 9) items: text-fit audit, Text.align, min-content-size, hasMeasured, scrollbar paint.
        // (a) audit(root, metrics) must catch a label wider than its clamped box ().
        Text longLabel = Ui.label("examplemod:samurai_spirit_katana");
        Widget<?> tight = Ui.row().children(longLabel.width(60));
        LaidOut tightTree = FlexLayout.layout(tight, new Rect(0, 0, 60, 12), UiMetrics.DEFAULT);
        boolean textFitAudit = UiRuntime.audit(tightTree).isEmpty()                     // box-containment passes
                && !UiRuntime.audit(tightTree, UiMetrics.DEFAULT).isEmpty();            // text-fit catches it
        // (b) Text.align(CENTER) shifts the draw x right of the left edge ().
        RecordingUiRenderer alignR = new RecordingUiRenderer();
        UiRuntime.render(Ui.label("hi").align(Justify.CENTER).width(100), new Rect(0, 0, 100, 12), UiMetrics.DEFAULT, alignR);
        boolean textAlign = alignR.commands().stream().anyMatch(c -> c.kind().equals("text") && c.x() > 0);
        // (c) minContentSize floors a label at its intrinsic width under shrink ().
        Text pinned = Ui.label("Кибер-Коммуна").minContentSize(true);
        int intrinsic = pinned.intrinsicWidth(UiMetrics.DEFAULT);
        Widget<?> crowded = Ui.row().gap(4).children(pinned, Ui.button("X", () -> { }).grow(1f).shrink(1f));
        LaidOut crowdedTree = FlexLayout.layout(crowded, new Rect(0, 0, 40, 20), UiMetrics.DEFAULT);
        LaidOut pinnedBox = find(crowdedTree, w -> w instanceof Text t && t.text().startsWith("Кибер"));
        boolean minContent = pinnedBox != null && pinnedBox.rect().width() >= intrinsic;
        // (d) hasMeasured + scrollbar paint (/).
        ScrollPanel sbar = Ui.scroll(makeList(20)).scrollbar(true);
        boolean beforeMeasure = !sbar.hasMeasured();
        RecordingUiRenderer barR = new RecordingUiRenderer();
        UiRuntime.render(Ui.column().align(AlignItems.STRETCH).children(sbar.height(40)), new Rect(0, 0, 120, 60), UiMetrics.DEFAULT, barR);
        boolean scrollbarPaint = sbar.hasMeasured() && beforeMeasure && barR.fillCount() >= 2; // track + thumb
        boolean roundThreeUiOk = textFitAudit && textAlign && minContent && scrollbarPaint;
        notes.add("UI: text-fit-audit=" + textFitAudit + ", text-align=" + textAlign
                + ", min-content=" + minContent + ", scrollbar+measured=" + scrollbarPaint);

        // 10) a wrapping label flows onto multiple lines instead of clipping horizontally.
        Text para = Ui.label("Defeat enemies of the hostile faction any damage resets the counter").wrap(true);
        Widget<?> paraCol = Ui.column().align(AlignItems.STRETCH).children(para);
        Rect paraViewport = new Rect(0, 0, 120, 80);
        LaidOut paraTree = FlexLayout.layout(paraCol, paraViewport, UiMetrics.DEFAULT);
        LaidOut paraBox = find(paraTree, w -> w instanceof Text t && t.wrap());
        boolean wrappedTaller = paraBox != null && paraBox.rect().height() > UiMetrics.DEFAULT.lineHeight();
        boolean wrapAuditClean = UiRuntime.audit(paraTree, UiMetrics.DEFAULT).isEmpty();
        RecordingUiRenderer wrapR = new RecordingUiRenderer();
        UiRuntime.render(paraCol, paraViewport, UiMetrics.DEFAULT, wrapR);
        long wrapTextDraws = wrapR.commands().stream().filter(cmd -> cmd.kind().equals("text")).count();
        boolean roundFourUiOk = wrappedTaller && wrapAuditClean && wrapTextDraws >= 2;
        notes.add("UI: wrapped-taller=" + wrappedTaller + ", wrap-audit-clean=" + wrapAuditClean
                + ", wrap-text-draws=" + wrapTextDraws);

        boolean passed = layoutOk && buttonsLaidOut && paintOk && clickOk && textInputOk && scrollOk
                && shrinkOk && auditCatches && scrollRestoreOk && roundThreeUiOk && roundFourUiOk;
        return new Result(layoutOk, buttonsLaidOut, paintOk, clickOk, textInputOk, scrollOk,
                shrinkOk, auditCatches, scrollRestoreOk, roundThreeUiOk,
                renderer.fillCount(), renderer.textCount(), notes, passed);
    }

    private static Container makeList(int rows) {
        Container list = Ui.column().gap(2);
        for (int i = 0; i < rows; i++) {
            list.add(Ui.label("row " + i));
        }
        return list;
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
                         boolean textInputOk, boolean scrollOk,
                         boolean shrinkOk, boolean auditCatches, boolean scrollRestoreOk,
                         boolean roundThreeUiOk,
                         int fillCount, int textCount, List<String> notes, boolean passed) {
    }
}
