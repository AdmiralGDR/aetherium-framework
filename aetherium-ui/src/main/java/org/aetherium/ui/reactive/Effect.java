/*
 * Aetherium Framework — reactive effect (auto-re-running side effect).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.reactive;

import java.util.Objects;

/**
 * A side effect that runs once immediately and re-runs whenever any {@link Signal} it read changes.
 *
 * <p>EN: The reactive glue between state and the world — repaint a widget, push a value to the platform, log a
 * change. The body's signal reads are tracked on every run (dynamic dependencies), and {@link #dispose()}
 * unsubscribes it for good. Single-threaded (run it on the UI thread). Zero-dependency.
 * RU: Реактивный клей между состоянием и миром — перерисовать виджет, протолкнуть значение на платформу.
 * Чтения сигналов в теле отслеживаются на каждом запуске (динамические зависимости); {@link #dispose()}
 * окончательно отписывает эффект. Однопоточный; без зависимостей.
 */
public final class Effect extends Reaction {

    private final Runnable body;
    private boolean disposed;

    public Effect(Runnable body) {
        this.body = Objects.requireNonNull(body, "body");
        track(body); // initial run establishes the dependency set
    }

    /** Create and immediately run an effect over {@code body}. */
    public static Effect create(Runnable body) {
        return new Effect(body);
    }

    @Override
    public void invalidate() {
        if (!disposed) {
            track(body);
        }
    }

    /** Stop the effect: unsubscribe from all sources so it never runs again. */
    public void dispose() {
        disposed = true;
        releaseSources();
    }

    /** Whether {@link #dispose()} has been called. */
    public boolean disposed() {
        return disposed;
    }
}
