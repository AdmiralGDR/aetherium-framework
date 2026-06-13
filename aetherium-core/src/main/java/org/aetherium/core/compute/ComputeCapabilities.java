package org.aetherium.core.compute;

import org.aetherium.core.Capability;

/**
 * Well-known Hardware &amp; Compute capability descriptors.
 *
 * <p>EN: Stable {@link Capability} constants so registration and lookup never hardcode raw id
 * strings in scattered places (anti-hardcoding rule). Providers for these are wired in by the
 * native module via the {@link org.aetherium.core.CapabilityRegistry}; here we only declare the
 * contract identities.
 *
 * <p>RU: Стабильные константы {@link Capability}, чтобы регистрация и поиск нигде не зашивали
 * сырые id-строки (правило отказа от хардкода). Провайдеры для них подключаются нативным модулем
 * через {@link org.aetherium.core.CapabilityRegistry}; здесь мы лишь объявляем идентичности
 * контрактов.
 */
public final class ComputeCapabilities {

    private ComputeCapabilities() {
    }

    /** Off-heap memory management via FFM arenas. Backed by {@link OffHeapAllocator}. */
    public static final Capability OFF_HEAP_MEMORY = new Capability(
            "aetherium.compute.off_heap_memory",
            "Off-heap memory management via the FFM Arena API.");

    /** Asynchronous accelerated compute. Backed by {@link ComputePipeline}. */
    public static final Capability GPU_COMPUTE = new Capability(
            "aetherium.compute.gpu_compute",
            "Asynchronous GPU/accelerated compute pipeline.");
}
