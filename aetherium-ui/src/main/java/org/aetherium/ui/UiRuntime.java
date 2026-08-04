/*
 * Aetherium Framework — UI runtime: layout + paint + hit-testing + input.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * Ties the declarative tree to a platform: lay out a screen, paint it through a {@link UiRenderer}, route a
 * click, and — new in — route <strong>keyboard input</strong> and <strong>scrolling</strong>.
 *
 * <p>EN: This is the whole loader integration surface. The loader's real {@code Screen} calls {@link #render}
 * every frame, {@link #click} on mouse-down, {@link #keyPressed}/{@link #charTyped} on key events, and
 * {@link #scroll} on the mouse wheel. Painting walks the {@link LaidOut} tree; a {@link ScrollPanel} clips
 * its subtree through {@link UiRenderer#pushClip}. Hit-testing walks back-to-front so the top-most widget
 * wins and respects the same clip. Clicking a {@link TextField} focuses it; typed characters then reach it —
 * closing the "a keystroke physically cannot reach a mod" gap. Pure logic, fully exercised offline.
 * RU: Вся поверхность интеграции с загрузчиком. Реальный {@code Screen} вызывает {@link #render} каждый
 * кадр, {@link #click} по нажатию мыши, {@link #keyPressed}/{@link #charTyped} по клавишам и {@link #scroll}
 * по колесу. Отрисовка обходит дерево {@link LaidOut}; {@link ScrollPanel} отсекает поддерево через
 * {@link UiRenderer#pushClip}. Клик по {@link TextField} фокусирует его — символы доходят до мода.
 */
public final class UiRuntime {

