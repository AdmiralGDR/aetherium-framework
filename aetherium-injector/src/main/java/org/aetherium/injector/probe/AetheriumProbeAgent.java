/*
 * Aetherium Framework — JVMTI agent shim that captures Instrumentation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.probe;

import java.lang.instrument.Instrumentation;

/**
 * The Java agent entry point whose sole job is to hand the live {@link Instrumentation} to the
 * framework — the key that unlocks class re-transformation for ephemeral probes.
 *
 * <p>EN: Re-transforming an already-loaded class (to weave a probe in, then strip it out) requires an
 * {@link Instrumentation} with retransform capability, which only a JVM agent receives. This shim can be
 * attached two ways: as a {@code -javaagent}/{@code premain} at startup, or — more usefully for an
 * ephemeral, on-demand profile — loaded into the running JVM via the Attach API by
 * {@link DynamicProbeController} ({@code agentmain}). Either way it just stows the handle in
 * {@link #INSTRUMENTATION}; it never instruments anything itself.
 *
 * <p>RU: Повторная трансформация уже загруженного класса (вплести зонд, затем убрать) требует
 * {@link Instrumentation} со способностью retransform, которую получает только агент JVM. Этот shim
 * подключается двумя путями: как {@code -javaagent}/{@code premain} при старте, или — что полезнее для
 * эфемерного профиля по запросу — загружается в работающую JVM через Attach API
 * ({@code agentmain}). В любом случае он лишь сохраняет хэндл в {@link #INSTRUMENTATION}.
 */
public final class AetheriumProbeAgent {

    /** The live instrumentation handle, or {@code null} if no agent has attached. */
    public static volatile Instrumentation INSTRUMENTATION;

    private AetheriumProbeAgent() {
    }

    /** Startup agent entry ({@code -javaagent:aetherium.jar}). */
    public static void premain(String args, Instrumentation inst) {
        INSTRUMENTATION = inst;
    }

    /** On-demand attach entry (loaded into a running JVM via the Attach API). */
    public static void agentmain(String args, Instrumentation inst) {
        INSTRUMENTATION = inst;
    }
}
