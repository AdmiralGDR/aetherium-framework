package org.aetherium.core.compute;

import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous GPU/accelerated compute pipeline — <strong>placeholder contract</strong>.
 *
 * <p>EN: Defines the surface developers will use for off-loadable, data-parallel work without
 * binding to a specific backend. Jobs take an off-heap input {@link MemorySegment} and complete
 * asynchronously with an output segment. The capability is gated through
 * {@link org.aetherium.core.CapabilityRegistry}; when no accelerated tier is available the runtime
 * supplies a CPU pipeline ({@code isAccelerated() == false}) so callers never special-case
 * "no GPU". No backend is implemented in this phase.
 *
 * <p>RU: Определяет поверхность, которую разработчики будут использовать для выгружаемой
 * data-parallel работы, не привязываясь к конкретному бэкенду. Задания принимают off-heap входной
 * {@link MemorySegment} и завершаются асинхронно с выходным сегментом. Возможность гейтится через
 * {@link org.aetherium.core.CapabilityRegistry}; при отсутствии ускоренного уровня среда
 * предоставляет CPU-конвейер ({@code isAccelerated() == false}), поэтому вызывающий код не делает
 * особых случаев для «нет GPU». Бэкенд на этом этапе не реализован.
 */
public interface ComputePipeline extends AutoCloseable {

    /** Backend identifier, e.g. {@code "vulkan-compute"}, {@code "opencl"}, {@code "cpu-fallback"}. */
    String backend();

    /** True if work runs on a hardware accelerator rather than the CPU fallback. */
    boolean isAccelerated();

    /** Submit a job; completes asynchronously with the output segment (or completes exceptionally). */
    CompletableFuture<MemorySegment> submit(ComputeJob job);

    @Override
    void close();

    /**
     * A unit of compute work.
     *
     * @param kernelId       identifier of the kernel/program to run
     * @param input          off-heap input data
     * @param outputByteSize size of the output buffer to produce
     */
    record ComputeJob(String kernelId, MemorySegment input, long outputByteSize) {
        public ComputeJob {
            Objects.requireNonNull(kernelId, "kernelId");
            Objects.requireNonNull(input, "input");
            if (outputByteSize < 0) {
                throw new IllegalArgumentException("outputByteSize must be >= 0: " + outputByteSize);
            }
        }
    }
}
