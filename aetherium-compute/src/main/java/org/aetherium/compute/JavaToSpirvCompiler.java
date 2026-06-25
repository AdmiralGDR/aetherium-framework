/*
 * Aetherium Framework — runtime Java→SPIR-V kernel compiler (ASM front-end).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Compiles a pure-Java {@code @AetheriumComputeShader} method into a Vulkan SPIR-V binary at runtime.
 *
 * <p>EN: This is the bytecode <em>front-end</em>. It reads the kernel class with ASM (never executing
 * it), locates the target method, and recognises the supported strict subset by scanning the
 * instruction list: a primitive-array store ({@code FASTORE}/{@code IASTORE}) fixes the element type,
 * and an arithmetic opcode ({@code F/IADD}, {@code F/ISUB}, {@code F/IMUL}) fixes the operation.
 * Anything that allocates ({@code NEW}, {@code ANEWARRAY}, …) or stores references ({@code AASTORE})
 * is rejected with {@link UnsupportedShaderException}. The recognised {@code (type, op)} drive
 * {@link SpirvKernelBuilder}, which emits the {@code dst[i] = a[i] OP b[i]} SPIR-V module.
 *
 * <p>RU: Это <em>front-end</em> по байт-коду. Класс ядра читается через ASM (без исполнения),
 * находится целевой метод, и поддерживаемое строгое подмножество распознаётся сканированием списка
 * инструкций: запись в примитивный массив ({@code FASTORE}/{@code IASTORE}) задаёт тип элемента, а
 * арифметический опкод ({@code F/IADD}, {@code F/ISUB}, {@code F/IMUL}) — операцию. Всё, что аллоцирует
 * ({@code NEW}, {@code ANEWARRAY}, …) или пишет ссылки ({@code AASTORE}), отвергается
 * {@link UnsupportedShaderException}. Распознанная пара {@code (тип, операция)} управляет
 * {@link SpirvKernelBuilder}, который выпускает модуль SPIR-V {@code dst[i] = a[i] OP b[i]}.
 */
public final class JavaToSpirvCompiler {

    private static final String ANNOTATION_DESC = Type.getDescriptor(AetheriumComputeShader.class);
    private static final int DEFAULT_LOCAL_SIZE_X = 64;

    /** Compile the single {@code @AetheriumComputeShader} method found on {@code kernelClass}. */
    public SpirvModule compile(Class<?> kernelClass) {
        ClassNode cn = readClass(kernelClass);
        MethodNode method = findAnnotatedMethod(cn);
        if (method == null) {
            throw new UnsupportedShaderException(
                    "no @AetheriumComputeShader method found on " + kernelClass.getName());
        }
        return analyzeAndBuild(method);
    }

    /**
     * EN: Compile from raw class bytes (e.g. a tool reading a {@code .class} off disk, or a fuzz
     * harness feeding adversarial input). Any malformed-bytecode failure from the ASM front-end is
     * normalized to {@link UnsupportedShaderException} so a caller need only guard one exception type —
     * a garbage blob can never escape as a raw {@code ArrayIndexOutOfBoundsException} or similar.
     * RU: Скомпилировать из сырых байтов класса (напр. инструмент, читающий {@code .class}, или фаззер
     * с враждебным входом). Любой сбой разбора в ASM-front-end нормализуется в
     * {@link UnsupportedShaderException}, чтобы вызывающему достаточно было перехватить один тип —
     * мусорный блоб не может «утечь» как сырой {@code ArrayIndexOutOfBoundsException}.
     */
    public SpirvModule compileBytes(byte[] classBytes) {
        if (classBytes == null) {
            throw new UnsupportedShaderException("class bytes are null");
        }
        ClassNode cn = new ClassNode();
        try {
            new ClassReader(classBytes).accept(cn, ClassReader.SKIP_FRAMES);
        } catch (RuntimeException malformed) {
            // ClassReader throws unchecked (AIOOBE, IllegalArgumentException, …) on a non-class blob.
            throw new UnsupportedShaderException(
                    "not a parseable Java class (" + malformed.getClass().getSimpleName() + ")");
        }
        MethodNode method = findAnnotatedMethod(cn);
        if (method == null) {
            throw new UnsupportedShaderException("no @AetheriumComputeShader method in supplied class bytes");
        }
        return analyzeAndBuild(method);
    }

    /** Compile a specific method (by name) of {@code kernelClass}. */
    public SpirvModule compile(Class<?> kernelClass, String methodName) {
        ClassNode cn = readClass(kernelClass);
        MethodNode method = cn.methods.stream()
                .filter(m -> m.name.equals(methodName))
                .findFirst()
                .orElseThrow(() -> new UnsupportedShaderException(
                        "method '" + methodName + "' not found on " + kernelClass.getName()));
        return analyzeAndBuild(method);
    }

