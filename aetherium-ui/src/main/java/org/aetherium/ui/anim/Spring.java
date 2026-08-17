/*
 * Aetherium Framework — damped-spring animator for natural motion.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.anim;

/**
 * A damped harmonic spring that eases a value toward a moving target — the physical feel behind a snapping
 * panel, a settling slider knob, or a toast that springs in.
 *
 * <p>EN: Unlike a {@link Tween} (fixed duration, fixed endpoints), a spring reacts to a target that can change
 * mid-flight and carries momentum, so redirecting it looks natural. It integrates {@code F = -k·(x−target) −
 * c·v} with a semi-implicit Euler step for stability; {@link #step(double)} advances by a time delta (seconds,
 * from a {@link org.aetherium.core.tick.FrameClock}) and returns the new value. Deterministic given the same
 * deltas; zero-dependency. Use {@link #critical(double, double)} for the common no-overshoot case.
 * RU: В отличие от {@link Tween} (фиксированные длительность и концы), пружина реагирует на цель, меняющуюся на
 * лету, и несёт импульс, поэтому перенаправление выглядит естественно. Интегрирует {@code F = -k·(x−target) −
 * c·v} полу-неявным шагом Эйлера; {@link #step(double)} продвигает на дельту времени (секунды, из
 * {@link org.aetherium.core.tick.FrameClock}) и возвращает новое значение. Детерминирована; без зависимостей.
 */
public final class Spring {

    private final double stiffness;
    private final double damping;
    private double value;
    private double velocity;
    private double target;

    public Spring(double initial, double stiffness, double damping) {
        if (stiffness <= 0) {
            throw new IllegalArgumentException("stiffness must be > 0: " + stiffness);
        }
        if (damping < 0) {
            throw new IllegalArgumentException("damping must be >= 0: " + damping);
        }
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    /**
     * A critically-damped spring (no overshoot, fastest settle) for a given {@code stiffness}: damping is set
     * to {@code 2·sqrt(stiffness)}, the critical value for a unit mass.
     */
    public static Spring critical(double initial, double stiffness) {
        return new Spring(initial, stiffness, 2.0 * Math.sqrt(stiffness));
    }

    /** Aim the spring at a new target; momentum is preserved so a redirect looks natural. */
    public void setTarget(double target) {
        this.target = target;
    }

    /** Advance by {@code dtSeconds} and return the new value (semi-implicit Euler: update velocity, then value). */
    public double step(double dtSeconds) {
        if (dtSeconds < 0) {
            throw new IllegalArgumentException("dtSeconds must be >= 0: " + dtSeconds);
        }
        double force = -stiffness * (value - target) - damping * velocity;
        velocity += force * dtSeconds;
        value += velocity * dtSeconds;
        return value;
    }

    public double value() {
        return value;
    }

    public double target() {
        return target;
    }

    /** Whether the spring has effectively reached its target (value and velocity both within {@code epsilon}). */
    public boolean settled(double epsilon) {
        return Math.abs(value - target) < epsilon && Math.abs(velocity) < epsilon;
    }
}
