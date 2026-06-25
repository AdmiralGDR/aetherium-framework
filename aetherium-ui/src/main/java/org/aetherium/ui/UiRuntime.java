/*
 * Aetherium Framework — UI runtime: layout + paint + hit-testing.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * Ties the declarative tree to a platform: lay out a screen, paint it through a {@link UiRenderer}, and
 * route a click to the right {@link Button}.
 *
 * <p>EN: This is the whole loader integration surface. The loader's real {@code Screen} calls
 * {@link #render} every frame and {@link #click} on mouse-down — nothing else. Painting walks the
 * {@link LaidOut} tree drawing backgrounds then text/buttons; hit-testing walks it back-to-front so the
 * top-most button wins. Pure logic, fully exercised offline by the self-test.
 * RU: Это вся поверхность интеграции с загрузчиком. Реальный {@code Screen} загрузчика вызывает
 * {@link #render} каждый кадр и {@link #click} по нажатию мыши — и только. Отрисовка обходит дерево
 * {@link LaidOut}, рисуя фоны, затем текст/кнопки; hit-test идёт сзади наперёд.
 */
public final class UiRuntime {

    private UiRuntime() {
    }

    /** Lay out {@code root} into {@code viewport} and paint it through {@code renderer}. */
    public static LaidOut render(Widget<?> root, Rect viewport, UiMetrics metrics, UiRenderer renderer) {
        LaidOut tree = FlexLayout.layout(root, viewport, metrics);
        paint(tree, renderer, metrics);
        return tree;
    }

    /** Paint an already-laid-out tree. */
    public static void paint(LaidOut node, UiRenderer renderer, UiMetrics metrics) {
        Widget<?> w = node.widget();
        Rect box = node.rect();

        UiColor bg = w.background();
        if (bg != null && !bg.isTransparent()) {
            renderer.fillRect(box.x(), box.y(), box.width(), box.height(), bg.argb());
        }
        if (w instanceof Text t) {
            Rect c = box.shrink(w.padding());
            renderer.drawText(c.x(), c.y(), t.text(), t.color().argb());
        } else if (w instanceof Button b) {
            Rect c = box.shrink(w.padding());
            int tw = metrics.textWidth(b.text());
            int tx = c.x() + Math.max(0, (c.width() - tw) / 2);
            int ty = c.y() + Math.max(0, (c.height() - metrics.lineHeight()) / 2);
            renderer.drawText(tx, ty, b.text(), b.color().argb());
        }
        for (LaidOut child : node.children()) {
            paint(child, renderer, metrics);
        }
    }

    /**
     * Dispatch a click at {@code (x, y)} to the top-most {@link Button} under the cursor.
     *
     * @return {@code true} if a button handled the click
     */
    public static boolean click(LaidOut node, int x, int y) {
        // Children paint on top of parents, so test them (last-drawn first) before the node itself.
        var kids = node.children();
        for (int i = kids.size() - 1; i >= 0; i--) {
            if (click(kids.get(i), x, y)) {
                return true;
            }
        }
        if (node.widget() instanceof Button b && b.onClick() != null && node.rect().contains(x, y)) {
            b.onClick().run();
            return true;
        }
        return false;
    }
}
