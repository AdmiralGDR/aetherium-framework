/*
 * Aetherium Framework — injector end-to-end self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import org.aetherium.bytecode.CollectingDiagnosticSink;
import org.aetherium.core.Diagnostic;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dependency-free, end-to-end self-test proving the fluent injector and its safety net.
 *
 * <p>EN: Without a running game or a test framework it (1) generates a mock target
 * {@code int compute()} returning 21; (2) uses the fluent cursor to inject a hook before the return,
 * lowered to {@code invokedynamic}; (3) loads and invokes the transformed class, asserting the hook
 * fired <em>and</em> the value is still 21 (the hook routed through the {@link HookTable}); then it
 * proves <strong>absolute safety</strong> with two failure cases that must both REVERT to the original
 * vanilla bytes and emit a structured {@link Diagnostic} without crashing: (4) an injection that
 * produces invalid bytecode (stack underflow → caught by the verification sandbox), and (5) a cursor
 * navigation that cannot be satisfied ({@code findOpcode} miss).
 *
 * <p>RU: Без запущенной игры и фреймворка тестов: (1) генерирует мок-цель {@code int compute()},
 * возвращающую 21; (2) текучим курсором внедряет хук перед возвратом, понижая до
 * {@code invokedynamic}; (3) загружает и вызывает преобразованный класс, проверяя, что хук сработал
 * <em>и</em> значение по-прежнему 21; затем доказывает <strong>абсолютную безопасность</strong> двумя
 * случаями отказа, которые обязаны откатиться к исходным байтам и выдать структурированный
 * {@link Diagnostic} без краха: (4) инъекция с невалидным байт-кодом (переполнение стека → ловится
 * песочницей) и (5) неосуществимая навигация курсора.
 */
public final class InjectorSelfTest {

    private static final String MOCK_INTERNAL = "org/aetherium/injector/demo/MockTarget";
    private static final String MOCK_BINARY = "org.aetherium.injector.demo.MockTarget";

    private static final AtomicInteger HOOK_CALLS = new AtomicInteger();

    private InjectorSelfTest() {
    }

    /** Structured outcome. */
    public record Result(boolean injectionOk,
                         int observedValue,
                         int hookCalls,
                         boolean revertOnInvalidBytecode,
                         boolean revertOnCursorMiss,
                         boolean cancellationOk,
                         int cancelledValue,
                         boolean argReadOk,
                         int observedArg,
                         boolean dagOrderOk,
                         List<String> dagOrder,
                         boolean mergedDoubleCancelOk,
                         int mergedCancelValue,
                         boolean cycleDetected,
                         List<String> notes,
                         List<Diagnostic> diagnostics) {
        public boolean passed() {
            return injectionOk && revertOnInvalidBytecode && revertOnCursorMiss
                    && cancellationOk && argReadOk
                    && dagOrderOk && mergedDoubleCancelOk && cycleDetected;
        }
    }

    /** Records the order in which merged hooks actually executed at runtime (proves the DAG order). */
    private static final java.util.List<String> MERGE_TRACE = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** The injected hook target (observable side effect). */
    public static void onHook() {
        HOOK_CALLS.incrementAndGet();
    }

    /** Captures the argument a context hook observed (proof that {@code this}/args reach the hook). */
    private static final AtomicInteger OBSERVED_ARG = new AtomicInteger(Integer.MIN_VALUE);

