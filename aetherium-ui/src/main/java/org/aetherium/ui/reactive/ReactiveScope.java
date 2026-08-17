/*
 * Aetherium Framework — reactive dependency-tracking scope (internal).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.reactive;

/**
 * The currently-running reaction, per thread — how a {@link Signal#get()} discovers <em>who</em> is reading it.
 *
 * <p>EN: When an {@link Effect}/{@link Computed} runs its body, it installs itself here for the duration; any
 * {@link Signal#get()} executed inside then registers a dependency automatically (fine-grained tracking, no
 * manual subscribe). Thread-local, so the reactive graph is single-threaded per thread — the UI thread owns
 * its own graph with no locking. Reads outside any reaction (current is {@code null}) simply don't subscribe.
 * RU: Пока {@link Effect}/{@link Computed} выполняет тело, он устанавливает себя здесь; любой
 * {@link Signal#get()} внутри автоматически регистрирует зависимость. Thread-local — граф однопоточный, без
 * блокировок; чтение вне реакции ({@code null}) не подписывается.
 */
final class ReactiveScope {

    private static final ThreadLocal<Subscriber> CURRENT = new ThreadLocal<>();

    private ReactiveScope() {
    }

    /** The reaction currently running on this thread, or {@code null} if a read is untracked. */
    static Subscriber current() {
        return CURRENT.get();
    }

    /** Run {@code body} with {@code subscriber} installed as the current reaction, then restore the previous. */
    static void runTracked(Subscriber subscriber, Runnable body) {
        Subscriber previous = CURRENT.get();
        CURRENT.set(subscriber);
        try {
            body.run();
        } finally {
            CURRENT.set(previous);
        }
    }
}
