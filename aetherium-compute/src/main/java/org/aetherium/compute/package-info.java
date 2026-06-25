/*
 * Aetherium Framework — compute module package docs.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * Runtime Java→SPIR-V compilation: pure-Java kernels become Vulkan compute binaries.
 *
 * <p>EN: Annotate a method {@link org.aetherium.compute.AetheriumComputeShader}, then
 * {@link org.aetherium.compute.JavaToSpirvCompiler} reads its bytecode (ASM), recognises the supported
 * subset (primitives, arrays, loops, {@code + - *}), and {@link org.aetherium.compute.SpirvKernelBuilder}
 * emits a {@link org.aetherium.compute.SpirvModule} (magic {@code 0x07230203}). The binary is routed to
 * the native bridge by {@link org.aetherium.compute.SpirvVulkanDispatch}.
 * RU: Пометьте метод {@link org.aetherium.compute.AetheriumComputeShader}, затем
 * {@link org.aetherium.compute.JavaToSpirvCompiler} читает его байт-код (ASM), распознаёт поддерживаемое
 * подмножество (примитивы, массивы, циклы, {@code + - *}), а {@link org.aetherium.compute.SpirvKernelBuilder}
 * выпускает {@link org.aetherium.compute.SpirvModule} (магия {@code 0x07230203}). Бинарь направляется
 * нативному мосту через {@link org.aetherium.compute.SpirvVulkanDispatch}.
 */
package org.aetherium.compute;
