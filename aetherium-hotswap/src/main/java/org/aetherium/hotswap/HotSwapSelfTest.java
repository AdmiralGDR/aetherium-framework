/*
 * Aetherium Framework — live hot-swap engine self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap;

import org.aetherium.injector.LiveHookGraph;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end proof of the hot-swap engine and its live DAG reconciliation.
 *
 * <p>EN: Generates two versions of a tiny class with ASM ({@code currentValue()} returning {@code 1}
 * then {@code 2}), loads v1, invokes it (expects {@code 1}), redefines it live with v2 through
 * {@link HotSwapEngine}, and invokes again (expects {@code 2}) — a genuine
 * {@link java.lang.instrument.Instrumentation#redefineClasses} round-trip. It also wires a
 * {@link HotSwapListener} that re-resolves a {@link LiveHookGraph}, proving injected hooks are
 * re-ordered after a swap. On a JVM where self-attach is forbidden, the engine degrades to
 * {@link HotSwapResult.Status#NO_INSTRUMENTATION}; the DAG-reconciliation checks still run and pass.
 * RU: Генерирует две версии крошечного класса через ASM ({@code currentValue()} возвращает {@code 1},
 * затем {@code 2}), загружает v1, вызывает (ожидает {@code 1}), переопределяет вживую v2 через
 * {@link HotSwapEngine} и вызывает снова (ожидает {@code 2}) — настоящий round-trip
 * {@link java.lang.instrument.Instrumentation#redefineClasses}. Также подключается
 * {@link HotSwapListener}, заново разрешающий {@link LiveHookGraph}, доказывая переупорядочивание
 * хуков после свопа. На JVM, где self-attach запрещён, движок деградирует до
 * {@link HotSwapResult.Status#NO_INSTRUMENTATION}; проверки согласования DAG всё равно проходят.
 */
public final class HotSwapSelfTest {

    private static final String PROBE_BINARY = "org.aetherium.hotswap.gen.LiveProbe";
    private static final String PROBE_INTERNAL = "org/aetherium/hotswap/gen/LiveProbe";

    private HotSwapSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();
        HotSwapEngine engine = new HotSwapEngine();

        // --- live DAG reconciliation, wired to fire on every successful swap ---------------------
        LiveHookGraph graph = new LiveHookGraph();
        graph.register("core", ctx -> { })
                .register("render", ctx -> { }).runAfter("render", "core")
                .register("physics", ctx -> { }).runAfter("physics", "core");
        List<String> orderBefore = graph.resolve();
        notes.add("initial hook order: " + orderBefore);

        AtomicInteger listenerFires = new AtomicInteger();
        engine.onReload(className -> {
            // A redefined class contributes a new hook → re-resolve the live order.
            graph.register("lighting", ctx -> { }).runAfter("lighting", "render");
            listenerFires.incrementAndGet();
        });

        // --- generate v1, load it, invoke -------------------------------------------------------
        boolean valueBeforeOk = false;
        boolean redefineApplied = false;
        boolean valueAfterOk = false;
        Class<?> probe = null;
        try {
            ByteClassLoader loader = new ByteClassLoader();
            probe = loader.define(PROBE_BINARY, generateProbe(1));
            int v1 = invokeCurrentValue(probe);
            valueBeforeOk = v1 == 1;
            notes.add("loaded v1, currentValue() = " + v1);
        } catch (Throwable t) {
            notes.add("v1 load/invoke failed: " + t);
        }

        boolean instrumentationAvailable = engine.available();
        notes.add("live Instrumentation: " + (instrumentationAvailable ? "available (instant redefine)" : "absent (degrade)"));

        // --- redefine to v2 live ----------------------------------------------------------------
        if (probe != null) {
            HotSwapResult result = engine.redefine(probe, generateProbe(2));
            notes.add("redefine result: " + result.status() + " — " + result.detail());
            redefineApplied = result.redefined();
            if (redefineApplied) {
                try {
                    int v2 = invokeCurrentValue(probe);
                    valueAfterOk = v2 == 2;
                    notes.add("after swap, currentValue() = " + v2 + " (no restart)");
                } catch (Throwable t) {
                    notes.add("post-swap invoke failed: " + t);
                }
            }
        }

        // The listener re-resolves the DAG; capture the new order (live, post-swap).
        List<String> orderAfter = graph.resolve();
        notes.add("reconciled hook order: " + orderAfter);

        boolean dagReconciled = reconciliationValid(orderBefore, orderAfter);
        boolean listenerFired = listenerFires.get() > 0;

        boolean passed = valueBeforeOk && dagReconciled
                && (!instrumentationAvailable || (redefineApplied && valueAfterOk && listenerFired));

        return new Result(instrumentationAvailable, valueBeforeOk, redefineApplied, valueAfterOk,
                listenerFired, dagReconciled, orderBefore, orderAfter, notes, passed);
    }

    /** The base order must be deterministic and constraint-honoring (core first). */
    private static boolean reconciliationValid(List<String> before, List<String> after) {
        if (before.isEmpty() || !"core".equals(before.get(0))) {
            return false;
        }
        for (String id : new String[]{"render", "physics"}) {
            if (before.indexOf("core") > before.indexOf(id)) {
                return false;
            }
        }
        // After reconciliation: if a swap fired the listener, 'lighting' appears after 'render';
        // either way the graph still resolves deterministically with core first.
        if (after.contains("lighting") && after.indexOf("render") > after.indexOf("lighting")) {
            return false;
        }
        return !after.isEmpty() && "core".equals(after.get(0));
    }

    private static int invokeCurrentValue(Class<?> probe) throws ReflectiveOperationException {
        return (int) probe.getMethod("currentValue").invoke(null);
    }

    /** Emit a class {@code org.aetherium.hotswap.gen.LiveProbe} with {@code static int currentValue()}. */
    private static byte[] generateProbe(int value) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                PROBE_INTERNAL, null, "java/lang/Object", null);

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "currentValue", "()I", null, null);
        mv.visitCode();
        mv.visitLdcInsn(value);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Minimal in-memory loader so the self-test can define and then redefine a class. */
    private static final class ByteClassLoader extends ClassLoader {
        ByteClassLoader() {
            super(HotSwapSelfTest.class.getClassLoader());
        }

        Class<?> define(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }

    /** Outcome of the hot-swap self-test, rendered by the CLI {@code hotswap} command. */
    public record Result(boolean instrumentationAvailable, boolean valueBeforeOk, boolean redefineApplied,
                         boolean valueAfterOk, boolean listenerFired, boolean dagReconciled,
                         List<String> orderBefore, List<String> orderAfter,
                         List<String> notes, boolean passed) {
    }
}
