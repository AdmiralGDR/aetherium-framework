/*
 * Aetherium Framework — mod runtime context.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.mod;

import org.aetherium.core.CapabilityTier;

/**
 * The loader-agnostic context handed to an {@link AetheriumMod} at initialization.
 *
 * <p>EN: This is the entire surface a mod needs from the host — logging and the resolved compute
 * tier — with <strong>no Minecraft or NeoForge types</strong>. The loader supplies the concrete
 * implementation (which may log through NeoForge's SLF4J); the mod stays pure and portable across
 * loaders. Keeping this in {@code aetherium-core} is what lets {@code aetherium-testmod} target the
 * Aetherium API without ever importing a loader class.
 *
 * <p>RU: Это вся поверхность, нужная моду от хоста — логирование и выбранный уровень вычислений — и
 * <strong>без типов Minecraft или NeoForge</strong>. Загрузчик предоставляет конкретную реализацию
 * (которая может логировать через SLF4J NeoForge); мод остаётся чистым и переносимым между
 * загрузчиками. Размещение этого в {@code aetherium-core} и позволяет {@code aetherium-testmod}
 * целиться в API Aetherium, не импортируя ни одного класса загрузчика.
 */
public interface AetheriumContext {

    /** Log an informational message through the host's logging pipeline. */
    void log(String message);

    /** The compute capability tier the framework resolved for this launch. */
    CapabilityTier computeTier();

    /**
     * The physical side of this JVM ({@link Side#CLIENT} or {@link Side#SERVER}) — never {@link Side#BOTH}.
     * The loader supplies the real dist; a headless/off-platform context defaults to {@link Side#SERVER}
     * (the safe assumption for a no-client tool). The generated {@code @AetheriumInit} dispatch reads this to
     * gate side-declared inits ().
     */
    default Side side() {
        return Side.SERVER;
    }

    /**
     * Whether an init/feature declared for {@code declared} should run on this JVM's {@link #side()} —
     * shorthand for {@code declared.activeOn(side())}. A {@code null} {@code declared} is treated as
     * {@link Side#BOTH} (always runs).
     */
    default boolean runsOnSide(Side declared) {
        return declared == null || declared.activeOn(side());
    }
}
