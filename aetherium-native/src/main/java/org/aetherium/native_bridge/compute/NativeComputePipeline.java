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
 * <p>EN: Real GPU compute dispatch now exists (WS-6): {@code aeth_vk_dispatch} in the Zig native
 * bridge runs a compiled SPIR-V kernel on a real Vulkan compute queue (bind SSBOs → pipeline → submit →
 * readback), reached via {@code NativeBridge.dispatchUnary} and proven by the {@code computegpu} self-test
 * (GPU result == CPU, dependency-free). This {@link #submit} overload still runs the CPU kernel only because
 * its {@link ComputeJob} carries raw bytes + a {@code kernelId}, not a SPIR-V binary — threading SPIR-V
 * through the high-level job contract is the remaining integration step; the low-level dispatch it would call
 * is done. {@link #isAccelerated()} truthfully reflects whether a usable Vulkan device exists.
 *
 * <p>RU: Реальный GPU-диспатч теперь есть (Фаза 24 WS-6): {@code aeth_vk_dispatch} в Zig-мосте исполняет
 * скомпилированное SPIR-V-ядро на настоящей вычислительной очереди Vulkan (SSBO → пайплайн → submit →
 * readback), доступен через {@code NativeBridge.dispatchUnary} и доказан self-тестом {@code computegpu}
 * (результат GPU == CPU, без зависимостей). Этот {@link #submit} пока выполняет CPU-ядро лишь потому, что его
 * {@link ComputeJob} несёт сырые байты + {@code kernelId}, а не бинарь SPIR-V — проброс SPIR-V через
 * высокоуровневый контракт задачи остаётся; сам низкоуровневый диспатч готов.
 */
public final class NativeComputePipeline implements ComputePipeline {

    private final Arena arena = Arena.ofShared();
    private final VulkanProbe probe;

    public NativeComputePipeline(VulkanProbe probe) {
        this.probe = probe;
    }

    @Override
    public String backend() {
        // Real dispatch is available via NativeBridge.dispatchUnary (SPIR-V path); this byte-job submit()
        // still runs on the CPU pending SPIR-V threading through ComputeJob.
        return probe.hasUsableDevice() ? "vulkan-compute (dispatch via SPIR-V; byte-job on cpu)" : "ffm-native (cpu)";
    }

    @Override
    public boolean isAccelerated() {
        return probe.hasUsableDevice();
    }

    @Override
    public CompletableFuture<MemorySegment> submit(ComputeJob job) {
        // Real GPU dispatch lives in NativeBridge.dispatchUnary (WS-6, verified GPU==CPU). This
        // byte-oriented ComputeJob has no SPIR-V, so it runs the CPU kernel; wiring a kernelId→SPIR-V
        // registry into this path is the remaining step.
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
