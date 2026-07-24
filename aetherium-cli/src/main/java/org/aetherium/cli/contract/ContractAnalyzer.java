/*
 * Aetherium Framework — static hook contract verifier (Consistency).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.contract;

import org.aetherium.injector.contract.Constraint;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads {@link org.aetherium.injector.contract.Ensures @Ensures} /
 * {@link org.aetherium.injector.contract.Requires @Requires} contracts straight from compiled hook
 * bytecode and statically verifies the return postcondition — the Consistency pillar of the ACID engine.
 *
 * <p>EN: For each annotated method the analyzer runs the {@link SignInterpreter} to infer the abstract
 * signs its {@code return}s can produce, then compares them to the declared {@code @Ensures} constraint.
 * A return proven to lie outside the constraint (e.g. a light-level hook that can hand back {@code -1}
 * under {@code @Ensures(NON_NEGATIVE)}) is a {@link Verdict#VIOLATED} warning surfaced <em>before</em> the
 * game runs; a return the basic analyzer cannot pin down is {@link Verdict#UNVERIFIED} (reported, never a
 * false alarm). Read-only and non-executing — safe to run over any jar in {@code analyze}.
 *
 * <p>RU: Для каждого аннотированного метода анализатор запускает {@link SignInterpreter}, выводя
 * абстрактные знаки возвратов, и сравнивает их с объявленным {@code @Ensures}. Возврат, доказуемо
 * лежащий вне ограничения (напр. хук уровня освещённости, способный вернуть {@code -1} при
 * {@code @Ensures(NON_NEGATIVE)}), — предупреждение {@link Verdict#VIOLATED} ещё до запуска игры; возврат,
 * который простой анализатор не смог зафиксировать, — {@link Verdict#UNVERIFIED}. Только чтение, без
 * исполнения.
 */
public final class ContractAnalyzer {

    private static final String ENSURES_DESC = "Lorg/aetherium/injector/contract/Ensures;";
    private static final String REQUIRES_DESC = "Lorg/aetherium/injector/contract/Requires;";
    private static final String CONSTRAINT_DESC = "Lorg/aetherium/injector/contract/Constraint;";

    /** The outcome of checking one method's return postcondition. */
    public enum Verdict {
        /** Every return is proven to satisfy the constraint. */
        SATISFIED,
        /** At least one return is proven to violate the constraint (a warning). */
        VIOLATED,
        /** The return sign could not be pinned down by the basic analyzer. */
        UNVERIFIED
    }

    /** One declared input precondition. */
    public record RequiredInput(int param, Constraint constraint) {
    }

    /** The full contract verdict for one hook method. */
    public record MethodContract(String methodName,
                                 String methodDesc,
                                 Constraint ensures,
                                 List<RequiredInput> requires,
                                 Set<Sign> inferredReturnSigns,
                                 Verdict verdict,
                                 String message) {
    }

    /** The per-class contract report. */
    public record Report(String className, List<MethodContract> contracts) {
        public long violations() {
            return contracts.stream().filter(c -> c.verdict() == Verdict.VIOLATED).count();
        }

        public long unverified() {
            return contracts.stream().filter(c -> c.verdict() == Verdict.UNVERIFIED).count();
        }

        public boolean hasContracts() {
            return !contracts.isEmpty();
        }

        public boolean clean() {
            return violations() == 0;
        }
    }

    private ContractAnalyzer() {
    }

    /** Analyze one class's bytecode, returning the contract verdicts for every annotated method. */
    public static Report analyze(byte[] classBytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(classBytes).accept(cn, ClassReader.SKIP_FRAMES);

        List<MethodContract> contracts = new ArrayList<>();
        for (MethodNode method : cn.methods) {
            Constraint ensures = readEnsures(method.visibleAnnotations);
            List<RequiredInput> requires = readRequires(method.visibleAnnotations);
            if (ensures == null && requires.isEmpty()) {
                continue;
            }
            Constraint effectiveEnsures = ensures == null ? Constraint.ANY : ensures;
            Set<Sign> signs = SignInterpreter.inferReturnSigns(method);
            Verdict verdict = judge(effectiveEnsures, signs);
            contracts.add(new MethodContract(method.name, method.desc, ensures, requires, signs, verdict,
                    describe(effectiveEnsures, signs, verdict)));
        }
        return new Report(cn.name.replace('/', '.'), List.copyOf(contracts));
    }

    private static Verdict judge(Constraint ensures, Set<Sign> signs) {
        if (ensures == Constraint.ANY || signs.isEmpty()) {
            return Verdict.SATISFIED;
        }
        for (Sign s : signs) {
            if (s.violates(ensures)) {
                return Verdict.VIOLATED;
            }
        }
        return signs.contains(Sign.UNKNOWN) ? Verdict.UNVERIFIED : Verdict.SATISFIED;
    }

    private static String describe(Constraint ensures, Set<Sign> signs, Verdict verdict) {
        return switch (verdict) {
            case SATISFIED -> ensures == Constraint.ANY
                    ? "no return constraint"
                    : "every return proven " + ensures + " " + signs;
            case VIOLATED -> "a return can be " + signs + " but @Ensures(" + ensures + ") — contract may break";
            case UNVERIFIED -> "return sign " + signs + " could not be proven against @Ensures(" + ensures + ")";
        };
    }

    private static Constraint readEnsures(List<AnnotationNode> annotations) {
        AnnotationNode a = find(annotations, ENSURES_DESC);
        if (a == null) {
            return null;
        }
        Constraint c = constraintValue(a, "value");
        return c == null ? Constraint.ANY : c;
    }

    private static List<RequiredInput> readRequires(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return List.of();
        }
        List<RequiredInput> out = new ArrayList<>();
        for (AnnotationNode a : annotations) {
            if (!REQUIRES_DESC.equals(a.desc)) {
                continue;
            }
            Constraint c = constraintValue(a, "value");
            int param = intValue(a, "param", 0);
            out.add(new RequiredInput(param, c == null ? Constraint.ANY : c));
        }
        return List.copyOf(out);
    }

    private static AnnotationNode find(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) {
            return null;
        }
        for (AnnotationNode a : annotations) {
            if (desc.equals(a.desc)) {
                return a;
            }
        }
        return null;
    }

    /** Read an enum-valued member (ASM stores it as {@code String[]{ enumDesc, valueName }}). */
    private static Constraint constraintValue(AnnotationNode a, String member) {
        if (a.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < a.values.size(); i += 2) {
            if (member.equals(a.values.get(i)) && a.values.get(i + 1) instanceof String[] enumRef
                    && enumRef.length == 2 && CONSTRAINT_DESC.equals(enumRef[0])) {
                try {
                    return Constraint.valueOf(enumRef[1]);
                } catch (IllegalArgumentException unknown) {
                    return null;
                }
            }
        }
        return null;
    }

    private static int intValue(AnnotationNode a, String member, int fallback) {
        if (a.values == null) {
            return fallback;
        }
        for (int i = 0; i + 1 < a.values.size(); i += 2) {
            if (member.equals(a.values.get(i)) && a.values.get(i + 1) instanceof Integer n) {
                return n;
            }
        }
        return fallback;
    }
}
