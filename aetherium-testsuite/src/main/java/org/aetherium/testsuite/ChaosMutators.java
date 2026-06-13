/*
 * Aetherium Framework — chaos bytecode mutators.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Produces deliberately broken "mod" bytecode to stress the engine's safety net.
 *
 * <p>EN: Each mutator returns a {@code byte[]} that is invalid in a different way — truncated,
 * bit-flipped, type-confused, stack-underflowing, or a corrupted header. The contract under test is
 * that {@code BytecodeEngine.transformClass} contains <em>all</em> of these (reverting to the input
 * bytes + a diagnostic) and never throws. We never emit a class designed to dereference wild native
 * pointers — that would be a real SIGSEGV, not a catchable failure; native chaos uses FFM-guarded
 * operations instead (see {@code NativeChaos}).
 *
 * <p>RU: Каждый мутатор возвращает {@code byte[]}, некорректный по-своему — усечённый, с
 * перевёрнутыми битами, с путаницей типов, с недополнением стека или с испорченным заголовком.
 * Проверяется контракт: {@code BytecodeEngine.transformClass} локализует <em>все</em> такие случаи
 * (откат к входным байтам + диагностика) и никогда не бросает. Мы не создаём классы, разыменовывающие
 * дикие нативные указатели — это был бы настоящий SIGSEGV, а не перехватываемый сбой; нативный хаос
 * использует операции под защитой FFM (см. {@code NativeChaos}).
 */
public final class ChaosMutators {

    /** The kinds of corruption we inject. */
    public enum Kind {
        VALID,              // a healthy class (control sample)
        TRUNCATED,          // class file cut short
        BITFLIP,            // random bytes flipped
        HEADER_CORRUPT,     // bad magic / version
        TYPE_CONFUSION,     // returns an int where a reference is declared
        STACK_UNDERFLOW     // pops from an empty stack
    }

    private ChaosMutators() {
    }

    /** Generate a healthy dummy mod class with a unique name. */
    public static byte[] validClass(int seq) {
        return baseClass("org/aetherium/chaos/Mod" + seq, false, false);
    }

    /** Apply the given corruption kind to a freshly generated class. */
    public static byte[] mutate(Kind kind, int seq) {
        return switch (kind) {
            case VALID -> validClass(seq);
            case TYPE_CONFUSION -> baseClass("org/aetherium/chaos/Mod" + seq, true, false);
            case STACK_UNDERFLOW -> baseClass("org/aetherium/chaos/Mod" + seq, false, true);
            case TRUNCATED -> truncate(validClass(seq));
            case BITFLIP -> bitFlip(validClass(seq));
            case HEADER_CORRUPT -> corruptHeader(validClass(seq));
        };
    }

    /** Pick a random non-VALID corruption. */
    public static Kind randomCorruption() {
        Kind[] kinds = Kind.values();
        // skip VALID (index 0)
        return kinds[1 + ThreadLocalRandom.current().nextInt(kinds.length - 1)];
    }

    private static byte[] baseClass(String internalName, boolean typeConfusion, boolean stackUnderflow) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null);

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        if (typeConfusion) {
            // Declared to return Object, but returns an int via ARETURN — unverifiable.
            MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "broken", "()Ljava/lang/Object;", null, null);
            m.visitCode();
            m.visitInsn(Opcodes.ICONST_1);
            m.visitInsn(Opcodes.ARETURN); // type-confused: int on stack, areturn
            m.visitMaxs(1, 0);
            m.visitEnd();
        } else if (stackUnderflow) {
            // POP with nothing on the stack — stack underflow.
            MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "broken", "()V", null, null);
            m.visitCode();
            m.visitInsn(Opcodes.POP); // underflow
            m.visitInsn(Opcodes.RETURN);
            m.visitMaxs(2, 0); // deliberately wrong/forced maxs
            m.visitEnd();
        } else {
            MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()I", null, null);
            m.visitCode();
            m.visitIntInsn(Opcodes.BIPUSH, 21);
            m.visitInsn(Opcodes.ICONST_2);
            m.visitInsn(Opcodes.IMUL);
            m.visitInsn(Opcodes.IRETURN);
            m.visitMaxs(2, 0);
            m.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] truncate(byte[] original) {
        int keep = Math.max(8, original.length / 2);
        byte[] cut = new byte[keep];
        System.arraycopy(original, 0, cut, 0, keep);
        return cut;
    }

    private static byte[] bitFlip(byte[] original) {
        byte[] copy = original.clone();
        Random r = ThreadLocalRandom.current();
        int flips = 4 + r.nextInt(12);
        for (int i = 0; i < flips; i++) {
            // Flip somewhere past the 10-byte header to hit the constant pool / code.
            int idx = 10 + r.nextInt(Math.max(1, copy.length - 10));
            copy[idx] = (byte) (copy[idx] ^ (1 << r.nextInt(8)));
        }
        return copy;
    }

    private static byte[] corruptHeader(byte[] original) {
        byte[] copy = original.clone();
        // Smash the 0xCAFEBABE magic.
        copy[0] = 0x00;
        copy[1] = 0x00;
        return copy;
    }
}
