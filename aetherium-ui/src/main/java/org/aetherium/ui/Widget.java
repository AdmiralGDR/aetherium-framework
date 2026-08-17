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

    // --- paint SPI ------------------------------------------------------------------------------

    /**
     * Draw this widget's own content into its laid-out {@code box} (padding included) through {@code renderer}.
     *
     * <p>EN: The runtime fills the background before this and recurses into children after, so a leaf only
     * overrides this to draw text / a bar / an icon. Default: nothing (a plain box is just its background).
     * This is the extension seam that replaced the central {@code instanceof} chain — a new widget renders
     * without editing {@link UiRuntime}.
     * RU: Рантайм заливает фон до этого и обходит детей после, поэтому лист лишь переопределяет метод для
     * своей отрисовки (текст/полоса/иконка). По умолчанию — ничего. Это и есть точка расширения, заменившая
     * центральную цепочку {@code instanceof}: новый виджет рисует без правки {@link UiRuntime}.
     */
    public void paintContent(UiRenderer renderer, Rect box, UiMetrics metrics) {
        // default: no own content
    }

    /** Whether this widget clips its children to its own box (e.g. a scroll panel). Default {@code false}. */
    public boolean clipsChildren() {
        return false;
    }

    /**
     * Draw an overlay after this widget's children, outside any clip (e.g. a scrollbar). Default: nothing.
     */
    public void paintOverlay(UiRenderer renderer, Rect box, UiMetrics metrics) {
        // default: no overlay
    }

    // --- input SPI ------------------------------------------------------------------------------

    /**
     * Whether this widget is a click target during hit-testing.
     *
     * <p>EN: The input counterpart of the paint SPI — {@link UiRuntime} asks each widget instead of running a
     * central {@code instanceof} chain, so a new interactive widget joins hit-testing without editing the
     * runtime. A button is interactive only when it has a handler; a plain box never is. Default {@code false}.
     * RU: Входной аналог paint SPI — {@link UiRuntime} спрашивает сам виджет вместо центральной цепочки
     * {@code instanceof}, поэтому новый интерактивный виджет участвует в hit-test без правки рантайма.
     */
    public boolean interactive() {
        return false;
    }

    /** Whether a click on this widget should focus it (e.g. a text field). Default {@code false}. */
    public boolean focusable() {
        return false;
    }

    /** Give this widget keyboard focus (a focusable widget overrides). Default: nothing. */
    public void requestFocus() {
        // default: not focusable
    }

    /** Clear this widget's focus (a focusable widget overrides). Called on every widget before a new focus. */
    public void blur() {
        // default: nothing to clear
    }

    /**
     * Handle a click at widget-local {@code (localX, localY)} within a box of {@code width}×{@code height};
     * return {@code true} if it changed state or ran an action. Default: nothing handled. A button runs its
     * action; a toggle flips; a slider maps {@code localX} to its value.
     */
    public boolean handleClick(int localX, int localY, int width, int height) {
        return false;
    }

    // --- accessibility --------------------------------------------------------------------------

    private Role explicitRole;
    private String label;

    /** Override the semantic {@link Role} (defaults to {@link #defaultRole()}). */
    public S role(Role role) {
        this.explicitRole = role;
        return self();
    }

    /** Set the accessible name assistive tech / controller navigation announces for this widget. */
    public S label(String label) {
        this.label = label;
        return self();
    }

    /** The effective role: an explicit {@link #role(Role)} override, else {@link #defaultRole()}. */
    public Role role() {
        return explicitRole != null ? explicitRole : defaultRole();
    }

    /** A subclass's inherent role (e.g. a button is {@link Role#BUTTON}); default {@link Role#NONE}. */
    protected Role defaultRole() {
        return Role.NONE;
    }

    /** The explicit label set via {@link #label(String)}, or {@code null}. */
    public String label() {
        return label;
    }

    /**
     * The accessible name to announce: the explicit {@link #label(String)} by default. A widget with inherent
     * text (a button, a label, a field's placeholder) overrides this to fall back to that text, so it is named
     * without a redundant {@code label(...)}. {@code null}/blank means "no accessible name" — the a11y audit
     * ({@link UiRuntime#auditAccessibility}) flags that on an interactive widget.
     */
    public String accessibleName() {
        return label;
    }
}
