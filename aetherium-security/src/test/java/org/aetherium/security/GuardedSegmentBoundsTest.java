/*
 * Aetherium Framework — GuardedSegment overflow-safe bounds tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class GuardedSegmentBoundsTest {

    @Test
    void boundsCheckIsOverflowSafe() {
        SecurityPolicy policy = SecurityPolicy.global();
        policy.grant(CapabilityGrant.of("bounds_probe_mod", Capability.NATIVE_MEMORY));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(64);
            GuardedSegment g = GuardedSegment.grant(policy, "bounds_probe_mod", seg);

            g.setInt(0, 42);
            assertEquals(42, g.getInt(0));
            g.setInt(60, 7); // 60 + 4 == 64 == byteSize: the last valid int slot

            assertThrows(SecurityViolationException.class, () -> g.getInt(61), "61 + 4 = 65 > 64 must be rejected");
            assertThrows(SecurityViolationException.class, () -> g.getInt(-1), "a negative offset must be rejected");

            // The overflow case: a near-Long.MAX_VALUE offset must be reported as a SecurityViolationException,
            // NOT slip past the guard's own check (offset + width would wrap negative) into the FFM backstop.
            assertThrows(SecurityViolationException.class, () -> g.getLong(Long.MAX_VALUE - 2));
            assertThrows(SecurityViolationException.class, () -> g.setDouble(Long.MAX_VALUE - 1, 1.0));
        }
    }
}
