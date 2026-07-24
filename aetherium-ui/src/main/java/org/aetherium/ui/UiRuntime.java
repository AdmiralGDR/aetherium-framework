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
        }
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
