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
                         List<String> notes,
                         List<Diagnostic> diagnostics) {
        public boolean passed() {
            return injectionOk && revertOnInvalidBytecode && revertOnCursorMiss;
        }
    }

    /** The injected hook target (observable side effect). */
    public static void onHook() {
        HOOK_CALLS.incrementAndGet();
    }

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

        return new Result(injectionOk, observed, calls, revertedBad, revertedMiss,
                List.copyOf(notes), List.copyOf(diagnostics));
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