    public static Result run() throws ReflectiveOperationException {
        List<String> notes = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        HOOK_CALLS.set(0);

        byte[] original = generateMockTarget();
        notes.add("generated mock target: " + original.length + " bytes (compute() -> 21)");

        // (1)(2) Positive: inject a hook before the return via the fluent cursor.
        AetheriumInjector injector = AetheriumInjector.create()
                .inClass(MOCK_INTERNAL)
                    .method("compute", "()I")
                        .findReturn()
                        .insertHookBefore(InjectorSelfTest::onHook)   // -> O(1) invokedynamic
                    .commit();
        int installed = injector.installHooks();
        notes.add("fluent rule registered (" + injector.rules().size() + "); hooks installed=" + installed);

        CollectingDiagnosticSink okSink = new CollectingDiagnosticSink();
        byte[] transformed = injector.transform(original, InjectorSelfTest.class.getClassLoader(), okSink);
        boolean changed = !Arrays.equals(original, transformed);
        notes.add("transform produced " + transformed.length + " bytes (changed=" + changed
                + ", diagnostics=" + okSink.count() + ")");

        ByteClassLoader loader = new ByteClassLoader(InjectorSelfTest.class.getClassLoader());
        Class<?> mock = loader.define(MOCK_BINARY, transformed);
        int observed = (int) mock.getMethod("compute").invoke(null);
        int calls = HOOK_CALLS.get();
        boolean injectionOk = changed && observed == 21 && calls == 1 && okSink.isEmpty();
        notes.add("invoked compute() = " + observed + " (expected 21); hook fired " + calls + " time(s)");

        // (4) Negative A: an injection that yields invalid bytecode must revert to the original.
        InsnList underflow = new InsnList();
        underflow.add(new InsnNode(Opcodes.POP)); // POP on an empty stack -> verification failure
        AetheriumInjector badBytecode = AetheriumInjector.create()
                .inClass(MOCK_INTERNAL)
                    .method("compute", "()I")
                        .toStart()
                        .insertBefore(underflow)
                    .commit();
        CollectingDiagnosticSink badSink = new CollectingDiagnosticSink();
        byte[] afterBad = badBytecode.transform(original, InjectorSelfTest.class.getClassLoader(), badSink);
        boolean revertedBad = Arrays.equals(original, afterBad) && !badSink.isEmpty();
        // The reverted class must still load and behave exactly like vanilla.
        int vanilla = (int) new ByteClassLoader(InjectorSelfTest.class.getClassLoader())
                .define(MOCK_BINARY, afterBad).getMethod("compute").invoke(null);
        revertedBad = revertedBad && vanilla == 21;
        diagnostics.addAll(badSink.diagnostics());
        notes.add("invalid-bytecode injection: reverted-to-original=" + Arrays.equals(original, afterBad)
                + ", diagnostics=" + badSink.count() + ", reverted class compute()=" + vanilla);

        // (5) Negative B: a cursor navigation that cannot be satisfied must revert too.
        AetheriumInjector badCursor = AetheriumInjector.create()
                .inClass(MOCK_INTERNAL)
                    .method("compute", "()I")
                        .findOpcode(Opcodes.MONITORENTER) // never present -> CursorException
                        .insertHookBefore(InjectorSelfTest::onHook)
                    .commit();
        CollectingDiagnosticSink missSink = new CollectingDiagnosticSink();
        byte[] afterMiss = badCursor.transform(original, InjectorSelfTest.class.getClassLoader(), missSink);
        boolean revertedMiss = Arrays.equals(original, afterMiss) && !missSink.isEmpty();
        diagnostics.addAll(missSink.diagnostics());
        notes.add("cursor-miss injection: reverted-to-original=" + Arrays.equals(original, afterMiss)
                + ", diagnostics=" + missSink.count());

        // (6) Cancellation: inject a context hook at HEAD of compute() that cancels returning 99. The
        //     vanilla body (return 21) must be bypassed entirely -> compute() now returns 99.
        AetheriumInjector cancelling = AetheriumInjector.create()
                .inClass(MOCK_INTERNAL)
                    .method("compute", "()I")
                        .toStart()
                        .insertContextHookBefore(ctx -> ctx.cancel(99))   // -> O(1) invokedynamic + frame-correct IRETURN
                    .commit();
        cancelling.installHooks();
        CollectingDiagnosticSink cancelSink = new CollectingDiagnosticSink();
        byte[] cancelled = cancelling.transform(original, InjectorSelfTest.class.getClassLoader(), cancelSink);
        int cancelObserved = (int) new ByteClassLoader(InjectorSelfTest.class.getClassLoader())
                .define(MOCK_BINARY, cancelled).getMethod("compute").invoke(null);
        boolean cancellationOk = cancelObserved == 99 && cancelSink.isEmpty();
        notes.add("cancellation injection: compute() = " + cancelObserved + " (expected 99 — vanilla 21 bypassed)"
                + ", diagnostics=" + cancelSink.count());

        // (7) Argument read + value cancel: inject a context hook (capturing args) at HEAD of
        //     doubleIt(int) that reads arg(0) and cancels returning arg0 + 5. doubleIt(10) would
        //     normally return 20; cancelled it returns 15, proving the hook saw the real argument.
        OBSERVED_ARG.set(Integer.MIN_VALUE);
        AetheriumInjector argReading = AetheriumInjector.create()
                .inClass(MOCK_INTERNAL)
                    .method("doubleIt", "(I)I")
                        .toStart()
                        .insertContextHookBefore(InjectorSelfTest::onDoubleIt, true)  // capture arguments
                    .commit();
        argReading.installHooks();
        CollectingDiagnosticSink argSink = new CollectingDiagnosticSink();
        byte[] argInjected = argReading.transform(original, InjectorSelfTest.class.getClassLoader(), argSink);
        int argObserved = (int) new ByteClassLoader(InjectorSelfTest.class.getClassLoader())
                .define(MOCK_BINARY, argInjected).getMethod("doubleIt", int.class).invoke(null, 10);
        int seenArg = OBSERVED_ARG.get();
        boolean argReadOk = seenArg == 10 && argObserved == 15 && argSink.isEmpty();
        notes.add("arg-read + value-cancel injection: hook saw arg0=" + seenArg + " (expected 10), doubleIt(10) = "
                + argObserved + " (expected 15 — not vanilla 20), diagnostics=" + argSink.count());

        // (8) DAG ordering + ASM Semantic Merger (double-cancel resolution). Two hooks BOTH cancel the
        //     same method, declared in REVERSE order with a runAfter constraint. The DAG must reorder
        //     them to [mod_a, mod_b]; the merger runs BOTH against one shared context (mod_b observes
        //     mod_a's cancellation value) and applies a single, deterministic cancellation epilogue.
        MERGE_TRACE.clear();
        MergedHookBuilder mergeBuilder = AetheriumInjector.create()
                .inClass(MOCK_INTERNAL)
                    .method("merged", "(I)I")
                        .at(InjectionAnchor.HEAD)
                        .captureArguments()
                        // declared mod_b FIRST, but it must run AFTER mod_a:
                        .hook("mod_b", InjectorSelfTest::mergeB).runAfter("mod_a")
                        .hook("mod_a", InjectorSelfTest::mergeA);
        List<String> dagOrder = mergeBuilder.resolvedOrder();
        boolean dagOrderOk = dagOrder.equals(List.of("mod_a", "mod_b"));
        AetheriumInjector merging = mergeBuilder.commit();
        merging.installHooks();
        notes.add("DAG resolved order (declared [mod_b, mod_a] + runAfter) = " + dagOrder
                + " -> " + (dagOrderOk ? "OK" : "WRONG"));

        CollectingDiagnosticSink mergeSink = new CollectingDiagnosticSink();
        byte[] merged = merging.transform(original, InjectorSelfTest.class.getClassLoader(), mergeSink);
        int mergedObserved = (int) new ByteClassLoader(InjectorSelfTest.class.getClassLoader())
                .define(MOCK_BINARY, merged).getMethod("merged", int.class).invoke(null, 123);
        // mergeA cancel(7); mergeB observes 7 in the shared context and cancels(7+2)=9. Both ran.
        boolean mergedDoubleCancelOk = mergedObserved == 9
                && MERGE_TRACE.equals(List.of("mod_a", "mod_b"))
                && mergeSink.isEmpty();
        notes.add("double-cancel merge: merged(123) = " + mergedObserved + " (expected 9), runtime hook trace="
                + MERGE_TRACE + ", diagnostics=" + mergeSink.count());

        // (9) Cycle detection: an impossible runBefore/runAfter pair must be caught at commit() time.
        boolean cycleDetected = false;
        try {
            AetheriumInjector.create()
                    .inClass(MOCK_INTERNAL)
                        .method("merged", "(I)I")
                            .at(InjectionAnchor.HEAD)
                            .hook("a", c -> { }).runAfter("b")
                            .hook("b", c -> { }).runAfter("a")
                        .commit();
        } catch (HookCycleException expected) {
            cycleDetected = true;
        }
        notes.add("DAG cycle detection (a<->b): " + (cycleDetected ? "caught HookCycleException" : "NOT caught"));

        return new Result(injectionOk, observed, calls, revertedBad, revertedMiss,
                cancellationOk, cancelObserved, argReadOk, seenArg,
                dagOrderOk, List.copyOf(dagOrder), mergedDoubleCancelOk, mergedObserved, cycleDetected,
                List.copyOf(notes), List.copyOf(diagnostics));
    }

