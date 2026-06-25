/*
 * Aetherium Framework — compute-shader marker annotation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a pure-Java method as a GPU compute kernel to be compiled to SPIR-V at runtime.
 *
 * <p>EN: A modder writes ordinary Java — a loop over primitive arrays doing basic math, e.g.
 * {@code for (int i = 0; i < n; i++) c[i] = a[i] + b[i];} — and tags it with this annotation. The
 * {@link JavaToSpirvCompiler} reads the method's bytecode (never invoking it on the CPU), recognises
 * the supported strict subset (primitives, arrays, loops, {@code +-*}), and emits a Vulkan SPIR-V
 * binary that the {@link SpirvVulkanDispatch} hands to the native bridge. Object allocation, method
 * calls into the heap, and reference types are intentionally out of scope.
 *
 * <p>RU: Мод-разработчик пишет обычный Java — цикл по примитивным массивам с базовой математикой,
 * напр. {@code for (int i = 0; i < n; i++) c[i] = a[i] + b[i];} — и помечает его этой аннотацией.
 * {@link JavaToSpirvCompiler} читает байт-код метода (никогда не исполняя его на CPU), распознаёт
 * поддерживаемое строгое подмножество (примитивы, массивы, циклы, {@code +-*}) и выпускает бинарь
 * Vulkan SPIR-V, который {@link SpirvVulkanDispatch} передаёт нативному мосту. Аллокация объектов,
 * вызовы в кучу и ссылочные типы намеренно вне области поддержки.
 *
 * <p>The annotation is retained at runtime so the compiler can locate kernels reflectively, and it is
 * placed on the {@code .class} via standard {@code javac} — no special processor required.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AetheriumComputeShader {

    /**
     * EN: Optional X dimension of the compute work-group ({@code local_size_x}); default 64.
     * RU: Необязательный размер X рабочей группы ({@code local_size_x}); по умолчанию 64.
     */
    int localSizeX() default 64;
}
