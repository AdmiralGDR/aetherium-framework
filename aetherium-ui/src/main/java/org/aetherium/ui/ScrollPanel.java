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

    private final Widget<?> child;
    private int scrollOffset;

    // Recorded at layout time so scroll() can clamp without re-running layout / needing metrics.
    private int lastContentHeight;
    private int lastViewHeight;

    public ScrollPanel(Widget<?> child) {
        this.child = child;
    }

    public Widget<?> child() {
        return child;
    }

    public int scrollOffset() {
        return scrollOffset;
    }

    /** Set the scroll offset, clamped to the last laid-out content extent. */
    public void setScrollOffset(int offset) {
        int max = Math.max(0, lastContentHeight - lastViewHeight);
        this.scrollOffset = Math.max(0, Math.min(offset, max));
    }

    /** Record the content/view extents measured during layout (called by {@link FlexLayout}). */
    void recordExtents(int contentHeight, int viewHeight) {
        this.lastContentHeight = contentHeight;
        this.lastViewHeight = viewHeight;
        // Re-clamp in case the content shrank below the current offset.
        setScrollOffset(scrollOffset);
    }

    int maxScroll() {
        return Math.max(0, lastContentHeight - lastViewHeight);
    }

    @Override
    public List<Widget<?>> children() {
        return List.of(child);
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        return child.intrinsicWidth(metrics) + padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        // A scroll panel does not demand its child's full height — a height spec (or grow) bounds it.
        return metrics.lineHeight() + padding().vertical();
    }
}
