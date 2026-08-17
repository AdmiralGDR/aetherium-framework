/*
 * Aetherium Framework — fail-loud GPU kernel dispatch facade.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

import org.aetherium.core.Diagnostic;
import org.aetherium.core.Outcome;
import org.aetherium.native_bridge.NativeBridge;

/**
 * Dispatch a compiled SPIR-V kernel on the GPU under the framework's fail-loud contract: the caller is
 * <em>told</em> when the kernel did not run on the GPU, and picks the CPU/SIMD tier itself.
 *
 * <p>EN: The raw {@link NativeBridge#dispatchUnary} returns {@code null} when there is no usable Vulkan device
 * — a silent signal a careless caller can misuse (NPE) or ignore (proceed with no result). This facade turns
 * that into an {@link Outcome}: {@link Outcome.Ran} with the GPU result on a real device; {@link Outcome.Skipped}
 * with a {@link Diagnostic} when there is no device or no native bridge (so {@code outcome.orElseGet(cpu)} runs
 * the CPU path <em>and</em> {@code onSkipped} can log it); {@link Outcome.Failed} for a malformed module. No
 * silent GPU→CPU swap. The SPIR-V must bind two std430 SSBOs (0 = input, 1 = output). Zero-dependency.
 * RU: Сырой {@link NativeBridge#dispatchUnary} возвращает {@code null}, когда нет пригодного устройства Vulkan —
 * тихий сигнал, который небрежный вызывающий может уронить в NPE или проигнорировать. Этот фасад превращает
 * это в {@link Outcome}: {@link Outcome.Ran} с GPU-результатом на реальном устройстве; {@link Outcome.Skipped}
 * с {@link Diagnostic}, когда устройства/моста нет (тогда {@code orElseGet(cpu)} выполнит CPU-путь, а
 * {@code onSkipped} — залогирует); {@link Outcome.Failed} для некорректного модуля. Без тихой подмены GPU→CPU.
 */
public final class GpuKernels {

    private GpuKernels() {
    }

    /**
     * Dispatch a unary SPIR-V kernel over {@code input}. See the class doc for the {@link Outcome} contract.
     */
    public static Outcome<float[]> dispatchUnary(SpirvModule module, float[] input) {
        SpirvModule.Verification verification = module.verify();
        if (!verification.valid()) {
            return Outcome.failed(Diagnostic.error("AE-GPU-BADSPIRV",
                    "refusing to dispatch a malformed SPIR-V module: " + verification.detail()));
        }
        try (NativeBridge bridge = NativeBridge.load()) {
            float[] gpu = bridge.dispatchUnary(module.toByteArray(), input, module.localSizeX());
            if (gpu == null) {
                return Outcome.skipped(Diagnostic.warn("AE-GPU-NODEVICE",
                        "no usable Vulkan device — the caller should run the CPU/SIMD tier"));
            }
            return Outcome.ran(gpu);
        } catch (Throwable noNativeBridge) {
            return Outcome.skipped(Diagnostic.warn("AE-GPU-NOBRIDGE",
                    "native Vulkan bridge unavailable (" + noNativeBridge.getClass().getSimpleName()
                            + ") — the caller should run the CPU/SIMD tier"));
        }
    }
}
