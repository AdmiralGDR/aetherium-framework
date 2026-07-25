/*
 * Aetherium Framework — the Flexbox layout engine.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes absolute pixel boxes for a declarative widget tree — a single-pass Flexbox solver.
 *
 * <p>EN: For each {@link Container} it sizes children on the main axis (explicit size, else intrinsic),
 * distributes spare space to {@code grow} children, applies {@link Justify} when nothing grows, and sizes
 * + positions on the cross axis per {@link AlignItems} (including {@code STRETCH}). The result is a
 * {@link LaidOut} tree of absolute {@link Rect}s. Pure integer math — deterministic and offline-testable.
 * RU: Для каждого {@link Container} вычисляет размеры детей по главной оси (явный или собственный),
 * распределяет свободное место grow-детям, применяет {@link Justify} (если нет grow) и размещает по
 * поперечной оси согласно {@link AlignItems} (включая {@code STRETCH}). Результат — дерево {@link LaidOut}.
 */
public final class FlexLayout {

    private FlexLayout() {
    }

    /** Lay out {@code root} into {@code viewport} using {@code metrics}. */
    public static LaidOut layout(Widget<?> root, Rect viewport, UiMetrics metrics) {
        if (root instanceof Container container) {
            return layoutContainer(container, viewport, metrics);
        }
        if (root instanceof ScrollPanel panel) {
            return layoutScrollPanel(panel, viewport, metrics);
        }
        return new LaidOut(root, viewport, List.of());
    }

    /**
     * Lay out a {@link ScrollPanel}: the panel keeps its (bounded) box; its single child is laid out at full
     * intrinsic height and shifted up by the scroll offset, so content taller than the view overflows the
     * box (to be clipped at paint by {@link UiRuntime}).
     */
    private static LaidOut layoutScrollPanel(ScrollPanel panel, Rect box, UiMetrics metrics) {
        Rect content = box.shrink(panel.padding());
        Widget<?> child = panel.child();
        int childHeight = child.heightSpec() >= 0 ? child.heightSpec() : child.intrinsicHeight(metrics);
        // Record extents first so the panel clamps its offset to the freshly measured content.
        panel.recordExtents(childHeight, content.height());
        int childY = content.y() - panel.scrollOffset();
        Rect childBox = new Rect(content.x(), childY, content.width(), childHeight);
        return new LaidOut(panel, box, List.of(layout(child, childBox, metrics)));
    }

    private static LaidOut layoutContainer(Container c, Rect box, UiMetrics metrics) {
        Rect content = box.shrink(c.padding());
        boolean row = c.direction() == FlexDirection.ROW;
        int mainSize = row ? content.width() : content.height();
        int crossSize = row ? content.height() : content.width();
        List<Widget<?>> kids = c.children();
        int n = kids.size();
        if (n == 0) {
            return new LaidOut(c, box, List.of());
        }

        // 1) base main-axis sizes (explicit spec, else intrinsic) + grow weights.
        int[] mainLen = new int[n];
        float totalGrow = 0f;
        int sumBase = 0;
        for (int i = 0; i < n; i++) {
            Widget<?> k = kids.get(i);
            int explicit = row ? k.widthSpec() : k.heightSpec();
            mainLen[i] = explicit >= 0 ? explicit
                    : (row ? k.intrinsicWidth(metrics) : k.intrinsicHeight(metrics));
            totalGrow += k.growWeight();
            sumBase += mainLen[i];
        }
        int totalGap = c.gap() * (n - 1);
        int free = mainSize - sumBase - totalGap;

        // 2) distribute spare space to grow children, OR shrink over-full children (Flexbox flex-shrink).
        if (free > 0 && totalGrow > 0f) {
            int remaining = free;
            int lastGrower = -1;
            for (int i = 0; i < n; i++) {
                float g = kids.get(i).growWeight();
                if (g > 0f) {
                    int add = (int) (free * (g / totalGrow));
                    mainLen[i] += add;
                    remaining -= add;
                    lastGrower = i;
                }
            }
            if (lastGrower >= 0) {
                mainLen[lastGrower] += remaining; // absorb rounding remainder
            }
        } else if (free < 0) {
            // over-full — remove the deficit proportionally to shrink*baseSize, floored at 0, so
            // children stay inside the parent instead of painting off-screen.
            int deficit = -free;
            double weighted = 0d;
            for (int i = 0; i < n; i++) {
                weighted += kids.get(i).shrinkWeight() * mainLen[i];
            }
            if (weighted > 0d) {
                int removed = 0;
                int lastShrinker = -1;
                for (int i = 0; i < n; i++) {
                    double w = kids.get(i).shrinkWeight() * mainLen[i];
                    if (w > 0d) {
                        int cut = (int) (deficit * (w / weighted));
                        int newLen = Math.max(0, mainLen[i] - cut);
                        removed += mainLen[i] - newLen;
                        mainLen[i] = newLen;
                        lastShrinker = i;
                    }
                }
                int stillOver = deficit - removed; // rounding: remove the rest from the last shrinker
                if (lastShrinker >= 0 && stillOver > 0) {
                    mainLen[lastShrinker] = Math.max(0, mainLen[lastShrinker] - stillOver);
                }
            }
        }

        // 3) main-axis start + inter-child spacing (justify only bites when nothing grows).
        int startOffset = 0;
        int between = c.gap();
        if (totalGrow == 0f && free > 0) {
            switch (c.justify()) {
                case CENTER -> startOffset = free / 2;
                case END -> startOffset = free;
                case SPACE_BETWEEN -> {
                    if (n > 1) {
                        between = c.gap() + free / (n - 1);
                    } else {
                        startOffset = free / 2;
                    }
                }
                case START -> startOffset = 0;
            }
        }

        // 4) place each child.
        int mainOrigin = (row ? content.x() : content.y()) + startOffset;
        int crossOrigin = row ? content.y() : content.x();
        List<LaidOut> out = new ArrayList<>(n);
        int cursor = mainOrigin;
        for (int i = 0; i < n; i++) {
            Widget<?> k = kids.get(i);
            int childMain = mainLen[i];

            int crossExplicit = row ? k.heightSpec() : k.widthSpec();
            int childCross;
            if (crossExplicit >= 0) {
                childCross = crossExplicit;
            } else if (c.align() == AlignItems.STRETCH) {
                childCross = crossSize;
            } else {
                childCross = row ? k.intrinsicHeight(metrics) : k.intrinsicWidth(metrics);
            }
            childCross = Math.min(childCross, crossSize);

            int crossPos = crossOrigin + crossOffset(c.align(), crossSize, childCross);
            Rect childBox = row
                    ? new Rect(cursor, crossPos, childMain, childCross)
                    : new Rect(crossPos, cursor, childCross, childMain);
            out.add(layout(k, childBox, metrics));
            cursor += childMain + between;
        }
        return new LaidOut(c, box, out);
    }

    private static int crossOffset(AlignItems align, int crossSize, int childCross) {
        return switch (align) {
            case CENTER -> Math.max(0, (crossSize - childCross) / 2);
            case END -> Math.max(0, crossSize - childCross);
            case START, STRETCH -> 0;
        };
    }
}
