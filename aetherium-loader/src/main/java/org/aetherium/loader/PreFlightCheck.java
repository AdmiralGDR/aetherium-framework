/*
 * Aetherium Framework — Pre-Flight Check.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import org.aetherium.bytecode.selftest.EngineSelfTest;
import org.aetherium.core.Capability;
import org.aetherium.core.CapabilityRegistry;
import org.aetherium.core.CapabilityTier;
import org.aetherium.core.Diagnostic;
import org.aetherium.core.compute.ComputeCapabilities;
import org.aetherium.core.diag.DiagnosticTranslator;
import org.aetherium.core.diag.Explanation;
import org.aetherium.native_bridge.NativeCapabilityProviders;
import org.aetherium.native_bridge.NativeProbe;

import java.util.ArrayList;
import java.util.List;

/**
 * The framework's internal self-test, run <strong>before</strong> the mod-loading phase begins.
 *
 * <p>EN: Validates the two critical subsystems with real but harmless work — a dummy ASM transform
 * (via {@link EngineSelfTest}) and a dummy native memory allocation (via {@link NativeProbe}) — then
 * resolves the compute {@link CapabilityTier} through the {@link CapabilityRegistry}. It is
 * <em>total</em>: any failure (including a missing/broken native library) is caught, translated to a
 * bilingual {@link Explanation} by the {@link DiagnosticTranslator}, and recorded; the framework
 * degrades to pure Java and the launch proceeds. It never throws and never aborts Minecraft.
 *
 * <p>RU: Внутренняя самопроверка фреймворка, выполняемая <strong>до</strong> начала фазы загрузки
 * модов. Проверяет две критичные подсистемы реальной, но безвредной работой — фиктивной ASM-
 * трансформацией (через {@link EngineSelfTest}) и фиктивной нативной аллокацией (через
 * {@link NativeProbe}) — затем разрешает уровень вычислений {@link CapabilityTier} через
 * {@link CapabilityRegistry}. Она <em>тотальна</em>: любой сбой (включая отсутствие/поломку нативной
 * библиотеки) перехватывается, переводится в двуязычное {@link Explanation} транслятором
 * {@link DiagnosticTranslator} и фиксируется; фреймворк деградирует на чистую Java, и запуск
 * продолжается. Никогда не бросает исключение и не прерывает Minecraft.
 */
public final class PreFlightCheck {

    private PreFlightCheck() {
    }

    /**
     * Structured, UI-friendly outcome. Exposes only {@code core} types + primitives so any front-end
     * (CLI, loader log) can render it without depending on the bytecode/native modules.
     *
     * @param asmOk             the bytecode-engine self-test passed
     * @param asmValue          the value produced by the dummy transform (expected 42)
     * @param nativeHealthy     the native bridge loaded and passed its dummy allocation
     * @param tier              the resolved compute capability tier (FFM or PURE_JAVA)
     * @param vulkanAvailable   a Vulkan instance was created during the hardware-access probe
     * @param vulkanDeviceCount physical devices enumerated
     * @param vulkanQueueFamilies queue families on device 0
     * @param lines             bilingual human-readable status/explanation lines
     * @param diagnostics       structured diagnostics (e.g. the degradation warning)
     */
    public record Report(boolean asmOk,
                         int asmValue,
                         boolean nativeHealthy,
                         CapabilityTier tier,
                         boolean vulkanAvailable,
                         int vulkanDeviceCount,
                         int vulkanQueueFamilies,
                         List<String> lines,
                         List<Diagnostic> diagnostics) {

        /**
         * EN: The launch may proceed iff the ASM engine works and a tier was resolved (pure Java is a
         * valid tier). Native failure alone does NOT fail pre-flight — that is graceful degradation.
         * RU: Запуск допустим, если работает ASM-движок и уровень разрешён (чистая Java — валидный
         * уровень). Один лишь нативный сбой НЕ проваливает pre-flight — это мягкая деградация.
         */
        public boolean launchAllowed() {
            return asmOk && tier != CapabilityTier.DISABLED;
        }
    }

