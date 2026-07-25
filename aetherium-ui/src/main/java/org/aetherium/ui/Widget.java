/*
 * Aetherium Framework — base declarative UI widget (self-typed fluent modifiers).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.List;

/**
 * The base of the declarative widget tree — a node with size, flex-grow, padding, and background.
 *
 * <p>EN: Self-typed ({@code S extends Widget<S>}) so every fluent modifier returns the concrete subtype
 * and chains stay type-safe ({@code Ui.label("x").padding(4).background(c)} is still a {@link Text}). A
 * size of {@link #AUTO} means "use the intrinsic content size"; {@link #grow(float)} makes a child absorb
 * spare main-axis space (Flexbox {@code flex-grow}). The tree is immutable-by-convention once built and
 * is laid out by {@link FlexLayout} — there is no mutable widget state, no retained framework objects.
 * RU: Самотипизированный ({@code S extends Widget<S>}), поэтому каждый fluent-модификатор возвращает
 * конкретный подтип и цепочки типобезопасны. Размер {@link #AUTO} = «использовать собственный размер
 * содержимого»; {@link #grow(float)} заставляет ребёнка поглощать свободное место главной оси.
 */
public abstract class Widget<S extends Widget<S>> {

    /** Sentinel size meaning "compute from content" (Flexbox {@code auto}). */
    public static final int AUTO = -1;

    private int width = AUTO;
    private int height = AUTO;
    private float grow = 0f;
    private float shrink = 1f;
    private boolean minContentSize = false;
    private Insets padding = Insets.ZERO;
    private UiColor background;

    @SuppressWarnings("unchecked")
    protected final S self() {
        return (S) this;
    }

    public S width(int w) {
        this.width = w;
        return self();
    }

    public S height(int h) {
        this.height = h;
        return self();
    }

    public S size(int w, int h) {
        this.width = w;
        this.height = h;
        return self();
    }

    /** Absorb spare main-axis space proportionally to {@code weight} (Flexbox {@code flex-grow}). */
    public S grow(float weight) {
        this.grow = weight;
        return self();
    }

    /**
     * Give up main-axis space when the row/column is over-full, proportionally to {@code weight} (Flexbox
     * {@code flex-shrink}). Default 1 (shrinks); {@code shrink(0)} pins a child at its base size. 
     * without this an over-full row painted straight off-screen.
     */
    public S shrink(float weight) {
        this.shrink = Math.max(0f, weight);
        return self();
    }

    /**
     * When {@code true}, flex-shrink will not shrink this widget below its intrinsic (content) size — the
     * flexbox {@code min-width: auto} behaviour (). Use it on a label so "shrink" pushes the
     * pressure onto controls that can absorb it, instead of silently clipping the text.
     */
    public S minContentSize(boolean floorAtContent) {
        this.minContentSize = floorAtContent;
        return self();
    }

    public S padding(int p) {
        this.padding = Insets.all(p);
        return self();
    }

    public S padding(Insets insets) {
        this.padding = insets;
        return self();
    }

    public S background(UiColor color) {
        this.background = color;
        return self();
    }

    // --- read side (used by the layout/paint engine) ------------------------------------------

    public int widthSpec() {
        return width;
    }

    public int heightSpec() {
        return height;
    }

    public float growWeight() {
        return grow;
    }

    public float shrinkWeight() {
        return shrink;
    }

    public boolean minContentSize() {
        return minContentSize;
    }

    public Insets padding() {
        return padding;
    }

    public UiColor background() {
        return background;
    }

    /** Children of this widget (empty for leaves; overridden by {@link Container}). */
    public List<Widget<?>> children() {
        return List.of();
    }

    /** Intrinsic content width (including this widget's own padding). */
    public abstract int intrinsicWidth(UiMetrics metrics);

    /** Intrinsic content height (including this widget's own padding). */
    public abstract int intrinsicHeight(UiMetrics metrics);

    /**
     * Content height given a known box width (). Defaults to {@link #intrinsicHeight(UiMetrics)};
     * a wrapping {@link Text} overrides it to return the multi-line height once the layout has assigned its
     * width. The layout engine calls this on the column (cross-axis-known) path so wrapped text is measured
     * exactly, in the one place that knows both the box and the metrics.
     */
    public int measuredHeight(UiMetrics metrics, int assignedWidth) {
        return intrinsicHeight(metrics);
    }
}
