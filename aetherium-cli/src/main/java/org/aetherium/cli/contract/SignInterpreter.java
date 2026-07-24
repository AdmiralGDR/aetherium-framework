/*
 * Aetherium Framework — a basic symbolic (sign) interpreter over a method's bytecode.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.contract;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;

/**
 * A deliberately-basic symbolic executor that infers the abstract {@link Sign} of every value a method
 * can return — the static engine behind the CLI's contract verification.
 *
 * <p>EN: It walks the method's instructions once with a small abstract operand stack of {@link Sign}s:
 * constant pushes carry a precise sign, {@code neg} and integer {@code +,-,*} combine signs by the usual
 * rules, and anything the analyzer cannot follow (a loaded variable, a call result, control flow) makes
 * the analysis conservatively {@link Sign#UNKNOWN}. The result is the set of signs reachable at the
 * method's {@code IRETURN}/{@code LRETURN} sites, which the {@link ContractAnalyzer} compares against the
 * declared {@code @Ensures} constraint. It never executes the method and never throws to the caller — an
 * unmodelled shape simply yields {@code {UNKNOWN}} (reported as unverified, never a false alarm).
 *
 * <p>RU: Намеренно простой символический исполнитель, проходящий инструкции метода один раз с маленьким
 * абстрактным стеком {@link Sign}: константы несут точный знак, {@code neg} и целочисленные {@code +,-,*}
 * комбинируют знаки, а всё непрослеживаемое (переменная, результат вызова, поток управления) делает анализ
 * консервативно {@link Sign#UNKNOWN}. Результат — множество знаков, достижимых на точках
 * {@code IRETURN}/{@code LRETURN}. Метод не исполняется, исключения наружу не бросаются.
 */
public final class SignInterpreter {

    /** Thrown internally when an unmodelled opcode is hit — abandons precise analysis for this method. */
    private static final class UnsupportedShapeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UnsupportedShapeException() {
            super(null, null, false, false);
        }
    }

    private SignInterpreter() {
    }

    /**
     * The set of signs the method can return. Empty if the method has no integral ({@code int}/{@code long})
     * return type. {@code {UNKNOWN}} if the method's shape is beyond this basic analyzer.
     */
    public static Set<Sign> inferReturnSigns(MethodNode method) {
        Type ret = Type.getReturnType(method.desc);
        if (ret.getSort() != Type.INT && ret.getSort() != Type.LONG) {
            return EnumSet.noneOf(Sign.class);
        }
        try {
            return walk(method);
        } catch (RuntimeException beyondBasic) {
            // Any structure we cannot soundly follow is reported as unverified, never a false violation.
            return EnumSet.of(Sign.UNKNOWN);
        }
    }

    private static Set<Sign> walk(MethodNode method) {
        Deque<Sign> stack = new ArrayDeque<>();
        EnumSet<Sign> returns = EnumSet.noneOf(Sign.class);

        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op < 0) {
                continue; // labels, line numbers, frames
            }
            switch (op) {
                case Opcodes.NOP -> { }
                case Opcodes.ICONST_M1 -> stack.push(Sign.of(-1));
                case Opcodes.ICONST_0, Opcodes.LCONST_0 -> stack.push(Sign.ZERO);
                case Opcodes.ICONST_1, Opcodes.LCONST_1 -> stack.push(Sign.POSITIVE);
                case Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5 ->
                        stack.push(Sign.POSITIVE);
                case Opcodes.BIPUSH, Opcodes.SIPUSH -> stack.push(Sign.of(((IntInsnNode) insn).operand));
                case Opcodes.LDC -> stack.push(signOfConstant(((LdcInsnNode) insn).cst));
                case Opcodes.ILOAD, Opcodes.LLOAD -> stack.push(Sign.UNKNOWN);
                case Opcodes.INEG, Opcodes.LNEG -> stack.push(pop(stack).negate());
                case Opcodes.IADD, Opcodes.LADD -> {
                    Sign b = pop(stack);
                    Sign a = pop(stack);
                    stack.push(Sign.add(a, b));
                }
                case Opcodes.ISUB, Opcodes.LSUB -> {
                    Sign b = pop(stack);
                    Sign a = pop(stack);
                    stack.push(Sign.sub(a, b));
                }
                case Opcodes.IMUL, Opcodes.LMUL -> {
                    Sign b = pop(stack);
                    Sign a = pop(stack);
                    stack.push(Sign.mul(a, b));
                }
                case Opcodes.IDIV, Opcodes.LDIV, Opcodes.IREM, Opcodes.LREM,
                     Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR, Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR,
                     Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR, Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR -> {
                    pop(stack);
                    pop(stack);
                    stack.push(Sign.UNKNOWN);
                }
                case Opcodes.DUP -> stack.push(peek(stack));
                case Opcodes.POP -> pop(stack);
                case Opcodes.IRETURN, Opcodes.LRETURN -> returns.add(pop(stack));
                default -> throw new UnsupportedShapeException(); // branches, calls, arrays, floats, …
            }
        }
        return returns.isEmpty() ? EnumSet.of(Sign.UNKNOWN) : returns;
    }

    private static Sign signOfConstant(Object cst) {
        if (cst instanceof Integer i) {
            return Sign.of(i);
        }
        if (cst instanceof Long l) {
            return Sign.of(l);
        }
        return Sign.UNKNOWN;
    }

    private static Sign pop(Deque<Sign> stack) {
        Sign s = stack.poll();
        if (s == null) {
            throw new UnsupportedShapeException();
        }
        return s;
    }

    private static Sign peek(Deque<Sign> stack) {
        Sign s = stack.peek();
        if (s == null) {
            throw new UnsupportedShapeException();
        }
        return s;
    }
}
