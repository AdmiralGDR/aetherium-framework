/*
 * Aetherium Framework — a clipping, scrollable container.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.List;

/**
 * A vertically scrollable viewport around a single child — the container the feedback asked for.
 *
 * <p>EN: A variable-length list (restricted-items config, a chat log, …) used to overflow the viewport and
 * paint outside it, because {@link FlexLayout} had no clip and there was no scroll offset. A
 * {@code ScrollPanel} lays its child out at full intrinsic height, offset by {@link #scrollOffset()}, and
 * {@link UiRuntime} clips painting + hit-testing to the panel's box (via {@link UiRenderer#pushClip}). The
 * offset is mutable state the mod owns (like {@link TextField}); {@link UiRuntime#scroll} adjusts it,
 * clamped to the content. Content taller than the view is reachable; content is never drawn outside.
 * RU: Список переменной длины раньше вылезал за вьюпорт, т.к. у {@link FlexLayout} не было отсечения и
 * смещения прокрутки. {@code ScrollPanel} раскладывает ребёнка на полную высоту со смещением
 * {@link #scrollOffset()}, а {@link UiRuntime} отсекает отрисовку и hit-test по рамке панели.
 */
public final class ScrollPanel extends Widget<ScrollPanel> {

    private Widget<?> child;
    // The offset the caller *requested* (survives a rebuild); `scrollOffset` is it clamped to the last layout.
    private int requestedOffset;
    private int scrollOffset;
    private boolean scrollbar;

    // Recorded at layout time so scroll() can clamp without re-running layout / needing metrics.
    private int lastContentHeight;
    private int lastViewHeight;
    private boolean measured;

    public ScrollPanel(Widget<?> child) {
        this.child = child;
    }

    public Widget<?> child() {
        return child;
    }

    /**
     * Replace the child (so a cached panel can take fresh content each frame).  a screen whose
     * {@code build()} runs per frame reuses one panel instead of losing its scroll position to a new one.
     */
    public ScrollPanel child(Widget<?> newChild) {
        this.child = newChild;
        return this;
    }

    /** The clamped scroll offset actually applied at the last layout (use this for painting/scroll math). */
    public int scrollOffset() {
        return scrollOffset;
    }

    /**
     * Request a scroll offset. It is remembered verbatim and clamped to the content extents at the <em>next
     * layout</em> — so restoring a saved offset onto a fresh panel works (), instead of clamping to
     * 0 before layout has measured anything.
     */
    public void setScrollOffset(int offset) {
        this.requestedOffset = Math.max(0, offset);
        this.scrollOffset = clamp(requestedOffset);
    }

    /** Record the content/view extents measured during layout (called by {@link FlexLayout}). */
    void recordExtents(int contentHeight, int viewHeight) {
        this.lastContentHeight = contentHeight;
        this.lastViewHeight = viewHeight;
        this.measured = true;
        // Now that extents are known, apply the (possibly pre-layout) requested offset, clamped.
        this.scrollOffset = clamp(requestedOffset);
    }

    private int clamp(int offset) {
        return Math.max(0, Math.min(offset, maxScroll()));
    }

    /** The largest valid scroll offset (content taller than the view), or 0 if everything fits. */
    public int maxScroll() {
        return Math.max(0, lastContentHeight - lastViewHeight);
    }

    /**
     * Whether this panel has been through a layout pass yet (). {@link #maxScroll()} reflects the
     * <em>previous</em> layout, so a consumer rendering a scroll affordance can use this to tell "nothing to
     * scroll" (measured, maxScroll 0) from "not measured yet" (first frame after opening).
     */
    public boolean hasMeasured() {
        return measured;
    }

    /**
     * Total content height from the last layout pass ( — now public). With {@link #viewHeight()}
     * a mod can show "showing N of M" without recomputing what the framework already measured. Valid only
     * after a layout pass; guard with {@link #hasMeasured()}.
     */
    public int contentHeight() {
        return lastContentHeight;
    }

    /** Visible (viewport) height from the last layout pass ( — now public). See {@link #contentHeight()}. */
    public int viewHeight() {
        return lastViewHeight;
    }

    /** Paint a built-in track + proportional thumb on the right edge (). */
    public ScrollPanel scrollbar(boolean show) {
        this.scrollbar = show;
        return this;
    }

    public boolean scrollbar() {
        return scrollbar;
    }

    @Override
    public List<Widget<?>> children() {
        return List.of(child);
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        // Symmetric with intrinsicHeight (): a scroll panel reports a MINIMUM, not its content's
        // full width, so it doesn't push a row off-screen. Give it width via grow(...) or an explicit width.
        return padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        // A scroll panel does not demand its child's full height — a height spec (or grow) bounds it.
        return metrics.lineHeight() + padding().vertical();
    }

    @Override
    public boolean clipsChildren() {
        return true;
    }

    @Override
    public void paintOverlay(UiRenderer renderer, Rect box, UiMetrics metrics) {
        // Built-in scrollbar: a faint track + proportional thumb on the right edge, painted OUTSIDE the clip.
        if (!scrollbar || maxScroll() <= 0) {
            return;
        }
        final int barW = 3;
        int x = box.right() - barW;
        renderer.fillRect(x, box.y(), barW, box.height(), 0x40FFFFFF); // faint track
        int content = Math.max(1, contentHeight());
        int thumbH = Math.max(8, (int) ((long) box.height() * viewHeight() / content));
        int travel = box.height() - thumbH;
        int thumbY = box.y() + (maxScroll() == 0 ? 0 : (int) ((long) travel * scrollOffset() / maxScroll()));
        renderer.fillRect(x, thumbY, barW, thumbH, 0xC0FFFFFF); // thumb
    }
}
