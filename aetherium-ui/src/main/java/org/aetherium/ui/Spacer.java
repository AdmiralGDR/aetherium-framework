/*
 * Aetherium Framework — a flexible empty-space widget.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * Empty space. Give it {@link #grow(float)} to push siblings apart (the Flexbox spacer idiom).
 */
public final class Spacer extends Widget<Spacer> {

    @Override
    public int intrinsicWidth(UiMetrics metrics) {
        return padding().horizontal();
    }

    @Override
    public int intrinsicHeight(UiMetrics metrics) {
        return padding().vertical();
    }
}
