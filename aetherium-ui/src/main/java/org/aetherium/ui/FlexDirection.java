/*
 * Aetherium Framework — flex main-axis direction.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/** The main axis a {@link Container} lays its children along (Flexbox {@code flex-direction}). */
public enum FlexDirection {
    /** Children flow left→right; main axis = width. */
    ROW,
    /** Children flow top→bottom; main axis = height. */
    COLUMN
}
