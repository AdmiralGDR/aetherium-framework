/*
 * Aetherium Framework — reactive reaction base (internal): source tracking + cleanup.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.reactive;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Base for an {@link Effect}/{@link Computed}: remembers which {@link Signal}s it read last run so it can
 * <em>unsubscribe</em> before re-running — the fix for stale dynamic dependencies.
 *
 * <p>EN: Reactions are dynamic — an {@code if} inside the body means the set of signals read can change every
 * run. Without cleanup, a signal the body no longer reads would keep this reaction subscribed forever (a leak
 * and spurious re-runs). {@link #track} clears the old source links, re-runs under {@link ReactiveScope}, and
 * lets each {@link Signal#get()} re-link — so the dependency set is always exactly what the last run read.
 * RU: Реакции динамичны — {@code if} в теле меняет набор читаемых сигналов между запусками. Без очистки сигнал,
 * который тело больше не читает, держал бы подписку вечно (утечка и лишние перезапуски). {@link #track} снимает
 * старые связи, перезапускает тело под {@link ReactiveScope}, и каждый {@link Signal#get()} связывается заново.
 */
abstract class Reaction implements Subscriber {

    private final Set<Signal<?>> sources = new LinkedHashSet<>();

    /** Clear old source links, then run {@code body} tracked so reads re-link this reaction to its sources. */
    protected final void track(Runnable body) {
        releaseSources();
        ReactiveScope.runTracked(this, body);
    }

    /** Called by {@link Signal#get()} when this reaction is the one reading — records the source for cleanup. */
    final void linkSource(Signal<?> source) {
        sources.add(source);
    }

    /** Unsubscribe from every current source (before a re-run, or on dispose). */
    protected final void releaseSources() {
        for (Signal<?> source : sources) {
            source.unsubscribe(this);
        }
        sources.clear();
    }
}
