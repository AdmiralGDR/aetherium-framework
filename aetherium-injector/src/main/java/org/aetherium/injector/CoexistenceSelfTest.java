/*
 * Aetherium Framework — multi-mod injector coexistence self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import org.aetherium.bytecode.CollectingDiagnosticSink;
import org.aetherium.core.Diagnostic;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proves that <strong>two independently built injectors (two mods) coexist</strong> — the core of "several
 * Aetherium mods run together without conflict".
 *
 * <p>EN: Each {@link AetheriumInjector} used to allocate hook IDs from {@code 0}, and installing hooks
 * <em>replaced</em> the whole {@link HookTable}. So a second mod silently wiped the first: the first mod's
 * lowered {@code invokedynamic} either bound to the <em>wrong</em> hook or threw {@code BootstrapMethodError}
 * <em>inside a vanilla method</em>. This test builds mod&nbsp;A, then mod&nbsp;B (registration order matters),
 * transforms both, and invokes <em>A</em> — which would fail under the old design — asserting A's hook still
 * fires, B's hook fires, neither leaks into the other, and both return values are preserved. It is the
 * regression guard for the global, append-only hook-ID space.
 *
 * <p>RU: Раньше каждый инжектор выдавал ID хуков с нуля, а установка заменяла всю {@link HookTable} —
 * второй мод молча затирал первый (хук первого либо привязывался к чужому, либо бросал
 * {@code BootstrapMethodError} внутри ванильного метода). Тест строит мод&nbsp;A, затем мод&nbsp;B,
 * преобразует оба и вызывает именно A (что падало бы при старой схеме), проверяя, что хук A всё ещё
 * срабатывает, хук B срабатывает, они не перетекают друг в друга и оба значения сохранены.
 */
public final class CoexistenceSelfTest {

    private static final AtomicInteger MOD_A_CALLS = new AtomicInteger();
    private static final AtomicInteger MOD_B_CALLS = new AtomicInteger();

    private CoexistenceSelfTest() {
    }

    /** Structured outcome. */
    public record Result(boolean modAFired,
                         boolean modBFired,
                         boolean noCrossTalk,
                         boolean valuesPreserved,
                         int modAValue,
                         int modBValue,
                         List<String> notes,
                         List<Diagnostic> diagnostics) {
        public boolean passed() {
            return modAFired && modBFired && noCrossTalk && valuesPreserved;
        }
    }

    /** Mod A's injected hook (records its own side effect only). */
    public static void onModAHook() {
        MOD_A_CALLS.incrementAndGet();
    }

    /** Mod B's injected hook. */
    public static void onModBHook() {
        MOD_B_CALLS.incrementAndGet();
    }

    public static Result run() throws ReflectiveOperationException {
        List<String> notes = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        MOD_A_CALLS.set(0);
        MOD_B_CALLS.set(0);

        final String aInternal = "org/aetherium/injector/demo/ModATarget";
        final String aBinary = "org.aetherium.injector.demo.ModATarget";
        final String bInternal = "org/aetherium/injector/demo/ModBTarget";
        final String bBinary = "org.aetherium.injector.demo.ModBTarget";

        byte[] aOriginal = generateTarget(aInternal, 11);
        byte[] bOriginal = generateTarget(bInternal, 22);

        // Mod A registers its hook FIRST (claims the earlier global IDs) ...
        AetheriumInjector modA = AetheriumInjector.create()
                .inClass(aInternal)
                    .method("compute", "()I")
                        .findReturn()
                        .insertHookBefore(CoexistenceSelfTest::onModAHook)
                    .commit();
        // ... then mod B builds an entirely separate injector. Under the OLD design B's install() here
        // would clobber A's hook table. Under the global ID space, B simply gets the next IDs.
        AetheriumInjector modB = AetheriumInjector.create()
                .inClass(bInternal)
                    .method("compute", "()I")
                        .findReturn()
                        .insertHookBefore(CoexistenceSelfTest::onModBHook)
                    .commit();
        modA.installHooks();
        modB.installHooks();
        notes.add("built two independent injectors; global hook table size=" + HookTable.size());

        CollectingDiagnosticSink aSink = new CollectingDiagnosticSink();
        CollectingDiagnosticSink bSink = new CollectingDiagnosticSink();
        byte[] aTransformed = modA.transform(aOriginal, CoexistenceSelfTest.class.getClassLoader(), aSink);
        byte[] bTransformed = modB.transform(bOriginal, CoexistenceSelfTest.class.getClassLoader(), bSink);
        diagnostics.addAll(aSink.diagnostics());
        diagnostics.addAll(bSink.diagnostics());

        // Invoke MOD A FIRST — the case the old clobber bug broke. Its hook id must still resolve.
        int aValue = (int) new ByteClassLoader(CoexistenceSelfTest.class.getClassLoader())
                .define(aBinary, aTransformed).getMethod("compute").invoke(null);
        int aAfterA = MOD_A_CALLS.get();
        int bAfterA = MOD_B_CALLS.get();

        int bValue = (int) new ByteClassLoader(CoexistenceSelfTest.class.getClassLoader())
                .define(bBinary, bTransformed).getMethod("compute").invoke(null);
        int aAfterB = MOD_A_CALLS.get();
        int bAfterB = MOD_B_CALLS.get();

        boolean modAFired = aAfterA == 1;
        boolean modBFired = bAfterB == 1;
        // Cross-talk = A's hook fired when only B ran, or B's fired when only A ran.
        boolean noCrossTalk = bAfterA == 0 && aAfterB == aAfterA;
        boolean valuesPreserved = aValue == 11 && bValue == 22 && aSink.isEmpty() && bSink.isEmpty();

        notes.add("invoked A.compute()=" + aValue + " (expected 11) -> A fired " + aAfterA
                + "x, B fired " + bAfterA + "x");
        notes.add("invoked B.compute()=" + bValue + " (expected 22) -> A total " + aAfterB
                + "x, B total " + bAfterB + "x");
        notes.add("no cross-talk=" + noCrossTalk + ", values preserved=" + valuesPreserved
                + ", diagnostics=" + diagnostics.size());

        return new Result(modAFired, modBFired, noCrossTalk, valuesPreserved, aValue, bValue,
                notes, diagnostics);
    }

    /** Generate {@code public final class <internal> { static int compute() { return <value>; } }}. */
    private static byte[] generateTarget(String internalName, int value) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null,
                "java/lang/Object", null);

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        MethodVisitor compute = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "compute",
                "()I", null, null);
        compute.visitCode();
        compute.visitIntInsn(Opcodes.BIPUSH, value);
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
