/*
 * Aetherium Framework — the fail-loud result contract.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The result of an operation that may legitimately not run — the framework's <strong>fail-loud
 * contract</strong>: a capability that skips, degrades, or fails <em>must tell its caller why</em>, never
 * silently no-op or silently swap in a fallback.
 *
 * <p>EN: A {@code MethodHandle}-fast path, a GPU dispatch, a live class redefinition, or a rollback step can
 * all fail to run for a benign reason (no device, no {@code --enable-preview}, an unsupported redefine) or an
 * error. Returning the fallback value alone hides that from the caller — the exact anti-pattern this type
 * bans. An {@code Outcome<T>} is one of three sealed cases, so a {@code switch} over it is exhaustive (no
 * defensive {@code default}) and the caller cannot forget the not-run branches:
 * <ul>
 *   <li>{@link Ran} — the intended path ran and produced a {@code result};</li>
 *   <li>{@link Skipped} — it did not run for an expected, benign reason (a {@link Diagnostic}); no value;</li>
 *   <li>{@link Failed} — it was attempted and failed (a {@link Diagnostic}); no value.</li>
 * </ul>
 * Use {@link #orElseGet} to supply a fallback while {@link #onSkipped}/{@link #onFailed} or {@link #reason}
 * surface the cause, or {@link #orElseThrow} when not running is itself an error. Pure, zero-dependency.
 *
 * <p>RU: Быстрый путь, GPU-диспатч, live-переопределение класса или шаг отката могут не выполниться по
 * благонамеренной причине (нет устройства, нет {@code --enable-preview}, неподдерживаемый redefine) или из-за
 * ошибки. Вернуть только запасное значение — значит скрыть это от вызывающего; ровно этот анти-паттерн тип и
 * запрещает. {@code Outcome<T>} — один из трёх запечатанных случаев, поэтому {@code switch} исчерпывающий (без
 * защитного {@code default}), и вызывающий не забудет ветки «не выполнено»: {@link Ran} (выполнено, есть
 * {@code result}), {@link Skipped} (не выполнено по ожидаемой причине, {@link Diagnostic}, значения нет),
 * {@link Failed} (попытка не удалась, {@link Diagnostic}, значения нет). Чисто, без зависимостей.
 *
 * @param <T> the value type produced when the operation runs
 */
public sealed interface Outcome<T> permits Outcome.Ran, Outcome.Skipped, Outcome.Failed {

    /** The intended path ran and produced {@code result} (which may be {@code null} if that is a valid value). */
    record Ran<T>(T result) implements Outcome<T> {
    }

    /** The intended path did not run for an expected, benign {@code diagnostic}; no value was produced. */
    record Skipped<T>(Diagnostic diagnostic) implements Outcome<T> {
        public Skipped {
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    /** The intended path was attempted and failed for {@code diagnostic}; no value was produced. */
    record Failed<T>(Diagnostic diagnostic) implements Outcome<T> {
        public Failed {
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    static <T> Outcome<T> ran(T value) {
        return new Ran<>(value);
    }

    static <T> Outcome<T> skipped(Diagnostic reason) {
        return new Skipped<>(reason);
    }

    static <T> Outcome<T> failed(Diagnostic reason) {
        return new Failed<>(reason);
    }

    /** Whether the intended path actually ran. Authoritative "did it run" check (a {@link Ran} of null still ran). */
    default boolean ran() {
        return this instanceof Ran<T>;
    }

    default boolean skipped() {
        return this instanceof Skipped<T>;
    }

    default boolean failed() {
        return this instanceof Failed<T>;
    }

    /** The produced value if it ran (as an {@link Optional}; empty when skipped/failed or a {@link Ran} of null). */
    default Optional<T> value() {
        return this instanceof Ran<T> r ? Optional.ofNullable(r.result()) : Optional.empty();
    }

    /** The diagnostic explaining why it did not run, or empty when it ran. */
    default Optional<Diagnostic> reason() {
        return switch (this) {
            case Ran<T> ignored -> Optional.empty();
            case Skipped<T> s -> Optional.of(s.diagnostic());
            case Failed<T> f -> Optional.of(f.diagnostic());
        };
    }

    /** The value if it ran, else {@code fallback}. */
    default T orElse(T fallback) {
        return this instanceof Ran<T> r ? r.result() : fallback;
    }

    /** The value if it ran, else the lazily-computed {@code fallback}. */
    default T orElseGet(Supplier<? extends T> fallback) {
        return this instanceof Ran<T> r ? r.result() : fallback.get();
    }

    /** The value if it ran, else throw an {@link AetheriumException} carrying the not-run {@link Diagnostic}. */
    default T orElseThrow() {
        return switch (this) {
            case Ran<T> r -> r.result();
            case Skipped<T> s -> throw new AetheriumException(s.diagnostic());
            case Failed<T> f -> throw new AetheriumException(f.diagnostic());
        };
    }

    /** Map the value if it ran; a skipped/failed outcome passes its {@link Diagnostic} through unchanged. */
    default <R> Outcome<R> map(Function<? super T, ? extends R> fn) {
        return switch (this) {
            case Ran<T> r -> new Ran<>(fn.apply(r.result()));
            case Skipped<T> s -> new Skipped<>(s.diagnostic());
            case Failed<T> f -> new Failed<>(f.diagnostic());
        };
    }

    /** Run {@code action} with the reason iff this was {@link Skipped}; returns {@code this} for chaining. */
    default Outcome<T> onSkipped(Consumer<Diagnostic> action) {
        if (this instanceof Skipped<T> s) {
            action.accept(s.diagnostic());
        }
        return this;
    }

    /** Run {@code action} with the reason iff this was {@link Failed}; returns {@code this} for chaining. */
    default Outcome<T> onFailed(Consumer<Diagnostic> action) {
        if (this instanceof Failed<T> f) {
            action.accept(f.diagnostic());
        }
        return this;
    }
}
