/*
 * Aetherium Framework — cross-axis alignment.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/** How children align on the cross axis (Flexbox {@code align-items}). */
public enum AlignItems {
    START,
    CENTER,
    END,
    /** Stretch each child to fill the container's cross size (unless it has an explicit cross size). */
    STRETCH
}
