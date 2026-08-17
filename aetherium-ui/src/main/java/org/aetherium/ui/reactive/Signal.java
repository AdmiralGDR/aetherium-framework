/*
 * Aetherium Framework — reactive state cell (fine-grained signal).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.reactive;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A mutable reactive value — the atom of Aetherium's reactive UI. Reading it inside an {@link Effect} or
 * {@link Computed} subscribes that reaction; writing a <em>different</em> value re-runs every subscriber.
 *
 * <p>EN: Fine-grained reactivity with no virtual DOM and no dependency arrays — the dependency graph is
 * discovered by execution. {@link #get()} registers the currently-running reaction (see {@link ReactiveScope});
 * {@link #set(Object)} notifies subscribers only when the value actually changed (by {@link Objects#equals},
 * which dampens diamond re-runs). {@link #peek()} reads without subscribing. Single-threaded by design (the UI
 * thread owns the graph); zero-dependency. Example:
 * <pre>{@code
 * Signal<Integer> count = Signal.of(0);
 * Effect.create(() -> System.out.println("count = " + count.get())); // prints 0, and again on each change
 * count.set(1); // re-runs the effect -> prints 1
 * }</pre>
 *
 * <p>RU: Мелкозернистая реактивность без virtual DOM и без массивов зависимостей — граф зависимостей
 * обнаруживается исполнением. {@link #get()} регистрирует текущую реакцию; {@link #set(Object)} уведомляет
 * подписчиков только при реальном изменении значения (по {@link Objects#equals}, что гасит повторные запуски в
 * «ромбах»); {@link #peek()} читает без подписки. Однопоточный по замыслу; без зависимостей.
 *
 * @param <T> the value type
 */
public final class Signal<T> {

    private T value;
    private final Set<Subscriber> subscribers = new LinkedHashSet<>();

    public Signal(T initial) {
        this.value = initial;
    }

    /** Create a signal holding {@code initial}. */
    public static <T> Signal<T> of(T initial) {
        return new Signal<>(initial);
    }

    /** Read the value AND subscribe the currently-running reaction (if any) to future changes. */
    public T get() {
        Subscriber current = ReactiveScope.current();
        if (current != null) {
            subscribers.add(current);
            if (current instanceof Reaction reaction) {
                reaction.linkSource(this);
            }
        }
        return value;
    }

    /** Read the value WITHOUT subscribing — use in an event handler that must not create a dependency. */
    public T peek() {
        return value;
    }

    /** Set a new value; re-runs every subscriber iff it differs from the current value. */
    public void set(T next) {
        if (Objects.equals(value, next)) {
            return;
        }
        value = next;
        // Snapshot: a subscriber's invalidate() re-runs its body, which re-subscribes (mutating this set).
        for (Subscriber subscriber : List.copyOf(subscribers)) {
            subscriber.invalidate();
        }
    }

    /** Set a new value derived from the current one (read-modify-write without a subscription). */
    public void update(UnaryOperator<T> fn) {
        set(fn.apply(value));
    }

    /** How many reactions currently depend on this signal (visibility for tests). */
    public int subscriberCount() {
        return subscribers.size();
    }

    void unsubscribe(Subscriber subscriber) {
        subscribers.remove(subscriber);
    }
}