    /** Run the pre-flight check. Never throws. */
    public static Report run() {
        List<String> lines = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();

        // (1) Dummy ASM transform.
        boolean asmOk;
        int asmValue = -1;
        try {
            EngineSelfTest.Result asm = EngineSelfTest.run();
            asmOk = asm.passed();
            asmValue = asm.observedValue();
            lines.add("EN: ASM engine self-test " + (asmOk ? "passed" : "FAILED")
                    + " (dummy transform produced " + asmValue + ", fallback "
                    + (asm.fallbackOk() ? "ok" : "BROKEN") + ").");
            lines.add("RU: Самопроверка ASM-движка " + (asmOk ? "пройдена" : "ПРОВАЛЕНА")
                    + " (фиктивная трансформация дала " + asmValue + ", откат "
                    + (asm.fallbackOk() ? "ок" : "СЛОМАН") + ").");
        } catch (Throwable asmFailure) {
            asmOk = false;
            Explanation explanation = DiagnosticTranslator.translate(asmFailure);
            diagnostics.add(explanation.toDiagnostic());
            lines.add("EN: " + explanation.english());
            lines.add("RU: " + explanation.russian());
        }

        // (2) Dummy native memory allocation (+ Vulkan hardware-access probe). Never throws.
        NativeProbe.Report nativeReport = NativeProbe.run();
        boolean nativeHealthy = nativeReport.healthy();
        if (nativeHealthy) {
            lines.add("EN: Native bridge healthy (ABI " + nativeReport.abiVersion()
                    + ", dummy allocation summed " + nativeReport.allocationSum() + ").");
            lines.add("RU: Нативный мост исправен (ABI " + nativeReport.abiVersion()
                    + ", фиктивная аллокация в сумме " + nativeReport.allocationSum() + ").");
            lines.add("EN: Vulkan probe — available=" + nativeReport.vulkan().available()
                    + ", devices=" + nativeReport.vulkan().deviceCount()
                    + ", queueFamilies=" + nativeReport.vulkan().queueFamilyCount() + ".");
            lines.add("RU: Vulkan-зонд — доступен=" + nativeReport.vulkan().available()
                    + ", устройств=" + nativeReport.vulkan().deviceCount()
                    + ", семейств очередей=" + nativeReport.vulkan().queueFamilyCount() + ".");
        } else {
            // Graceful degradation: translate the raw error to a bilingual warning and continue.
            Throwable cause = nativeReport.error().orElseGet(
                    () -> new UnsatisfiedLinkError("native bridge unavailable"));
            Explanation explanation = DiagnosticTranslator.translate(cause);
            diagnostics.add(explanation.toDiagnostic());
            lines.add("EN: " + explanation.english());
            lines.add("RU: " + explanation.russian());
        }

        // (3) Resolve the compute tier through the registry (consistent with runtime resolution).
        CapabilityRegistry registry = new CapabilityRegistry();
        Capability gpuCompute = ComputeCapabilities.GPU_COMPUTE;
        registry.register(gpuCompute, NativeCapabilityProviders.computeChain());
        CapabilityTier tier = registry.tierOf(gpuCompute);
        lines.add("EN: Resolved compute tier: " + tier + (tier == CapabilityTier.PURE_JAVA
                ? " (degraded — native acceleration unavailable)." : "."));
        lines.add("RU: Выбранный уровень вычислений: " + tier + (tier == CapabilityTier.PURE_JAVA
                ? " (деградация — нативное ускорение недоступно)." : "."));

        return new Report(
                asmOk,
                asmValue,
                nativeHealthy,
                tier,
                nativeReport.vulkan().available(),
                nativeReport.vulkan().deviceCount(),
                nativeReport.vulkan().queueFamilyCount(),
                List.copyOf(lines),
                List.copyOf(diagnostics));
    }
}
