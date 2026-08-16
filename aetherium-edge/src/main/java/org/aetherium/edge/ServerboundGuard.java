/*
 * Aetherium Framework — per-sender flood guard for inbound serverbound packets.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A per-sender, per-channel token-bucket rate limiter that protects a serverbound channel from a flooding
 * client (protection). A serverbound channel is a new attack surface — a malicious client can push
 * admin packets as fast as it likes — so the framework drops floods <em>before</em> the mod handler ever runs,
 * making the channel safe-by-default without the author writing the check.
 *
 * <p>EN: Each {@code (channelId, sender)} pair gets a bucket of {@code burst} tokens that refills at
 * {@code refillPerSec}. Each accepted packet spends one token; when the bucket is empty the packet is dropped.
 * Pure, deterministic given a supplied {@code nowMillis} (so it is unit-testable off-platform).
 *
 * <p>RU: Токен-бакет на пару {@code (канал, отправитель)}: {@code burst} токенов, пополняется со скоростью
 * {@code refillPerSec}; каждый принятый пакет тратит токен, при пустом бакете пакет отбрасывается. Чисто и
 * детерминированно при переданном {@code nowMillis} — тестируется без игры.
 */
public final class ServerboundGuard {

    /** Default burst: a client may send up to this many packets on one channel back-to-back. */
    public static final double DEFAULT_BURST = 32.0;
    /** Default sustained rate: tokens refilled per second per (channel, sender). */
    public static final double DEFAULT_REFILL_PER_SEC = 16.0;

    /**
     * When the live bucket count first exceeds this, one caller sweeps fully-refilled (idle) buckets.
     * A flood guard that never forgets a sender is itself a slow memory-exhaustion vector, so the map is
     * self-bounded — but only past a high-water mark, keeping the steady-state hot path allocation-free.
     */
    static final int SWEEP_THRESHOLD = 4096;

    private final double burst;
    private final double refillPerSec;
    /** key {@code channelId + '\0' + uuid} → {@code [tokens, lastMillis]}. */
    private final ConcurrentHashMap<String, double[]> buckets = new ConcurrentHashMap<>();
    /** Ensures at most one sweep runs at a time; other callers skip rather than pile on. */
    private final AtomicBoolean sweeping = new AtomicBoolean(false);

    public ServerboundGuard() {
        this(DEFAULT_BURST, DEFAULT_REFILL_PER_SEC);
    }

    public ServerboundGuard(double burst, double refillPerSec) {
        this.burst = Math.max(1.0, burst);
        this.refillPerSec = Math.max(0.0, refillPerSec);
    }

    /**
     * Whether a packet from {@code sender} on {@code channelId} is allowed at {@code nowMillis} — {@code true}
     * spends a token; {@code false} means the bucket is empty (drop the packet).
     */
    public boolean allow(String channelId, UUID sender, long nowMillis) {
        String key = channelId + '\0' + sender;
        double[] bucket = buckets.computeIfAbsent(key, k -> new double[] {burst, nowMillis});
        boolean allowed;
        synchronized (bucket) {
            double elapsedSec = Math.max(0L, nowMillis - (long) bucket[1]) / 1000.0;
            bucket[0] = Math.min(burst, bucket[0] + elapsedSec * refillPerSec);
            bucket[1] = nowMillis;
            allowed = bucket[0] >= 1.0;
            if (allowed) {
                bucket[0] -= 1.0;
            }
        }
        // Reclaim idle senders once the map is large. Done outside the token math (never holding a bucket
        // lock across an O(n) pass) and by a single caller at a time, so it never stalls the hot path.
        if (buckets.size() > SWEEP_THRESHOLD && sweeping.compareAndSet(false, true)) {
            try {
                sweepIdle(nowMillis);
            } finally {
                sweeping.set(false);
            }
        }
        return allowed;
    }

    /**
     * Drop every bucket that is fully refilled as of {@code nowMillis}. Such a bucket is byte-identical to
     * the {@code {burst, nowMillis}} a fresh sender would get, so removing it changes no future decision —
     * a re-created bucket is the one we removed. The conditional {@link Map#remove(Object, Object)} skips any
     * entry a concurrent {@link #allow} has already replaced, and the per-bucket lock keeps the token read
     * consistent with that path.
     */
    private void sweepIdle(long nowMillis) {
        for (Map.Entry<String, double[]> entry : buckets.entrySet()) {
            double[] bucket = entry.getValue();
            synchronized (bucket) {
                double elapsedSec = Math.max(0L, nowMillis - (long) bucket[1]) / 1000.0;
                if (Math.min(burst, bucket[0] + elapsedSec * refillPerSec) >= burst) {
                    buckets.remove(entry.getKey(), bucket);
                }
            }
        }
    }

    /** Number of live per-sender buckets currently tracked (visibility for tests / metrics). */
    public int trackedBuckets() {
        return buckets.size();
    }

    /** Forget all buckets (test hook / between runs). */
    public void reset() {
        buckets.clear();
    }
}