    /** Context hook for case (7): records the observed argument and cancels with {@code arg0 + 5}. */
    public static void onDoubleIt(HookContext ctx) {
        int arg0 = (Integer) ctx.arg(0);
        OBSERVED_ARG.set(arg0);
        ctx.cancel(arg0 + 5);
    }

    /** Merge demo hook A (runs first per DAG): unconditionally cancels with 7. */
    public static void mergeA(HookContext ctx) {
        MERGE_TRACE.add("mod_a");
        ctx.cancel(7);
    }

    /** Merge demo hook B (runs after A): reads A's cancellation value from the shared context and
     *  combines it (prev + 2). With naive per-hook lowering this would never run — the merger lets it. */
    public static void mergeB(HookContext ctx) {
        MERGE_TRACE.add("mod_b");
        int prev = ctx.isCancelled() ? (Integer) ctx.returnValue() : 0;
        ctx.cancel(prev + 2);
    }

    /** {@code public final class MockTarget { public static int compute() { return 21; } }} */
    private static byte[] generateMockTarget() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, MOCK_INTERNAL, null, "java/lang/Object", null);

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        MethodVisitor compute = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "compute", "()I", null, null);
        compute.visitCode();
        compute.visitIntInsn(Opcodes.BIPUSH, 21);
        compute.visitInsn(Opcodes.IRETURN);
        compute.visitMaxs(1, 0);
        compute.visitEnd();

        // static int doubleIt(int x) { return x * 2; } -> 20 for input 10, unless an injection cancels.
        MethodVisitor doubleIt = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "doubleIt", "(I)I", null, null);
        doubleIt.visitCode();
        doubleIt.visitVarInsn(Opcodes.ILOAD, 0);
        doubleIt.visitInsn(Opcodes.ICONST_2);
        doubleIt.visitInsn(Opcodes.IMUL);
        doubleIt.visitInsn(Opcodes.IRETURN);
        doubleIt.visitMaxs(2, 1);
        doubleIt.visitEnd();

        // static int merged(int x) { return x; } -> the DAG/merge double-cancel target.
        MethodVisitor merged = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "merged", "(I)I", null, null);
        merged.visitCode();
        merged.visitVarInsn(Opcodes.ILOAD, 0);
        merged.visitInsn(Opcodes.IRETURN);
        merged.visitMaxs(1, 1);
        merged.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Minimal class loader that defines classes from raw bytes. */
    private static final class ByteClassLoader extends ClassLoader {
        ByteClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }
}
