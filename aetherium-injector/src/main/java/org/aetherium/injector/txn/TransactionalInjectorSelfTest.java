/*
 * Aetherium Framework — transactional (ACID Atomicity) injector self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.txn;

import org.aetherium.core.Diagnostic;
import org.aetherium.injector.AetheriumInjector;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dependency-free, end-to-end proof of ACID Atomicity for mod hooks.
 *
 * <p>EN: It builds one mod, {@code gravity_plus}, whose single injector declares <strong>three</strong>
 * hooks across three classes — the first two ({@code MockA}, {@code MockB}) are valid, the third
 * ({@code MockC}) injects stack-underflow bytecode that the verification sandbox rejects. Applying the
 * transaction proves the framework's central Atomicity claim: hooks 1 and 2 verify cleanly, then hook 3
 * fails, so <em>every</em> edit of that mod is rolled back — the published class table contains nothing
 * from {@code gravity_plus}, and loading the (vanilla) {@code MockA}/{@code MockB} fires no hook. To
 * prove the rollback is <strong>graceful</strong> (the JVM is not crashed; only the offending mod is
 * disabled), a second, healthy mod {@code speed_mod} is applied in the same pass and commits normally —
 * its class loads and its hook fires.
 *
 * <p>RU: Строится один мод {@code gravity_plus}, чей единственный инжектор объявляет <strong>три</strong>
 * хука в трёх классах — первые два ({@code MockA}, {@code MockB}) валидны, третий ({@code MockC})
 * внедряет байткод с переполнением стека, отвергаемый песочницей. Применение транзакции доказывает
 * ключевое свойство атомарности: хуки 1 и 2 проходят верификацию, затем хук 3 падает, поэтому
 * <em>каждая</em> правка этого мода откатывается — опубликованная таблица классов не содержит ничего от
 * {@code gravity_plus}, а загрузка (ванильных) {@code MockA}/{@code MockB} не вызывает ни одного хука.
 * Чтобы доказать мягкость отката (JVM не падает, отключается лишь виновный мод), в том же проходе
 * применяется здоровый мод {@code speed_mod} и коммитится штатно.
 */
public final class TransactionalInjectorSelfTest {

    private static final String PKG = "org/aetherium/injector/txn/demo/";
    private static final String A_INT = PKG + "MockA";
    private static final String B_INT = PKG + "MockB";
    private static final String C_INT = PKG + "MockC";
    private static final String D_INT = PKG + "MockD";
    private static final String A_BIN = A_INT.replace('/', '.');
    private static final String B_BIN = B_INT.replace('/', '.');
    private static final String C_BIN = C_INT.replace('/', '.');
    private static final String D_BIN = D_INT.replace('/', '.');

    static final AtomicInteger HOOK_A = new AtomicInteger();
    static final AtomicInteger HOOK_B = new AtomicInteger();
    static final AtomicInteger HOOK_D = new AtomicInteger();

    private TransactionalInjectorSelfTest() {
    }

    public static void onA() {
        HOOK_A.incrementAndGet();
    }

    public static void onB() {
        HOOK_B.incrementAndGet();
    }

    public static void onD() {
        HOOK_D.incrementAndGet();
    }

    /** Structured outcome for the CLI/JUnit. */
    public record Result(boolean gravityRolledBack,
                         int appliedBeforeAbort,
                         String failedClass,
                         boolean gravityPublishedNothing,
                         boolean rolledBackHooksInert,
                         boolean speedCommitted,
                         boolean healthyModRuns,
                         int healthyValue,
                         List<String> transactionLog,
                         List<String> notes,
                         List<Diagnostic> diagnostics) {
        public boolean passed() {
            return gravityRolledBack
                    && appliedBeforeAbort == 2
                    && failedClass != null && failedClass.endsWith("MockC")
                    && gravityPublishedNothing
                    && rolledBackHooksInert
                    && speedCommitted
                    && healthyModRuns
                    && healthyValue == 21;
        }
    }

