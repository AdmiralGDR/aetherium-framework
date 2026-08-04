/*
 * Aetherium Framework — a flex container widget (row/column).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A Flexbox container that lays children along a {@link FlexDirection} with {@code gap}, {@link Justify}
 * (main axis), and {@link AlignItems} (cross axis).
 *
 * <p>EN/RU: контейнер Flexbox — раскладывает детей вдоль главной оси с зазором, выравниванием по главной
 * и поперечной осям.
 */
public final class Container extends Widget<Container> {

    private final FlexDirection direction;
    private final List<Widget<?>> children = new ArrayList<>();
    private int gap;
    private Justify justify = Justify.START;
    private AlignItems align = AlignItems.START;

    public Container(FlexDirection direction) {
        this.direction = direction;
    }

    /** Pixel gap between adjacent children along the main axis. */
    public Container gap(int gap) {
        this.gap = gap;
        return this;
    }

    public Container justify(Justify justify) {
        this.justify = justify;
        return this;
    }

    public Container align(AlignItems align) {
        this.align = align;
        return this;
    }

    /** Append children (declarative tree construction). */
    public Container children(Widget<?>... kids) {
        for (Widget<?> kid : kids) {
            if (kid != null) {
                children.add(kid);
            }
        }
        return this;
    }

    public Container add(Widget<?> child) {
        children.add(child);
        return this;
    }

    public FlexDirection direction() {
        return direction;
    }

    public int gap() {
        return gap;
    }

    public Justify justify() {
        return justify;
    }

    public AlignItems align() {
        return align;
    }

    @Override
    public List<Widget<?>> children() {
        return List.copyOf(children);
    }

    private boolean isRow() {
        return direction == FlexDirection.ROW;
    }

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        int total = isRow() ? mainExtentOfChildren(metrics, true) : maxCrossOfChildren(metrics, true);
        return total + padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        int total = isRow() ? maxCrossOfChildren(metrics, false) : mainExtentOfChildren(metrics, false);
        return total + padding().vertical();
    }

    /** Sum of children's main-axis measured sizes plus the gaps between them. */
    private int mainExtentOfChildren(UiMetrics metrics, boolean horizontalMain) {
        int sum = 0;
        for (Widget<?> c : children) {
            sum += horizontalMain ? measuredWidth(c, metrics) : measuredHeight(c, metrics);
        }
        sum += gap * Math.max(0, children.size() - 1);
        return sum;
    }

    /** Max of children's cross-axis measured sizes. */
    private int maxCrossOfChildren(UiMetrics metrics, boolean horizontalCross) {
        int max = 0;
        for (Widget<?> c : children) {
            max = Math.max(max, horizontalCross ? measuredWidth(c, metrics) : measuredHeight(c, metrics));
        }
        return max;
    }

    /**
     * a child's contribution to its parent's extent must agree with how {@link FlexLayout}
     * <em>places</em> it. FlexLayout honours an explicit {@code widthSpec()}/{@code heightSpec()} when it lays
     * a child out, but measuring here used to consult only the intrinsic size — so a child sized only by
     * {@code height(4)} (whose intrinsic height is 0, e.g. a {@link Spacer}) contributed 0, the parent
     * reserved no room, and the child then overdrew its sibling. Take the larger of the spec and the intrinsic
     * so measuring and placing agree.
     */
    private static int measuredWidth(Widget<?> c, UiMetrics metrics) {
        int intrinsic = c.intrinsicWidth(metrics);
        return c.widthSpec() >= 0 ? Math.max(c.widthSpec(), intrinsic) : intrinsic;
    }

    private static int measuredHeight(Widget<?> c, UiMetrics metrics) {
        int intrinsic = c.intrinsicHeight(metrics);
        return c.heightSpec() >= 0 ? Math.max(c.heightSpec(), intrinsic) : intrinsic;
    }
}