    private ClassNode readClass(Class<?> kernelClass) {
        String resource = kernelClass.getName().replace('.', '/') + ".class";
        try (InputStream in = kernelClass.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new UnsupportedShaderException("class bytes not found for " + kernelClass.getName());
            }
            ClassNode cn = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_FRAMES);
            return cn;
        } catch (IOException e) {
            throw new UnsupportedShaderException("could not read " + kernelClass.getName() + ": " + e.getMessage());
        }
    }

    private MethodNode findAnnotatedMethod(ClassNode cn) {
        for (MethodNode m : cn.methods) {
            if (hasShaderAnnotation(m)) {
                return m;
            }
        }
        return null;
    }

    private static boolean hasShaderAnnotation(MethodNode m) {
        List<AnnotationNode> anns = m.visibleAnnotations;
        if (anns == null) {
            return false;
        }
        return anns.stream().anyMatch(a -> ANNOTATION_DESC.equals(a.desc));
    }

    private SpirvModule analyzeAndBuild(MethodNode method) {
        ComputeElementType elementType = null;
        ComputeBinaryOp op = null;
        ComputeUnaryOp unaryOp = null;

        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            // A java.lang.Math.<fn> call lowers to a GLSL.std.450 extended instruction.
            if (insn instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESTATIC
                    && "java/lang/Math".equals(call.owner)) {
                ComputeUnaryOp mathOp = ComputeUnaryOp.forMathMethod(call.name);
                if (mathOp == null) {
                    throw new UnsupportedShaderException(
                            "unsupported java.lang.Math call '" + call.name + "' (supported: sin, cos, tan, "
                                    + "sqrt, exp, log, abs, floor)");
                }
                if (unaryOp != null && unaryOp != mathOp) {
                    throw new UnsupportedShaderException(
                            "kernel '" + method.name + "' mixes multiple Math intrinsics; one per kernel");
                }
                unaryOp = mathOp;
            }
            int opcode = insn.getOpcode();
            switch (opcode) {
                // Object/array allocation and reference stores are outside the subset.
                case Opcodes.NEW, Opcodes.ANEWARRAY, Opcodes.MULTIANEWARRAY, Opcodes.NEWARRAY ->
                        throw new UnsupportedShaderException(
                                "kernel allocates memory (opcode " + opcode + "); only passed-in primitive arrays are supported");
                case Opcodes.AASTORE ->
                        throw new UnsupportedShaderException("kernel stores object references; only primitive arrays are supported");

                // A primitive-array store fixes the element type of the kernel.
                case Opcodes.FASTORE -> elementType = ComputeElementType.FLOAT32;
                case Opcodes.IASTORE -> elementType = ComputeElementType.INT32;

                // The arithmetic op fixes the operation.
                case Opcodes.FADD, Opcodes.IADD -> op = ComputeBinaryOp.ADD;
                case Opcodes.FSUB, Opcodes.ISUB -> op = ComputeBinaryOp.SUB;
                case Opcodes.FMUL, Opcodes.IMUL -> op = ComputeBinaryOp.MUL;

                default -> { /* loads, increments, branches, constants: part of the supported loop form */ }
            }
        }

        if (elementType == null) {
            throw new UnsupportedShaderException(
                    "kernel '" + method.name + "' writes no primitive array (expected float[]/int[] output)");
        }
        int localSizeX = localSizeX(method);
        // A hostile/garbage annotation may carry a non-positive work-group size; keep the public
        // contract (only UnsupportedShaderException escapes) instead of leaking the builder's IAE.
        if (localSizeX < 1) {
            throw new UnsupportedShaderException(
                    "kernel '" + method.name + "' declares a non-positive localSizeX (" + localSizeX + ")");
        }

        // Unary math kernel: c[i] = Math.fn(a[i]) → GLSL.std.450 OpExtInst (float-only).
        if (unaryOp != null) {
            if (op != null) {
                throw new UnsupportedShaderException(
                        "kernel '" + method.name + "' mixes a Math intrinsic with arithmetic; not supported");
            }
            if (elementType != ComputeElementType.FLOAT32) {
                throw new UnsupportedShaderException(
                        "kernel '" + method.name + "' uses a Math intrinsic but writes a non-float array; "
                                + "GLSL.std.450 math is float-only");
            }
            return SpirvKernelBuilder.buildUnary(unaryOp, localSizeX);
        }

        if (op == null) {
            throw new UnsupportedShaderException(
                    "kernel '" + method.name + "' performs no supported arithmetic (+, -, * on primitives)");
        }
        return SpirvKernelBuilder.build(elementType, op, localSizeX);
    }

    /** Read {@code localSizeX()} from the annotation if present, else the default work-group size. */
    private static int localSizeX(MethodNode method) {
        if (method.visibleAnnotations == null) {
            return DEFAULT_LOCAL_SIZE_X;
        }
        for (AnnotationNode a : method.visibleAnnotations) {
            if (!ANNOTATION_DESC.equals(a.desc) || a.values == null) {
                continue;
            }
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                if ("localSizeX".equals(a.values.get(i)) && a.values.get(i + 1) instanceof Integer x) {
                    return x;
                }
            }
        }
        return DEFAULT_LOCAL_SIZE_X;
    }
}
