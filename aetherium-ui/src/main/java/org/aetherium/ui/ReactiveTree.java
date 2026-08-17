/*
 * Aetherium Framework — reactive, memoized UI tree (retained layout).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import org.aetherium.ui.reactive.Computed;
import org.aetherium.ui.reactive.Signal;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A screen's widget tree that rebuilds only when its reactive state changes, and re-lays-out only when the tree
 * or the viewport actually changed — the retained-layout efficiency win.
 *
 * <p>EN: The naive loop rebuilds the tree and runs {@link FlexLayout} every single frame. Here the {@code
 * build} supplier is wrapped in a reactive {@link Computed}, so it re-runs only when a {@link Signal} it read
 * changes (identity of the tree changes then, and only then). {@link #layout} caches the {@link LaidOut} and
 * reuses it while the tree instance, viewport, and metrics are unchanged — so an idle frame does zero rebuild
 * and zero layout work. Best paired with state held in {@link Signal}s; widget-local mutable state (a text
 * field's buffer) still paints live but does not itself trigger a re-layout. Zero-dependency.
 * RU: Наивный цикл каждый кадр пересобирает дерево и запускает {@link FlexLayout}. Здесь {@code build} обёрнут
 * в реактивный {@link Computed} и пересобирается только при изменении прочитанного {@link Signal} (тогда — и
 * только тогда — меняется идентичность дерева). {@link #layout} кэширует {@link LaidOut} и переиспользует его,
 * пока дерево, вьюпорт и метрики неизменны — простаивающий кадр не делает ни пересборки, ни раскладки. Лучше
 * всего со состоянием в {@link Signal}. Без зависимостей.
 */
public final class ReactiveTree {

    private final Computed<Widget<?>> tree;

    private Widget<?> laidWidget;
    private Rect laidViewport;
    private UiMetrics laidMetrics;
    private LaidOut cached;
    private int layoutCount;

    public ReactiveTree(Supplier<Widget<?>> build) {
        Objects.requireNonNull(build, "build");
        this.tree = Computed.of(build::get);
    }

    /** The current widget tree (recomputed reactively; not subscribed — safe to read outside a reaction). */
    public Widget<?> current() {
        return tree.peek();
    }

    /**
     * Lay out the current tree into {@code viewport}, reusing the cached {@link LaidOut} when nothing that
     * affects layout (tree identity, viewport, metrics) has changed since the last call.
     */
    public LaidOut layout(Rect viewport, UiMetrics metrics) {
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(metrics, "metrics");
        Widget<?> currentTree = tree.peek();
        if (cached == null
                || currentTree != laidWidget
                || !viewport.equals(laidViewport)
                || !metrics.equals(laidMetrics)) {
            cached = FlexLayout.layout(currentTree, viewport, metrics);
            laidWidget = currentTree;
            laidViewport = viewport;
            laidMetrics = metrics;
            layoutCount++;
        }
        return cached;
    }

    /** Lay out (cached) and paint through {@code renderer}; returns the laid-out tree. */
    public LaidOut render(Rect viewport, UiMetrics metrics, UiRenderer renderer) {
        LaidOut laid = layout(viewport, metrics);
        UiRuntime.paint(laid, renderer, metrics);
        return laid;
    }

    /** How many times layout has actually run (a reused frame does not increment it) — visibility for tests. */
    public int layoutCount() {
        return layoutCount;
    }

    /** Stop reacting to state changes. */
    public void dispose() {
        tree.dispose();
    }
}
