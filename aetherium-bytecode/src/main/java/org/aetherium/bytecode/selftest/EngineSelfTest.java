package org.aetherium.bytecode.selftest;

import org.aetherium.bytecode.BytecodeEngine;
import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.CollectingDiagnosticSink;
import org.aetherium.bytecode.TransformResult;
import org.aetherium.bytecode.runtime.DispatchTable;
import org.aetherium.bytecode.transform.DispatchLoweringTransformer;
import org.aetherium.core.Diagnostic;
import org.aetherium.core.SymbolManifest;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An end-to-end, dependency-free self-test of the bytecode engine.
 *
 * <p>EN: Proves the whole pipeline without a running game or a test framework. It (1) builds a
 * symbol manifest, (2) installs a dispatch handle that doubles its input, (3) generates a dummy
 * class that calls an abstract API method {@code MockApi.compute(21)}, (4) runs the engine — which
 * lowers that call to {@code invokedynamic}, verifies, and emits new bytes, (5) loads and invokes
 * the transformed class and checks it returns {@code 42} (proving the call routed through the
 * dispatch table), and (6) runs a deliberately-throwing transformer to confirm the engine reverts
 * to the original bytes and logs a {@link Diagnostic} instead of crashing.
 *
 * <p>RU: Доказывает весь конвейер без запущенной игры и фреймворка тестов. Он (1) строит манифест
 * символов, (2) устанавливает дескриптор диспетчеризации, удваивающий вход, (3) генерирует
 * фиктивный класс, вызывающий абстрактный метод API {@code MockApi.compute(21)}, (4) запускает
 * движок — который понижает вызов до {@code invokedynamic}, верифицирует и выдаёт новые байты,
 * (5) загружает и вызывает преобразованный класс и проверяет, что он возвращает {@code 42}
 * (доказывая маршрутизацию через таблицу диспетчеризации), и (6) запускает намеренно бросающий
 * трансформер, чтобы подтвердить откат к исходным байтам и логирование {@link Diagnostic} вместо
 * краха.
 */
public final class EngineSelfTest {

    private static final String DEMO_INTERNAL = "org/aetherium/demo/Demo";
    private static final String DEMO_BINARY = "org.aetherium.demo.Demo";
    private static final String MOCK_API_INTERNAL = "org/aetherium/demo/MockApi";
    private static final String NAMESPACE = "demo";

    private EngineSelfTest() {
    }

    /** Structured outcome of the self-test. */
    public record Result(boolean dispatchLoweringOk,
                         int observedValue,
                         boolean fallbackOk,
                         List<String> notes,
                         List<Diagnostic> diagnostics) {
        public boolean passed() {
            return dispatchLoweringOk && fallbackOk;
        }
    }

    /** The dispatch target the lowered call site is expected to reach: {@code x -> x * 2}. */
    public static int doubler(int x) {
        return x * 2;
    }

    public static Result run() throws ReflectiveOperationException {
        List<String> notes = new ArrayList<>();

        // (1) Manifest: one symbol "demo:compute" with id 0.
        SymbolManifest manifest = SymbolManifest.builder()
                .add(NAMESPACE, "compute", "(I)I")
                .build();
        notes.add("manifest: 1 symbol, demo:compute -> id " + manifest.idOf("demo:compute").getAsInt());

        // (2) Install the dispatch table: handles[0] = doubler.
        MethodHandle doubler = MethodHandles.lookup()
                .findStatic(EngineSelfTest.class, "doubler", MethodType.methodType(int.class, int.class));
        DispatchTable.install(new MethodHandle[]{doubler});
        notes.add("dispatch table installed: size " + DispatchTable.size());

        // (3) Generate the dummy class that calls the abstract API.
        byte[] original = generateDemoClass();
        notes.add("generated dummy class: " + original.length + " bytes");

        // (4) Engine with the dispatch-lowering transformer + a benign no-op transformer.
        CollectingDiagnosticSink sink = new CollectingDiagnosticSink();
        BytecodeEngine engine = BytecodeEngine.builder()
                .manifest(manifest)
                .transformer(new NoOpTransformer(10))
                .transformer(new DispatchLoweringTransformer(MOCK_API_INTERNAL, NAMESPACE, manifest, 100))
                .classLoader(EngineSelfTest.class.getClassLoader())
                .build();
        byte[] transformed = engine.transformClass(original, sink);
        boolean changed = !Arrays.equals(original, transformed);
        notes.add("transform produced " + transformed.length + " bytes (changed=" + changed + ")");

        // (5) Load and invoke; expect doubler(21) == 42.
        ByteClassLoader loader = new ByteClassLoader(EngineSelfTest.class.getClassLoader());
        Class<?> demo = loader.define(DEMO_BINARY, transformed);
        int observed = (int) demo.getMethod("run").invoke(null);
        boolean dispatchOk = changed && observed == 42 && sink.isEmpty();
        notes.add("invoked Demo.run() = " + observed + " (expected 42); transform diagnostics=" + sink.count());

        // (6) Fallback: a transformer that throws must revert to original bytes + log a diagnostic.
        CollectingDiagnosticSink failSink = new CollectingDiagnosticSink();
        BytecodeEngine failingEngine = BytecodeEngine.builder()
                .manifest(manifest)
                .transformer(new ThrowingTransformer(100))
                .classLoader(EngineSelfTest.class.getClassLoader())
                .build();
        byte[] afterFailure = failingEngine.transformClass(original, failSink);
        boolean fallbackOk = Arrays.equals(original, afterFailure) && !failSink.isEmpty();
        notes.add("fallback: reverted-to-original=" + Arrays.equals(original, afterFailure)
                + ", diagnostics=" + failSink.count());

        List<Diagnostic> allDiagnostics = new ArrayList<>(sink.diagnostics());
        allDiagnostics.addAll(failSink.diagnostics());
        return new Result(dispatchOk, observed, fallbackOk, List.copyOf(notes), List.copyOf(allDiagnostics));
    }

    /**
     * Generates: {@code public final class Demo { public static int run() { return MockApi.compute(21); } }}
     * where {@code MockApi.compute} is an {@code INVOKESTATIC} to a (non-existent) abstract API owner.
     * The owner need not exist: the dispatch-lowering transform removes the reference before the
     * class is ever loaded.
     */
    private static byte[] generateDemoClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, DEMO_INTERNAL, null, "java/lang/Object", null);

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()I", null, null);
        run.visitCode();
        run.visitIntInsn(Opcodes.BIPUSH, 21);
        run.visitMethodInsn(Opcodes.INVOKESTATIC, MOCK_API_INTERNAL, "compute", "(I)I", false);
        run.visitInsn(Opcodes.IRETURN);
        run.visitMaxs(1, 0);
        run.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** A transformer that touches nothing — proves benign transformers compose cleanly. */
    private record NoOpTransformer(int order) implements ClassTransformer {
        @Override
        public boolean handles(ClassContext context) {
            return false;
        }

        @Override
        public TransformResult apply(ClassContext context) {
            return new TransformResult.Skipped("no-op");
        }
    }

    /** A transformer that always throws — exercises the engine's revert-to-original safety net. */
    private record ThrowingTransformer(int order) implements ClassTransformer {
        @Override
        public boolean handles(ClassContext context) {
            return true;
        }

        @Override
        public TransformResult apply(ClassContext context) {
            throw new IllegalStateException("intentional self-test failure");
        }
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
