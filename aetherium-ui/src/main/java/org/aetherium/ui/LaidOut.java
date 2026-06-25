/*
 * Aetherium Framework — a widget with its computed layout box.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.List;

/**
 * The immutable result of laying out one widget: the widget, its absolute {@link Rect}, and its laid-out
 * children. {@link FlexLayout} produces this tree; paint and hit-testing walk it.
 */
public record LaidOut(Widget<?> widget, Rect rect, List<LaidOut> children) {

    public LaidOut {
        children = List.copyOf(children);
    }
}
