/*
 * Aetherium Framework — hook contract-verification self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.contract;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * A dependency-free proof of ACID Consistency: static contract verification of hook return values.
 *
 * <p>EN: It generates a class of {@code @Ensures}-annotated hooks and runs the {@link ContractAnalyzer}
 * over the compiled bytecode. It proves the analyzer (1) passes a hook proven to satisfy its contract
 * ({@code return 5} under {@code NON_NEGATIVE}); (2) <strong>warns</strong> on one proven to violate it
 * ({@code return -5} — an {@code ICONST_5/INEG} the symbolic sign interpreter follows); (3) flags a
 * subtler {@code POSITIVE} contract that a {@code return 0} breaks; and (4) reports a variable return it
 * cannot pin down as merely unverified rather than a false alarm. No hook is executed.
 *
 * <p>RU: Генерирует класс хуков с {@code @Ensures} и запускает {@link ContractAnalyzer} по байткоду.
 * Доказывает, что анализатор: (1) пропускает хук, доказуемо соблюдающий контракт ({@code return 5} при
 * {@code NON_NEGATIVE}); (2) <strong>предупреждает</strong> о доказуемо нарушающем ({@code return -5} —
 * {@code ICONST_5/INEG}, отслеживаемый знаковым интерпретатором); (3) отмечает тонкий контракт
 * {@code POSITIVE}, ломаемый {@code return 0}; (4) сообщает о непрослеживаемом возврате как «не
 * проверено», а не как ложная тревога. Ни один хук не исполняется.
 */
public final class ContractSelfTest {

    private static final String ENSURES = "Lorg/aetherium/injector/contract/Ensures;";
    private static final String REQUIRES = "Lorg/aetherium/injector/contract/Requires;";
    private static final String CONSTRAINT = "Lorg/aetherium/injector/contract/Constraint;";
    private static final String INTERNAL = "org/aetherium/cli/contract/demo/HookContracts";

    private ContractSelfTest() {
    }

    public record Result(int methodsChecked,
                         long violations,
                         long unverified,
                         boolean goodSatisfied,
                         boolean negativeViolated,
                         boolean zeroUnderPositiveViolated,
                         boolean variableUnverified,
                         boolean requiresParsed,
                         List<String> verdictLines,
                         List<String> notes) {
        public boolean passed() {
            return goodSatisfied && negativeViolated && zeroUnderPositiveViolated
                    && variableUnverified && requiresParsed && violations == 2;
        }
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();
        byte[] bytes = generate();
        notes.add("generated hook class with 5 @Ensures/@Requires-annotated methods (" + bytes.length + " bytes)");

        ContractAnalyzer.Report report = ContractAnalyzer.analyze(bytes);
        List<String> lines = new ArrayList<>();
        boolean goodSatisfied = false;
        boolean negativeViolated = false;
        boolean zeroUnderPositiveViolated = false;
        boolean variableUnverified = false;
        boolean requiresParsed = false;

        for (ContractAnalyzer.MethodContract c : report.contracts()) {
            lines.add(String.format("%-22s @Ensures(%s) -> %s : %s",
                    c.methodName() + c.methodDesc(),
                    c.ensures(), c.verdict(), c.message()));
            switch (c.methodName()) {
                case "lightLevel" -> goodSatisfied = c.verdict() == ContractAnalyzer.Verdict.SATISFIED;
                case "brokenLightLevel" -> negativeViolated = c.verdict() == ContractAnalyzer.Verdict.VIOLATED;
                case "mustBePositive" -> zeroUnderPositiveViolated = c.verdict() == ContractAnalyzer.Verdict.VIOLATED;
                case "fromWorld" -> variableUnverified = c.verdict() == ContractAnalyzer.Verdict.UNVERIFIED;
                case "indexed" -> requiresParsed = !c.requires().isEmpty()
                        && c.requires().get(0).param() == 0;
                default -> { }
            }
        }

        notes.add("analyzer: " + report.contracts().size() + " contracts, "
                + report.violations() + " violation(s), " + report.unverified() + " unverified");

        return new Result(report.contracts().size(), report.violations(), report.unverified(),
                goodSatisfied, negativeViolated, zeroUnderPositiveViolated, variableUnverified,
                requiresParsed, List.copyOf(lines), List.copyOf(notes));
    }

    private static byte[] generate() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, INTERNAL, null, "java/lang/Object", null);

        // int lightLevel() { return 5; }  @Ensures(NON_NEGATIVE) -> SATISFIED
        constIntHook(cw, "lightLevel", "NON_NEGATIVE", mv -> {
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitInsn(Opcodes.IRETURN);
        });

        // int brokenLightLevel() { return -5; }  @Ensures(NON_NEGATIVE) -> VIOLATED (ICONST_5, INEG)
        constIntHook(cw, "brokenLightLevel", "NON_NEGATIVE", mv -> {
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitInsn(Opcodes.INEG);
            mv.visitInsn(Opcodes.IRETURN);
        });

        // int mustBePositive() { return 0; }  @Ensures(POSITIVE) -> VIOLATED (zero is not > 0)
        constIntHook(cw, "mustBePositive", "POSITIVE", mv -> {
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitInsn(Opcodes.IRETURN);
        });

        // int fromWorld(int) { return arg0; }  @Ensures(NON_NEGATIVE) -> UNVERIFIED (loaded variable)
        MethodVisitor fromWorld = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "fromWorld", "(I)I", null, null);
        ensures(fromWorld, "NON_NEGATIVE");
        fromWorld.visitCode();
        fromWorld.visitVarInsn(Opcodes.ILOAD, 0);
        fromWorld.visitInsn(Opcodes.IRETURN);
        fromWorld.visitMaxs(1, 1);
        fromWorld.visitEnd();

        // int indexed(int) { return 1; }  @Requires(param=0, NON_NEGATIVE) @Ensures(NON_NEGATIVE)
        MethodVisitor indexed = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "indexed", "(I)I", null, null);
        AnnotationVisitor req = indexed.visitAnnotation(REQUIRES, true);
        req.visit("param", 0);
        req.visitEnum("value", CONSTRAINT, "NON_NEGATIVE");
        req.visitEnd();
        ensures(indexed, "NON_NEGATIVE");
        indexed.visitCode();
        indexed.visitInsn(Opcodes.ICONST_1);
        indexed.visitInsn(Opcodes.IRETURN);
        indexed.visitMaxs(1, 1);
        indexed.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private interface Body {
        void emit(MethodVisitor mv);
    }

    private static void constIntHook(ClassWriter cw, String name, String constraint, Body body) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "()I", null, null);
        ensures(mv, constraint);
        mv.visitCode();
        body.emit(mv);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
    }

    private static void ensures(MethodVisitor mv, String constraint) {
        AnnotationVisitor av = mv.visitAnnotation(ENSURES, true);
        av.visitEnum("value", CONSTRAINT, constraint);
        av.visitEnd();
    }
}