    public static Result run() throws ReflectiveOperationException {
        HOOK_A.set(0);
        HOOK_B.set(0);
        HOOK_D.set(0);
        List<String> notes = new ArrayList<>();

        byte[] vanillaA = computeClass(A_INT);
        byte[] vanillaB = computeClass(B_INT);
        byte[] vanillaC = computeClass(C_INT);
        byte[] vanillaD = computeClass(D_INT);
        notes.add("generated 4 mock targets (each static compute() -> 21)");

        // The broken mod: hooks 1 & 2 valid, hook 3 injects POP-on-empty-stack (verification failure).
        InsnList underflow = new InsnList();
        underflow.add(new InsnNode(Opcodes.POP));
        AetheriumInjector gravity = AetheriumInjector.create()
                .inClass(A_INT).method("compute", "()I").findReturn()
                    .insertHookBefore(TransactionalInjectorSelfTest::onA).commit()
                .inClass(B_INT).method("compute", "()I").findReturn()
                    .insertHookBefore(TransactionalInjectorSelfTest::onB).commit()
                .inClass(C_INT).method("compute", "()I").toStart()
                    .insertBefore(underflow).commit();
        notes.add("mod 'gravity_plus' declares 3 hooks: MockA(ok), MockB(ok), MockC(bad bytecode)");

        // The healthy mod applied alongside it.
        AetheriumInjector speed = AetheriumInjector.create()
                .inClass(D_INT).method("compute", "()I").findReturn()
                    .insertHookBefore(TransactionalInjectorSelfTest::onD).commit();

        ClassLoader verify = TransactionalInjectorSelfTest.class.getClassLoader();
        TransactionalInjector engine = TransactionalInjector.create(verify)
                .mod("gravity_plus", gravity, List.of(
                        new TargetClass(A_BIN, vanillaA),
                        new TargetClass(B_BIN, vanillaB),
                        new TargetClass(C_BIN, vanillaC)))
                .mod("speed_mod", speed, new TargetClass(D_BIN, vanillaD));

        EngineReport report = engine.apply();

        TransactionResult gravityResult = report.results().get("gravity_plus");
        TransactionResult speedResult = report.results().get("speed_mod");

        boolean gravityRolledBack = gravityResult.rolledBack();
        int applied = gravityResult.appliedBeforeAbort();
        String failedClass = gravityResult.failedClass();
        notes.add("gravity_plus -> " + gravityResult.status()
                + " (verified " + applied + " of 3 before abort at " + failedClass + ")");

        // Atomicity: NONE of the broken mod's classes were published (not even the two that verified).
        boolean gravityPublishedNothing = report.published(A_BIN).isEmpty()
                && report.published(B_BIN).isEmpty()
                && report.published(C_BIN).isEmpty()
                && gravityResult.committedBytes().isEmpty();
        notes.add("published table contains gravity_plus classes? "
                + (gravityPublishedNothing ? "no (correct — full rollback)" : "YES (BUG)"));

        // Prove the two rolled-back hooks are truly discarded: the effective classes are still vanilla,
        // so loading + running them fires no hook (had A/B stayed transformed, the counters would tick).
        DemoLoader loader = new DemoLoader(verify);
        int aVal = (int) loader.define(A_BIN, effective(report, A_BIN, vanillaA)).getMethod("compute").invoke(null);
        int bVal = (int) loader.define(B_BIN, effective(report, B_BIN, vanillaB)).getMethod("compute").invoke(null);
        boolean rolledBackHooksInert = aVal == 21 && bVal == 21
                && HOOK_A.get() == 0 && HOOK_B.get() == 0;
        notes.add("rolled-back MockA/MockB run vanilla: compute()=" + aVal + "/" + bVal
                + ", hookA/hookB fired " + HOOK_A.get() + "/" + HOOK_B.get() + " time(s) (expected 0/0)");

        // Availability: the healthy mod committed and actually runs despite the neighbour's failure.
        boolean speedCommitted = speedResult.committed() && report.published(D_BIN).isPresent();
        int dVal = (int) loader.define(D_BIN, effective(report, D_BIN, vanillaD)).getMethod("compute").invoke(null);
        boolean healthyModRuns = HOOK_D.get() == 1;
        notes.add("speed_mod -> " + speedResult.status() + "; MockD compute()=" + dVal
                + ", hookD fired " + HOOK_D.get() + " time(s) (expected 1)");
        notes.add("JVM alive after a mod failed verification: yes (graceful disable, no crash)");

        List<Diagnostic> diagnostics = new ArrayList<>(gravityResult.diagnostics());

        return new Result(gravityRolledBack, applied, failedClass, gravityPublishedNothing,
                rolledBackHooksInert, speedCommitted, healthyModRuns, dVal,
                List.copyOf(gravityResult.log()), List.copyOf(notes), List.copyOf(diagnostics));
    }

    /** The effective class bytes after the transaction: the published transform, or vanilla fallback. */
    private static byte[] effective(EngineReport report, String binaryName, byte[] vanilla) {
        return report.published(binaryName).orElse(vanilla);
    }

    /** {@code public final class X { public static int compute() { return 21; } }} */
    private static byte[] computeClass(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

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
    private static final class DemoLoader extends ClassLoader {
        DemoLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }
}
