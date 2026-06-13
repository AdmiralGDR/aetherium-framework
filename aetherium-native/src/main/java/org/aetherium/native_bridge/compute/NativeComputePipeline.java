/*
 * Aetherium Framework — native compute pipeline (FFM tier, Vulkan scaffold).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.native_bridge.compute;

import org.aetherium.core.compute.ComputePipeline;
import org.aetherium.native_bridge.VulkanProbe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.CompletableFuture;

/**
 * FFM/native implementation of {@link ComputePipeline} — <strong>hardware-access scaffold</strong>.
 *
 * <p>EN: The <em>reliable hardware-access layer</em> is in place ({@link VulkanProbe} reports the
 * device surface), but the GPU shader/dispatch path is intentionally NOT implemented yet. To keep
 * results correct and verifiable today, {@link #submit} runs the same placeholder kernel as the CPU
 * pipeline while {@link #isAccelerated()} truthfully reflects whether a usable Vulkan device exists.
 * When the compute shaders land, only this class changes — the {@link ComputePipeline} contract mod
 * developers depend on does not.
 *
 * <p>RU: <em>Надёжный слой доступа к оборудованию</em> готов ({@link VulkanProbe} сообщает о
 * поверхности устройства), но путь GPU-шейдеров/диспетчеризации намеренно ещё НЕ реализован. Чтобы
 * результаты были корректны и проверяемы уже сейчас, {@link #submit} выполняет то же заглушечное
 * ядро, что и CPU-конвейер, тогда как {@link #isAccelerated()} честно отражает наличие пригодного
 * Vulkan-устройства. Когда появятся вычислительные шейдеры, изменится только этот класс — контракт
 * {@link ComputePipeline}, от которого зависят мод-разработчики, останется прежним.
 */
public final class NativeComputePipeline implements ComputePipeline {

    private final Arena arena = Arena.ofShared();
    private final VulkanProbe probe;

    public NativeComputePipeline(VulkanProbe probe) {
        this.probe = probe;
    }

    @Override
    public String backend() {
        return probe.hasUsableDevice() ? "vulkan-compute (scaffold)" : "ffm-native (cpu)";
    }

    @Override
    public boolean isAccelerated() {
        return probe.hasUsableDevice();
    }

    @Override
    public CompletableFuture<MemorySegment> submit(ComputeJob job) {
        // TODO(compute): dispatch to a Vulkan compute shader once the kernel layer lands.
        // Until then, run the placeholder kernel so behaviour is correct and testable.
        return CompletableFuture.supplyAsync(() -> {
            MemorySegment out = arena.allocate(job.outputByteSize());
            long n = Math.min(job.input().byteSize(), job.outputByteSize());
            for (long i = 0; i < n; i++) {
                byte v = job.input().get(ValueLayout.JAVA_BYTE, i);
                out.set(ValueLayout.JAVA_BYTE, i, (byte) (v * 2));
            }
            return out;
        });
    }

    @Override
    public void close() {
        arena.close();
    }
}
