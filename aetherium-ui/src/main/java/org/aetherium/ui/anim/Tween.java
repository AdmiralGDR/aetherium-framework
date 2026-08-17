/*
 * Aetherium Framework — time-anchored tween + interpolation helpers.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.anim;

/**
 * A time-anchored animation ramp: eased progress from 0 to 1 between a start time and a duration.
 *
 * <p>EN: A {@code Tween} holds no value of its own — it turns "now" (from a {@link
 * org.aetherium.core.tick.FrameClock}) into an eased {@code [0,1]} progress, and the caller {@link #lerp}s
 * whatever it wants (a position, an opacity, an argb colour) by that progress. Progress is clamped, so
 * sampling before the start reads 0 and after the end reads 1 (a finished animation holds its final frame).
 * Deterministic given the clock; zero-dependency. Example:
 * <pre>{@code
 * Tween t = new Tween(clock.nanos(), 300_000_000L, Easing.EASE_OUT); // 300 ms ease-out
 * int x = Tween.lerpInt(0, 200, t.progress(clock.nanos()));          // slide 0 -> 200 px
 * }</pre>
 *
 * <p>RU: {@code Tween} не хранит значение — он превращает «сейчас» (из {@link
 * org.aetherium.core.tick.FrameClock}) в сглаженный прогресс {@code [0,1]}, а вызывающий интерполирует любое
 * (позицию, прозрачность, цвет argb). Прогресс ограничен: до старта 0, после конца 1 (законченная анимация
 * держит финальный кадр). Детерминирован при заданных часах; без зависимостей.
 */
public final class Tween {

    private final long startNanos;
    private final long durationNanos;
    private final Easing easing;

    public Tween(long startNanos, long durationNanos, Easing easing) {
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must be >= 0: " + durationNanos);
        }
        this.startNanos = startNanos;
        this.durationNanos = durationNanos;
        this.easing = java.util.Objects.requireNonNull(easing, "easing");
    }

    /** Eased progress in {@code [0,1]} at {@code nowNanos} (clamped; a zero-duration tween is instantly 1). */
    public double progress(long nowNanos) {
        if (durationNanos == 0) {
            return 1.0;
        }
        double t = (double) (nowNanos - startNanos) / durationNanos;
        t = Math.max(0.0, Math.min(1.0, t));
        return easing.ease(t);
    }

    /** Whether the tween has reached its end at {@code nowNanos}. */
    public boolean done(long nowNanos) {
        return nowNanos - startNanos >= durationNanos;
    }

    // --- interpolation helpers (pure) -----------------------------------------------------------

    /** Linear interpolation {@code a + (b-a)*t}. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Rounded integer interpolation (e.g. pixel positions). */
    public static int lerpInt(int a, int b, double t) {
        return (int) Math.round(a + (double) (b - a) * t);
    }

    /**
     * Per-channel interpolation of two ARGB colours. Each of A, R, G, B is lerped independently and clamped to
     * a byte, so a fade or a colour transition is smooth and never overflows a channel.
     */
    public static int lerpArgb(int argbA, int argbB, double t) {
        int a = lerpChannel(argbA >>> 24, argbB >>> 24, t);
        int r = lerpChannel((argbA >> 16) & 0xFF, (argbB >> 16) & 0xFF, t);
        int g = lerpChannel((argbA >> 8) & 0xFF, (argbB >> 8) & 0xFF, t);
        int b = lerpChannel(argbA & 0xFF, argbB & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int a, int b, double t) {
        int v = (int) Math.round(a + (double) (b - a) * t);
        return Math.max(0, Math.min(255, v));
    }
}
