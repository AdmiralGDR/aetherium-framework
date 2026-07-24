/*
 * Aetherium Framework — JUnit coverage for FFM memory-domain isolation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN: Locks in the Isolation pillar — cross-mod FFM access is denied by default and only an explicit,
 * owner-issued grant opens a neighbour's domain.
 * RU: Фиксирует столп изоляции — межмодовый FFM-доступ запрещён по умолчанию, и лишь явный грант
 * владельца открывает домен соседа.
 */
final class MemoryDomainTest {

    @Test
    void selfTestPasses() {
        assertTrue(MemoryDomainSelfTest.run().passed(), "memory-domain self-test must pass");
    }

    @Test
    void crossModAccessDeniedUntilGranted() {
        SecurityPolicy policy = SecurityPolicy.global();
        policy.reset();
        policy.grant(CapabilityGrant.of("owner", Capability.NATIVE_MEMORY));
        policy.grant(CapabilityGrant.of("intruder", Capability.NATIVE_MEMORY));
        try (MemoryDomainRegistry registry = MemoryDomainRegistry.create(policy)) {
            MemoryDomainHandle domain = registry.allocate("owner", 32);
            registry.open("owner", domain.domainId()).setLong(0, 0xDEADBEEFL);

            assertFalse(registry.canAccess("intruder", domain.domainId()));
            assertThrows(SecurityViolationException.class,
                    () -> registry.open("intruder", domain.domainId()),
                    "an unrelated mod must not open another's domain");

            registry.grantAccess("owner", domain.domainId(), "intruder");
            assertTrue(registry.canAccess("intruder", domain.domainId()));
            assertEquals(0xDEADBEEFL, registry.open("intruder", domain.domainId()).getLong(0));

            registry.revokeAccess("owner", domain.domainId(), "intruder");
            assertThrows(SecurityViolationException.class,
                    () -> registry.open("intruder", domain.domainId()));
        } finally {
            policy.reset();
        }
    }

    @Test
    void unknownDomainAndNonOwnerGrantAreRejected() {
        SecurityPolicy policy = SecurityPolicy.global();
        policy.reset();
        policy.grant(CapabilityGrant.of("a", Capability.NATIVE_MEMORY));
        policy.grant(CapabilityGrant.of("b", Capability.NATIVE_MEMORY));
        try (MemoryDomainRegistry registry = MemoryDomainRegistry.create(policy)) {
            assertThrows(SecurityViolationException.class,
                    () -> registry.open("a", UUID.randomUUID()), "unknown domain id is rejected");

            MemoryDomainHandle domain = registry.allocate("a", 16);
            assertThrows(SecurityViolationException.class,
                    () -> registry.grantAccess("b", domain.domainId(), "b"),
                    "only the owner may grant access");
        } finally {
            policy.reset();
        }
    }
}
