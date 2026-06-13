/*
 * Aetherium Framework — pure-Java compute pipeline (fallback tier).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.native_bridge.compute;

import org.aetherium.core.compute.ComputePipeline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.CompletableFuture;

/**
 * CPU implementation of {@link ComputePipeline} — the always-available fallback.
 *
 * <p>EN: Not accelerated. Runs the (placeholder) kernel on the JVM and returns an Arena-owned
 * output segment. Mod developers code against {@link ComputePipeline}; they never see whether this
 * or the native pipeline is in use (no-boilerplate goal). Outputs are owned by this pipeline's Arena
 * and freed on {@link #close()}.
 *
 * <p>RU: Без ускорения. Выполняет (заглушечное) ядро на JVM и возвращает выходной сегмент,
 * принадлежащий Arena. Мод-разработчики пишут под {@link ComputePipeline}; они не видят, что
 * используется — это или нативный конвейер (цель «ноль шаблонов»). Выходы принадлежат Arena этого
 * конвейера и освобождаются при {@link #close()}.
 */
public final class PureJavaComputePipeline implements ComputePipeline {

    private final Arena arena = Arena.ofShared();

    @Override
    public String backend() {
        return "cpu-fallback";
    }

    @Override
    public boolean isAccelerated() {
        return false;
    }

    @Override
    public CompletableFuture<MemorySegment> submit(ComputeJob job) {
        // Placeholder kernel: produce an output buffer of the requested size, doubling each input
        // byte (mirrors the dispatch "doubler" so behaviour is verifiable end-to-end).
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