    /** Platform key codes (GLFW) the loader forwards; exposed so a mod's {@code onKey} can compare. */
    public static final int KEY_BACKSPACE = 259;
    public static final int KEY_ENTER = 257;
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_PAGE_UP = 266;
    public static final int KEY_PAGE_DOWN = 267;

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
            // a wrapped label flows onto multiple lines that each fit the box width; a plain
            // label is one line. The same wrapLines() the layout used sizes the box, so drawing agrees.
            java.util.List<String> lines = t.wrap()
                    ? t.wrapLines(c.width(), metrics)
                    : java.util.List.of(t.text());
            int lineY = c.y();
            for (String line : lines) {
                int tw = metrics.textWidth(line);
                int tx = switch (t.align()) {
                    case CENTER -> c.x() + Math.max(0, (c.width() - tw) / 2);
                    case END -> c.x() + Math.max(0, c.width() - tw);
                    default -> c.x();
                };
                renderer.drawText(tx, lineY, line, t.color().argb());
                lineY += metrics.lineHeight();
            }
        } else if (w instanceof Button b) {
            Rect c = box.shrink(w.padding());
            int tw = metrics.textWidth(b.text());
            int tx = c.x() + Math.max(0, (c.width() - tw) / 2);
            int ty = c.y() + Math.max(0, (c.height() - metrics.lineHeight()) / 2);
            renderer.drawText(tx, ty, b.text(), b.color().argb());
        } else if (w instanceof TextField tf) {
            Rect c = box.shrink(w.padding());
            boolean empty = tf.text().isEmpty();
            String shown = (empty ? tf.placeholderText() : tf.text()) + (tf.focused() ? "_" : "");
            int color = empty && !tf.focused() ? UiColor.rgb(0x808080).argb() : tf.textColor().argb();
            renderer.drawText(c.x(), c.y(), shown, color);
        }

        // A scroll panel clips its subtree to its own box so overflowing content is not painted outside it.
        boolean clip = w instanceof ScrollPanel;
        if (clip) {
            renderer.pushClip(box.x(), box.y(), box.width(), box.height());
        }
        for (LaidOut child : node.children()) {
            paint(child, renderer, metrics);
        }
        if (clip) {
            renderer.popClip();
            // Built-in scrollbar (): a faint track + proportional thumb on the right edge, painted
            // OUTSIDE the clip so it's always visible. Two fillRects — no new SPI.
            if (w instanceof ScrollPanel sp && sp.scrollbar() && sp.maxScroll() > 0) {
                paintScrollbar(sp, box, renderer);
            }
        }
    }

    private static void paintScrollbar(ScrollPanel sp, Rect box, UiRenderer renderer) {
        final int barW = 3;
        int x = box.right() - barW;
        renderer.fillRect(x, box.y(), barW, box.height(), 0x40FFFFFF); // faint track
        int content = Math.max(1, sp.contentHeight());
        int thumbH = Math.max(8, (int) ((long) box.height() * sp.viewHeight() / content));
        int travel = box.height() - thumbH;
        int thumbY = box.y() + (sp.maxScroll() == 0 ? 0 : (int) ((long) travel * sp.scrollOffset() / sp.maxScroll()));
        renderer.fillRect(x, thumbY, barW, thumbH, 0xC0FFFFFF); // thumb
    }

    /**
     * Dispatch a click at {@code (x, y)} to the top-most interactive widget under the cursor. A
     * {@link Button} runs its handler; a {@link TextField} gains focus (all other fields blur). Respects
     * {@link ScrollPanel} clipping — a click outside a panel never reaches its overflowing children.
     *
     * @return {@code true} if a widget handled the click
     */
    public static boolean click(LaidOut root, int x, int y) {
        LaidOut hit = hitTest(root, x, y);
        if (hit == null) {
            return false;
        }
        Widget<?> w = hit.widget();
        if (w instanceof Button b && b.onClick() != null) {
            blurAll(root);
            b.onClick().run();
            return true;
        }
        if (w instanceof TextField tf) {
            blurAll(root);
            tf.setFocused(true);
            return true;
        }
        return false;
    }

    /**
     * Route a key press to the focused {@link TextField} (backspace), after giving no widget special keys.
     * Returns {@code true} if the key was consumed.
     */
    public static boolean keyPressed(LaidOut root, int keyCode, int modifiers) {
        // Page keys scroll the first scroll panel by a page (), even with no text field focused.
        if (keyCode == KEY_PAGE_UP || keyCode == KEY_PAGE_DOWN) {
            ScrollPanel panel = firstScrollPanel(root);
            if (panel != null && panel.maxScroll() > 0) {
                int page = Math.max(1, panel.viewHeight());
                panel.setScrollOffset(panel.scrollOffset() + (keyCode == KEY_PAGE_DOWN ? page : -page));
                return true;
            }
        }
        TextField focused = focusedField(root);
        if (focused == null) {
            return false;
        }
        if (keyCode == KEY_BACKSPACE) {
            focused.backspace();
            return true;
        }
        // Enter/Escape are left for the screen's onKey to handle (e.g. submit/close).
        return false;
    }

    /** Route a typed character to the focused {@link TextField}. Returns {@code true} if consumed. */
    public static boolean charTyped(LaidOut root, char c) {
        TextField focused = focusedField(root);
        if (focused == null) {
            return false;
        }
        focused.type(c);
        return true;
    }

    /**
     * Scroll the top-most {@link ScrollPanel} under {@code (x, y)} by {@code delta} rows (positive = down).
     * Returns {@code true} if a panel consumed it.
     */
    public static boolean scroll(LaidOut root, int x, int y, int delta) {
        ScrollPanel panel = scrollPanelAt(root, x, y);
        if (panel == null) {
            return false;
        }
        panel.setScrollOffset(panel.scrollOffset() + delta);
        return true;
    }

    /**
     * Walk a laid-out tree and report every child whose box escapes its parent's — the layout safety net
     * asked for. A {@link ScrollPanel}'s child is intentionally larger/offset (it is clipped), so
     * overflow under a scroll panel is expected and not reported.
     *
     * @return one human-readable line per violation (empty if the layout is clean)
     */
    public static java.util.List<String> audit(LaidOut root) {
        java.util.List<String> violations = new java.util.ArrayList<>();
        auditNode(root, violations);
        return violations;
    }

    /**
     * Audit + also report any {@link Text}/{@link Button} whose label is wider than its own box — i.e. text
     * that a player sees clipped (). Box containment alone passes a screen whose every label is cut
     * off; this catches the half the player actually reads. Needs {@link UiMetrics} to measure the text.
     */
    public static java.util.List<String> audit(LaidOut root, UiMetrics metrics) {
        java.util.List<String> violations = audit(root);
        auditTextFit(root, metrics, violations);
        return violations;
    }

    private static void auditTextFit(LaidOut node, UiMetrics metrics, java.util.List<String> out) {
        Widget<?> w = node.widget();
        // a wrapping label is not "clipped" when it is wider than its box — it flows onto more
        // lines — so it is never flagged here (it must instead fit its measured HEIGHT, checked by auditNode's
        // containment). Only non-wrapping single-line labels can overrun their inner width.
        boolean wraps = w instanceof Text t && t.wrap();
        String text = wraps ? null
                : (w instanceof Text t ? t.text() : (w instanceof Button b ? b.text() : null));
        if (text != null && !text.isEmpty()) {
            Rect inner = node.rect().shrink(w.padding());
            int tw = metrics.textWidth(text);
            if (tw > inner.width()) {
                out.add(w.getClass().getSimpleName() + "('" + text + "') text width " + tw
                        + " > inner width " + inner.width() + " (clipped)");
            }
        }
        for (LaidOut child : node.children()) {
            auditTextFit(child, metrics, out);
        }
    }

    private static void auditNode(LaidOut node, java.util.List<String> out) {
        boolean clips = node.widget() instanceof ScrollPanel;
        Rect p = node.rect();
        for (LaidOut child : node.children()) {
            Rect c = child.rect();
            if (!clips && (c.x() < p.x() || c.y() < p.y() || c.right() > p.right() || c.bottom() > p.bottom())) {
                out.add(node.widget().getClass().getSimpleName() + " " + p + " does not contain "
                        + child.widget().getClass().getSimpleName() + " " + c);
            }
            auditNode(child, out);
        }
        auditSiblingOverlap(node, out);
    }

    /**
     * within one container, laid-out sibling rectangles must not intersect. A flex container tiles
     * its children along the main axis, so any two siblings sharing pixels is a layout defect — exactly the
     * bug (a size-specced bar measured as 0, so its parent reserved no row and it painted across a label).
     * Containment alone passed that layout because the bar was inside the card; this catches the overlap the
     * player actually sees. O(n²) over one container's direct children (n is tiny), pure geometry — no metrics
     * needed. A {@link ScrollPanel} has a single child (no siblings) and intentionally offsets it, so it is not
     * considered here.
     */
    private static void auditSiblingOverlap(LaidOut node, java.util.List<String> out) {
        if (node.widget() instanceof ScrollPanel) {
            return;
        }
        java.util.List<LaidOut> kids = node.children();
        for (int i = 0; i < kids.size(); i++) {
            Rect a = kids.get(i).rect();
            if (a.width() <= 0 || a.height() <= 0) {
                continue; // a zero-area child cannot visibly overlap anything
            }
            for (int j = i + 1; j < kids.size(); j++) {
                Rect b = kids.get(j).rect();
                if (b.width() <= 0 || b.height() <= 0) {
                    continue;
                }
                boolean overlap = a.x() < b.right() && b.x() < a.right()
                        && a.y() < b.bottom() && b.y() < a.bottom();
                if (overlap) {
                    out.add(node.widget().getClass().getSimpleName() + " has overlapping siblings: "
                            + kids.get(i).widget().getClass().getSimpleName() + " " + a + " intersects "
                            + kids.get(j).widget().getClass().getSimpleName() + " " + b);
                }
            }
        }
    }

    // --- internals ------------------------------------------------------------------------------

    /** Topmost interactive ({@link Button}/{@link TextField}) node under the point, respecting clips. */
    private static LaidOut hitTest(LaidOut node, int x, int y) {
        // A scroll panel clips its children: don't descend if the point is outside the panel box.
        if (!(node.widget() instanceof ScrollPanel) || node.rect().contains(x, y)) {
            var kids = node.children();
            for (int i = kids.size() - 1; i >= 0; i--) {
                LaidOut hit = hitTest(kids.get(i), x, y);
                if (hit != null) {
                    return hit;
                }
            }
        }
        Widget<?> w = node.widget();
        boolean interactive = (w instanceof Button b && b.onClick() != null) || w instanceof TextField;
        return interactive && node.rect().contains(x, y) ? node : null;
    }

    /** Clear focus on every {@link TextField} in the tree. */
    private static void blurAll(LaidOut node) {
        if (node.widget() instanceof TextField tf) {
            tf.setFocused(false);
        }
        for (LaidOut child : node.children()) {
            blurAll(child);
        }
    }

    /** The currently focused {@link TextField}, or {@code null}. */
    private static TextField focusedField(LaidOut node) {
        if (node.widget() instanceof TextField tf && tf.focused()) {
            return tf;
        }
        for (LaidOut child : node.children()) {
            TextField hit = focusedField(child);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** The first {@link ScrollPanel} in the tree (used for page-key scrolling). */
    private static ScrollPanel firstScrollPanel(LaidOut node) {
        if (node.widget() instanceof ScrollPanel p) {
            return p;
        }
        for (LaidOut child : node.children()) {
            ScrollPanel hit = firstScrollPanel(child);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** Topmost {@link ScrollPanel} whose box contains the point. */
    private static ScrollPanel scrollPanelAt(LaidOut node, int x, int y) {
        for (int i = node.children().size() - 1; i >= 0; i--) {
            ScrollPanel hit = scrollPanelAt(node.children().get(i), x, y);
            if (hit != null) {
                return hit;
            }
        }
        return node.widget() instanceof ScrollPanel p && node.rect().contains(x, y) ? p : null;
    }
}
