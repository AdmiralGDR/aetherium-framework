/*
 * Aetherium Framework — injectable monotonic frame clock.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.tick;

/**
 * A monotonic time source, injectable so time-dependent subsystems stay deterministic under test.
 *
 * <p>EN: The reactive UI's animations and the fixed-step simulation both need "how much time has passed",
 * but reading {@code System.nanoTime()} directly makes them untestable and non-reproducible. A {@code
 * FrameClock} is the seam: {@link #system()} is the real monotonic clock in-game, while {@link Manual} is a
 * hand-advanced clock a test drives step by step — so an animation curve or a sim tick is asserted at exact
 * times, with no sleeping and no flakiness. Monotonic by contract: {@link #nanos()} never goes backwards.
 *
 * <p>RU: Анимациям реактивного UI и симуляции с фиксированным шагом нужно «сколько времени прошло», но прямое
 * чтение {@code System.nanoTime()} делает их непроверяемыми и невоспроизводимыми. {@code FrameClock} — это
 * шов: {@link #system()} — настоящие монотонные часы в игре, а {@link Manual} — часы, которые тест двигает сам,
 * поэтому кривая анимации или тик симуляции проверяются в точные моменты, без сна и без флаки-тестов.
 * Монотонность по контракту: {@link #nanos()} никогда не идёт назад.
 */
@FunctionalInterface
public interface FrameClock {

    /** The current time in monotonic nanoseconds. Never decreases across calls on the same instance. */
    long nanos();

    /** Milliseconds derived from {@link #nanos()} (integer division). */
    default long millis() {
        return nanos() / 1_000_000L;
    }

    /** The real, wall-independent monotonic clock ({@code System.nanoTime}) — the in-game default. */
    static FrameClock system() {
        return System::nanoTime;
    }

    /**
     * A hand-advanced clock for deterministic tests and headless simulation. Not thread-safe: a test drives
     * it from one thread. {@link #advance} only ever moves time forward (monotonicity is enforced).
     */
    final class Manual implements FrameClock {

        private long nanos;

        public Manual() {
            this(0L);
        }

        public Manual(long startNanos) {
            this.nanos = startNanos;
        }

        @Override
        public long nanos() {
            return nanos;
        }

        /** Advance by {@code deltaNanos} (must be &ge; 0, preserving monotonicity). */
        public void advance(long deltaNanos) {
            if (deltaNanos < 0) {
                throw new IllegalArgumentException("deltaNanos must be >= 0 (clock is monotonic): " + deltaNanos);
            }
            nanos += deltaNanos;
        }

        /** Advance by {@code deltaMillis} milliseconds. */
        public void advanceMillis(long deltaMillis) {
            advance(Math.multiplyExact(deltaMillis, 1_000_000L));
        }
    }
}
