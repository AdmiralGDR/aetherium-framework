/*
 * Aetherium Framework — hands a compiled SPIR-V module to the native Vulkan bridge.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import org.aetherium.native_bridge.VulkanProbe;

/**
 * Routes a compiled {@link SpirvModule} into the existing {@code aetherium-native} Vulkan bridge.
 *
 * <p>EN: The compiler's product is a {@code byte[]} SPIR-V binary — exactly what
 * {@code vkCreateShaderModule} consumes. This class is the seam: it verifies the binary structurally
 * (refusing to upload a malformed module), inspects the {@link VulkanProbe} hardware surface, and
 * reports whether the kernel would dispatch on a real device or fall back to the CPU path. When the
 * native GPU dispatch layer lands ({@link org.aetherium.native_bridge.compute.NativeComputePipeline}),
 * only the {@code upload} step changes — the compiler and this contract do not.
 *
 * <p>RU: Продукт компилятора — бинарь SPIR-V в виде {@code byte[]}, ровно то, что принимает
 * {@code vkCreateShaderModule}. Этот класс — шов: структурно проверяет бинарь (отказываясь загружать
 * некорректный модуль), смотрит на аппаратную поверхность {@link VulkanProbe} и сообщает, будет ли
 * ядро диспетчеризовано на реальном устройстве или уйдёт на CPU-fallback. Когда появится нативный
 * слой GPU-диспетчеризации, изменится только шаг {@code upload}, а контракт — нет.
 */
public final class SpirvVulkanDispatch {

    private final VulkanProbe probe;

    public SpirvVulkanDispatch(VulkanProbe probe) {
        this.probe = probe;
    }

    /**
     * EN: Validate and "upload" the module, returning where it would run.
     * RU: Проверить и «загрузить» модуль, вернув, где он будет выполняться.
     */
    public DispatchResult dispatch(SpirvModule module) {
        SpirvModule.Verification v = module.verify();
        if (!v.valid()) {
            return new DispatchResult(false, false, "rejected", "invalid SPIR-V: " + v.detail());
        }
        boolean accelerated = probe.hasUsableDevice();
        String backend = accelerated
                ? "vulkan-compute (device=" + probe.deviceCount() + ", queues=" + probe.queueFamilyCount() + ")"
                : "cpu-fallback (no usable Vulkan device)";
        String message = String.format(
                "uploaded %d-word SPIR-V (%s %s) → %s",
                module.wordCount(), module.elementType(), module.op(), backend);
        return new DispatchResult(true, accelerated, backend, message);
    }

    /**
     * Outcome of a dispatch attempt.
     *
     * @param uploaded    the SPIR-V passed structural verification and was accepted by the bridge
     * @param accelerated a usable Vulkan device exists, so the kernel would run on the GPU
     * @param backend     a short label of the execution backend
     * @param message     a human-readable summary for logs / the CLI
     */
    public record DispatchResult(boolean uploaded, boolean accelerated, String backend, String message) {
    }
}
