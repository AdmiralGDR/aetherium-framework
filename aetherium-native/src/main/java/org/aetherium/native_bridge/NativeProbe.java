/*
 * Aetherium Framework — native probe (Pre-Flight building block).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.native_bridge;

import org.aetherium.core.CapabilityTier;

import java.util.Optional;

/**
 * A non-throwing probe of the native bridge, for use by the Pre-Flight Check.
 *
 * <p>EN: Attempts to load the library and run a dummy native allocation + Vulkan probe, catching
 * <em>everything</em> (including {@link UnsatisfiedLinkError} and other {@link Error}s) so the
 * caller gets a structured {@link Report} instead of an exception. The decided {@link CapabilityTier}
 * is {@code FFM} when the native path works and {@code PURE_JAVA} otherwise — the graceful-degradation
 * contract: a broken native layer must never abort the launch.
 *
 * <p>RU: Пытается загрузить библиотеку и выполнить фиктивную нативную аллокацию + Vulkan-зонд,
 * перехватывая <em>всё</em> (включая {@link UnsatisfiedLinkError} и прочие {@link Error}), чтобы
 * вызывающая сторона получила структурированный {@link Report}, а не исключение. Выбранный
 * {@link CapabilityTier} — {@code FFM}, когда нативный путь работает, иначе {@code PURE_JAVA} —
 * контракт мягкой деградации: сломанный нативный слой никогда не должен прерывать запуск.
 */
public final class NativeProbe {

    private NativeProbe() {
    }

    /**
     * Structured probe outcome.
     *
     * @param loaded         the native library loaded and passed its ABI check
     * @param abiVersion     reported ABI version (-1 if not loaded)
     * @param selfTestOk     native {@code selfTest(21) == 42}
     * @param allocationSum  result of the dummy Arena allocation + native sum (-1 if not run)
     * @param allocationOk   the allocation sum matched the expected value
     * @param vulkan         the Vulkan hardware-access probe result
     * @param tier           the decided capability tier (FFM or PURE_JAVA)
     * @param error          the raw failure, if any (for the diagnostic translator)
     */
    public record Report(boolean loaded,
                         int abiVersion,
                         boolean selfTestOk,
                         long allocationSum,
                         boolean allocationOk,
                         VulkanProbe vulkan,
                         CapabilityTier tier,
                         Optional<Throwable> error) {

        /** True if the native bridge is fully healthy (loaded, self-test + allocation passed). */
        public boolean healthy() {
            return loaded && selfTestOk && allocationOk;
        }
    }

    private static final int SELF_TEST_INPUT = 21;
    private static final int SELF_TEST_EXPECTED = 42;
    private static final int ALLOC_BYTES = 4096;

    /** Run the probe. Never throws. */
    public static Report run() {
        try (NativeBridge bridge = NativeBridge.load()) {
            int abi = bridge.abiVersion();
            boolean selfTestOk = bridge.selfTest(SELF_TEST_INPUT) == SELF_TEST_EXPECTED;
            long sum = bridge.allocateAndSum(ALLOC_BYTES);
            boolean allocationOk = sum == ALLOC_BYTES; // each of ALLOC_BYTES bytes set to 1
            VulkanProbe vulkan = bridge.probeVulkan();

            CapabilityTier tier = (selfTestOk && allocationOk) ? CapabilityTier.FFM : CapabilityTier.PURE_JAVA;
            return new Report(true, abi, selfTestOk, sum, allocationOk, vulkan, tier, Optional.empty());
        } catch (Throwable failure) {
            // Catch Throwable on purpose: UnsatisfiedLinkError / ExceptionInInitializerError / etc.
            // must degrade, not propagate.
            return new Report(false, -1, false, -1L, false,
                    VulkanProbe.unavailable(Integer.MIN_VALUE), CapabilityTier.PURE_JAVA, Optional.of(failure));
        }
    }
}
