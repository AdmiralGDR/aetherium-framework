/*
 * Aetherium Framework — reactive derived value.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.reactive;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A read-only value derived from other signals — recomputed when its inputs change, and itself observable.
 *
 * <p>EN: {@code Computed} is an {@link Effect} that writes its result into a private output {@link Signal}: when
 * a source changes the effect recomputes and stores the new value, and reading the computed ({@link #get()})
 * subscribes to that output signal exactly like reading a plain signal. Because the write goes through
 * {@link Signal#set(Object)}, an unchanged recomputation does <em>not</em> propagate (equality-dampened), so
 * chains and diamonds settle without redundant downstream work. Composable to any depth. Zero-dependency.
 * <pre>{@code
 * Signal<Integer> a = Signal.of(2), b = Signal.of(3);
 * Computed<Integer> sum = Computed.of(() -> a.get() + b.get());
 * sum.get(); // 5, and re-derives whenever a or b changes
 * }</pre>
 *
 * <p>RU: {@code Computed} — это {@link Effect}, пишущий результат в приватный выходной {@link Signal}: при
 * изменении источника эффект пересчитывает и сохраняет значение, а чтение computed ({@link #get()})
 * подписывается на этот выходной сигнал как на обычный. Поскольку запись идёт через {@link Signal#set(Object)},
 * неизменившийся пересчёт не распространяется (гашение по равенству). Композируется на любую глубину.
 *
 * @param <T> the derived value type
 */
public final class Computed<T> {

    private final Signal<T> output = new Signal<>(null);
    private final Effect recompute;

    public Computed(Supplier<T> derive) {
        Objects.requireNonNull(derive, "derive");
        // The effect's body reads `derive`'s sources (tracked) and writes the result; it does not read
        // `output`, so it never depends on itself. The constructor's initial run seeds `output`.
        this.recompute = new Effect(() -> output.set(derive.get()));
    }

    /** Create a derived value from {@code derive}. */
    public static <T> Computed<T> of(Supplier<T> derive) {
        return new Computed<>(derive);
    }

    /** Read the derived value AND subscribe the currently-running reaction to future changes. */
    public T get() {
        return output.get();
    }

    /** Read the derived value WITHOUT subscribing. */
    public T peek() {
        return output.peek();
    }

    /** Stop deriving: unsubscribe from the source signals. */
    public void dispose() {
        recompute.dispose();
    }
}
