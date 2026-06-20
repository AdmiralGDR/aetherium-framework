/*
 * Aetherium Framework — ephemeral dynamic-probe controller (hot-swap on demand).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.probe;

import org.aetherium.bytecode.BytecodeEngine;
import org.aetherium.bytecode.CollectingDiagnosticSink;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Turns ephemeral JFR probes on and off at runtime, hot-swapping probe bytecode into target methods
 * only while a profile is requested — the delivery vehicle for zero-static-overhead telemetry.
 *
 * <p>EN: Holds the active {@link ProbeTarget} set. When you {@link #enable(ProbeTarget)} a probe and a
 * retransform-capable {@link Instrumentation} is available (via {@link AetheriumProbeAgent}, attached at
 * startup or on demand through the Attach API), it re-transforms the already-loaded target class so
 * {@link ProbeWeaver} weaves the JFR begin/commit in; {@link #disable(ProbeTarget)}/{@link #clear()}
 * re-transform again from the cached <em>original</em> bytes, physically removing the probe. There is no
 * runtime flag on the hot path — an un-probed method has no probe code at all. If no agent can be
 * acquired (locked-down JVM), the controller degrades gracefully: the active set still feeds the
 * load-time {@code ProbeWeaver}, so probes apply at the next class load instead of instantly.
 *
 * <p>RU: Хранит активное множество {@link ProbeTarget}. При {@link #enable(ProbeTarget)} и наличии
 * {@link Instrumentation} со способностью retransform (через {@link AetheriumProbeAgent}, подключённый на
 * старте или по запросу через Attach API) уже загруженный класс ретрансформируется, и {@link ProbeWeaver}
 * вплетает JFR begin/commit; {@link #disable(ProbeTarget)}/{@link #clear()} ретрансформируют снова из
 * кэшированных <em>исходных</em> байт, физически убирая зонд. На горячем пути нет рантайм-флага — у
 * незондированного метода кода зонда нет вовсе. Если агент недоступен, контроллер деградирует мягко:
 * активное множество всё равно питает {@code ProbeWeaver} времени загрузки.
 */
public final class DynamicProbeController {

    private static final DynamicProbeController INSTANCE = new DynamicProbeController();

    private final CopyOnWriteArrayList<ProbeTarget> active = new CopyOnWriteArrayList<>();
    private final Map<String, byte[]> originalBytes = new ConcurrentHashMap<>();
    private volatile Instrumentation instrumentation;
    private volatile ClassFileTransformer transformer;

    private DynamicProbeController() {
    }

    public static DynamicProbeController get() {
        return INSTANCE;
    }

    /** Active probe targets (snapshot). */
    public List<ProbeTarget> active() {
        return List.copyOf(active);
    }

    /** A {@link ProbeWeaver} bound to the current active set — also usable as a load-time transformer. */
    public ProbeWeaver loadTimeWeaver(int order) {
        return new ProbeWeaver(active(), order);
    }

    /** True if a retransform-capable agent is available, enabling instant (already-loaded) hot-swap. */
    public boolean instrumentationAvailable() {
        return acquire() != null;
    }

    /** Enable a probe. Returns true if it was hot-swapped into an already-loaded class immediately. */
    public boolean enable(ProbeTarget target) {
        if (!active.contains(target)) {
            active.add(target);
        }
        return retransform(target.classInternalName());
    }

    /** Disable a probe and strip its bytecode (if the class is loaded and an agent is available). */
    public boolean disable(ProbeTarget target) {
        active.remove(target);
        return retransform(target.classInternalName());
    }

    /** Disable every probe (strips all). */
    public void clear() {
        List<ProbeTarget> snapshot = active();
        active.clear();
        for (ProbeTarget t : snapshot) {
            retransform(t.classInternalName());
        }
    }

    /** A short human status line for the CLI. */
    public String status() {
        return "active probes=" + active.size()
                + ", instrumentation=" + (instrumentationAvailable() ? "live (instant hot-swap)" : "absent (next-load weaving)");
    }

    /** Re-transform an already-loaded class to reflect the current active set; false if not possible. */
    private boolean retransform(String internalName) {
        Instrumentation inst = acquire();
        if (inst == null) {
            return false;
        }
        ensureTransformerRegistered(inst);
        String binary = internalName.replace('/', '.');
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c.getName().equals(binary) && inst.isModifiableClass(c)) {
                try {
                    inst.retransformClasses(c);
                    return true;
                } catch (Throwable t) {
                    return false; // contained: never crash the host on a profiling toggle
                }
            }
        }
        return false; // class not loaded yet -> the load-time weaver will pick it up
    }

    private void ensureTransformerRegistered(Instrumentation inst) {
        if (transformer != null) {
            return;
        }
        synchronized (this) {
            if (transformer != null) {
                return;
            }
            ClassFileTransformer t = new ClassFileTransformer() {
                @Override
                public byte[] transform(ClassLoader loader, String internalName, Class<?> beingRedefined,
                                        ProtectionDomain pd, byte[] classfileBuffer) {
                    if (internalName == null) {
                        return null;
                    }
                    // Always weave from the cached ORIGINAL so disabling restores byte-for-byte.
                    byte[] original = originalBytes.computeIfAbsent(internalName, k -> classfileBuffer);
                    boolean targeted = active.stream().anyMatch(p -> p.classInternalName().equals(internalName));
                    if (!targeted) {
                        return original; // strip: return the pristine class (zero probe code)
                    }
                    BytecodeEngine engine = BytecodeEngine.builder()
                            .transformer(new ProbeWeaver(active(), 100))
                            .classLoader(loader != null ? loader : ClassLoader.getSystemClassLoader())
                            .build();
                    return engine.transformClass(original, new CollectingDiagnosticSink());
                }
            };
            inst.addTransformer(t, true);
            this.transformer = t;
        }
    }

    /** Best-effort: use an already-attached agent, else self-attach via the Attach API. Null if locked down. */
    private Instrumentation acquire() {
        Instrumentation existing = instrumentation;
        if (existing != null) {
            return existing;
        }
        if (AetheriumProbeAgent.INSTRUMENTATION != null) {
            instrumentation = AetheriumProbeAgent.INSTRUMENTATION;
            return instrumentation;
        }
        synchronized (this) {
            if (instrumentation != null) {
                return instrumentation;
            }
            instrumentation = SelfAttach.tryAcquire();
            return instrumentation;
        }
    }
}
