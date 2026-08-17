/*
 * Aetherium Framework — reactive subscriber (internal).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.reactive;

/**
 * A dependent that a {@link Signal} notifies when its value changes.
 *
 * <p>EN: Package-private seam between a {@link Signal} (the source) and an {@link Effect}/{@link Computed}
 * (the reaction). {@link #invalidate()} tells the reaction one of its tracked sources changed.
 * RU: Внутренний шов между {@link Signal} (источник) и {@link Effect}/{@link Computed} (реакция).
 */
interface Subscriber {

    /** One of this subscriber's tracked sources changed — recompute/re-run. */
    void invalidate();
}
