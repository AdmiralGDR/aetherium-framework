/*
 * Aetherium Framework — easing curves for UI animation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.anim;

/**
 * An easing curve: maps a linear progress {@code t} in {@code [0,1]} to an eased progress in {@code [0,1]}.
 *
 * <p>EN: The shape of motion. {@code LINEAR} is constant speed; the cubic ins/outs accelerate or decelerate so
 * a moving panel or a fading tooltip feels natural instead of mechanical. Pure functions of {@code t}, so an
 * animation driven by a {@link org.aetherium.core.tick.FrameClock} is fully deterministic and testable. Inputs
 * are clamped by the caller ({@link Tween#progress}); an easing itself assumes {@code t} in {@code [0,1]}.
 * RU: Форма движения. {@code LINEAR} — постоянная скорость; кубические in/out ускоряют или замедляют, поэтому
 * панель или всплывающая подсказка движутся естественно, а не механически. Чистые функции от {@code t} —
 * анимация на {@link org.aetherium.core.tick.FrameClock} детерминирована и тестируема.
 */
@FunctionalInterface
public interface Easing {

    double ease(double t);

    /** Constant speed. */
    Easing LINEAR = t -> t;

    /** Slow start (cubic). */
    Easing EASE_IN = t -> t * t * t;

    /** Slow stop (cubic). */
    Easing EASE_OUT = t -> {
        double u = 1.0 - t;
        return 1.0 - u * u * u;
    };

    /** Slow start and stop (cubic), symmetric about the midpoint. */
    Easing EASE_IN_OUT = t -> {
        if (t < 0.5) {
            return 4.0 * t * t * t;
        }
        double u = -2.0 * t + 2.0;
        return 1.0 - (u * u * u) / 2.0;
    };
}
