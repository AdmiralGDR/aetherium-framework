/*
 * Aetherium Framework — capability-checked, bounds-enforcing FFM memory view.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * A capability-gated, bounds-enforcing wrapper over an off-heap {@link MemorySegment} — the Integrity
 * guard of the CIA triad.
 *
 * <p>EN: FFM gives mods raw off-heap power; uncontrolled, a stray offset is a memory-safety hole. A
 * {@code GuardedSegment} (1) is only constructible by a mod that holds {@link Capability#NATIVE_MEMORY},
 * and (2) re-checks every access against the granted region's bounds, converting any escape attempt into
 * a contained {@link SecurityViolationException} rather than undefined behavior. The mod is handed this
 * view, never the raw segment, so it can touch <em>only</em> the memory the framework granted it.
 *
 * <p>RU: FFM даёт модам сырую off-heap мощь; без контроля случайное смещение — дыра в безопасности
 * памяти. {@code GuardedSegment} (1) создаётся только модом с {@link Capability#NATIVE_MEMORY} и
 * (2) перепроверяет каждый доступ по границам выданной области, превращая любую попытку выхода в
 * локализованное {@link SecurityViolationException}. Моду передаётся это представление, а не сырой
 * сегмент, поэтому он касается <em>только</em> выданной памяти.
 */
public final class GuardedSegment {

    private final MemorySegment segment;
    private final long byteSize;
    private final String modId;

    private GuardedSegment(MemorySegment segment, String modId) {
        this.segment = segment;
        this.byteSize = segment.byteSize();
        this.modId = modId;
    }

    /**
     * Grant a bounds-checked view of {@code segment} to {@code modId}. Requires the mod to hold
     * {@link Capability#NATIVE_MEMORY} under the given policy, else {@link SecurityViolationException}.
     */
    public static GuardedSegment grant(SecurityPolicy policy, String modId, MemorySegment segment) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(segment, "segment");
        policy.require(modId, Capability.NATIVE_MEMORY);
        return new GuardedSegment(segment, modId);
    }

    public long byteSize() {
        return byteSize;
    }

    public int getInt(long offset) {
        checkBounds(offset, Integer.BYTES);
        return segment.get(ValueLayout.JAVA_INT, offset);
    }

    public void setInt(long offset, int value) {
        checkBounds(offset, Integer.BYTES);
        segment.set(ValueLayout.JAVA_INT, offset, value);
    }

    public long getLong(long offset) {
        checkBounds(offset, Long.BYTES);
        return segment.get(ValueLayout.JAVA_LONG, offset);
    }

    public void setLong(long offset, long value) {
        checkBounds(offset, Long.BYTES);
        segment.set(ValueLayout.JAVA_LONG, offset, value);
    }

    public float getFloat(long offset) {
        checkBounds(offset, Float.BYTES);
        return segment.get(ValueLayout.JAVA_FLOAT, offset);
    }

    public void setFloat(long offset, float value) {
        checkBounds(offset, Float.BYTES);
        segment.set(ValueLayout.JAVA_FLOAT, offset, value);
    }

    public double getDouble(long offset) {
        checkBounds(offset, Double.BYTES);
        return segment.get(ValueLayout.JAVA_DOUBLE, offset);
    }

    public void setDouble(long offset, double value) {
        checkBounds(offset, Double.BYTES);
        segment.set(ValueLayout.JAVA_DOUBLE, offset, value);
    }

    private void checkBounds(long offset, int width) {
        if (offset < 0 || offset + width > byteSize) {
            throw new SecurityViolationException("mod '" + modId + "' attempted out-of-bounds FFM access at offset "
                    + offset + " (width " + width + ") on a " + byteSize + "-byte granted region");
        }
    }
}
