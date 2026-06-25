/*
 * Aetherium Framework — shared on-demand Instrumentation acquisition.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.probe;

import java.lang.instrument.Instrumentation;

/**
 * The framework's single door to a live {@link Instrumentation}, shared by every subsystem that needs
 * to redefine or retransform classes (ephemeral JFR probes <em>and</em> the live hot-swap engine).
 *
 * <p>EN: Returns an already-attached agent's handle ({@link AetheriumProbeAgent#INSTRUMENTATION}) if
 * present, otherwise tries a best-effort self-attach via the Attach API ({@link SelfAttach}). Both paths
 * are reflective/optional, so a locked-down JVM yields {@code null} rather than failing — callers
 * degrade gracefully. This consolidates acquisition so {@code aetherium-hotswap} reuses the exact
 * mechanism {@link DynamicProbeController} already relies on.
 * RU: Возвращает хэндл уже подключённого агента ({@link AetheriumProbeAgent#INSTRUMENTATION}), если он
 * есть, иначе пытается self-attach через Attach API ({@link SelfAttach}). Оба пути
 * рефлексивные/опциональные, поэтому заблокированная JVM даёт {@code null}, а не сбой — вызывающий код
 * мягко деградирует. Это объединяет получение, чтобы {@code aetherium-hotswap} переиспользовал тот же
 * механизм, что уже использует {@link DynamicProbeController}.
 */
public final class InstrumentationSupport {

    private static volatile Instrumentation cached;

    private InstrumentationSupport() {
    }

    /** A retransform/redefine-capable {@link Instrumentation}, or {@code null} on a locked-down JVM. */
    public static Instrumentation acquire() {
        Instrumentation existing = cached;
        if (existing != null) {
            return existing;
        }
        if (AetheriumProbeAgent.INSTRUMENTATION != null) {
            cached = AetheriumProbeAgent.INSTRUMENTATION;
            return cached;
        }
        synchronized (InstrumentationSupport.class) {
            if (cached != null) {
                return cached;
            }
            cached = SelfAttach.tryAcquire();
            return cached;
        }
    }

    /** True if a live {@link Instrumentation} can be obtained (i.e. instant class redefinition works). */
    public static boolean available() {
        return acquire() != null;
    }
}
