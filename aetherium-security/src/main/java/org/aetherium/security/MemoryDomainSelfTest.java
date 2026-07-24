/*
 * Aetherium Framework — FFM memory-domain isolation self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import java.util.ArrayList;
import java.util.List;

/**
 * A dependency-free proof of ACID Isolation for off-heap memory.
 *
 * <p>EN: {@code mod_a} allocates a private memory domain and writes to it; it can read its own domain.
 * {@code mod_b} — with the same base {@link Capability#NATIVE_MEMORY} grant — is nonetheless
 * <em>denied</em> when it tries to open {@code mod_a}'s domain (isolation by default). Only after
 * {@code mod_a} issues an explicit {@code grantAccess} can {@code mod_b} open it and read {@code mod_a}'s
 * value; a subsequent {@code revoke} re-seals it. It also proves the two guard rails: a mod without
 * {@link Capability#NATIVE_MEMORY} cannot allocate a domain at all, and a non-owner cannot grant access
 * to a domain it does not own.
 *
 * <p>RU: {@code mod_a} выделяет приватный домен памяти и пишет в него; свой домен он читает. {@code mod_b}
 * — с той же базовой возможностью {@link Capability#NATIVE_MEMORY} — тем не менее <em>получает отказ</em>
 * при попытке открыть домен {@code mod_a} (изоляция по умолчанию). Лишь после явного {@code grantAccess}
 * от {@code mod_a} мод {@code mod_b} открывает домен и читает значение {@code mod_a}; последующий
 * {@code revoke} снова его запечатывает. Также проверяются два ограждения: мод без
 * {@link Capability#NATIVE_MEMORY} не может выделить домен, а не-владелец не может выдать доступ.
 */
public final class MemoryDomainSelfTest {

    private MemoryDomainSelfTest() {
    }

    public record Result(boolean ownerReadOk,
                         int ownerValue,
                         boolean crossModDeniedByDefault,
                         boolean grantedAccessOk,
                         int granteeReadValue,
                         boolean revokeReSeals,
                         boolean uncapableCannotAllocate,
                         boolean nonOwnerCannotGrant,
                         List<String> notes) {
        public boolean passed() {
            return ownerReadOk && crossModDeniedByDefault && grantedAccessOk
                    && granteeReadValue == ownerValue && revokeReSeals
                    && uncapableCannotAllocate && nonOwnerCannotGrant;
        }
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();
        SecurityPolicy policy = SecurityPolicy.global();
        // Isolated policy state for the test.
        policy.reset();
        policy.grant(CapabilityGrant.of("mod_a", Capability.NATIVE_MEMORY));
        policy.grant(CapabilityGrant.of("mod_b", Capability.NATIVE_MEMORY));
        // mod_c intentionally holds no capabilities (default deny).

        try (MemoryDomainRegistry registry = MemoryDomainRegistry.create(policy)) {
            // mod_a allocates a private domain and writes a marker value.
            MemoryDomainHandle domainA = registry.allocate("mod_a", 64);
            final int marker = 0xA5A5;
            registry.open("mod_a", domainA.domainId()).setInt(0, marker);
            int ownerValue = registry.open("mod_a", domainA.domainId()).getInt(0);
            boolean ownerReadOk = ownerValue == marker;
            notes.add("mod_a allocated domain " + shortId(domainA) + " and read back 0x"
                    + Integer.toHexString(ownerValue).toUpperCase());

            // mod_b is denied by default — even though it holds NATIVE_MEMORY, it does not own this domain.
            boolean crossModDenied = deniedOpen(registry, "mod_b", domainA);
            notes.add("mod_b open(mod_a's domain) without grant -> "
                    + (crossModDenied ? "DENIED (isolation ✓)" : "ALLOWED (BUG)"));

            // mod_a explicitly grants mod_b access; now mod_b can read mod_a's value.
            registry.grantAccess("mod_a", domainA.domainId(), "mod_b");
            int granteeValue = registry.open("mod_b", domainA.domainId()).getInt(0);
            boolean grantedOk = granteeValue == marker;
            notes.add("mod_a grantAccess -> mod_b; mod_b read 0x"
                    + Integer.toHexString(granteeValue).toUpperCase() + " (== mod_a's value)");

            // Revoke re-seals the domain.
            registry.revokeAccess("mod_a", domainA.domainId(), "mod_b");
            boolean revokeReSeals = deniedOpen(registry, "mod_b", domainA);
            notes.add("mod_a revokeAccess -> mod_b open again -> "
                    + (revokeReSeals ? "DENIED (re-sealed ✓)" : "ALLOWED (BUG)"));

            // Guard rail 1: a mod without NATIVE_MEMORY cannot allocate a domain.
            boolean uncapableCannotAllocate;
            try {
                registry.allocate("mod_c", 32);
                uncapableCannotAllocate = false;
            } catch (SecurityViolationException expected) {
                uncapableCannotAllocate = true;
            }
            notes.add("mod_c (no NATIVE_MEMORY) allocate -> "
                    + (uncapableCannotAllocate ? "DENIED (default-deny ✓)" : "ALLOWED (BUG)"));

            // Guard rail 2: a non-owner cannot grant access to a domain it does not own.
            boolean nonOwnerCannotGrant;
            try {
                registry.grantAccess("mod_b", domainA.domainId(), "mod_c");
                nonOwnerCannotGrant = false;
            } catch (SecurityViolationException expected) {
                nonOwnerCannotGrant = true;
            }
            notes.add("mod_b (non-owner) grantAccess on mod_a's domain -> "
                    + (nonOwnerCannotGrant ? "DENIED (owner-only ✓)" : "ALLOWED (BUG)"));

            return new Result(ownerReadOk, ownerValue, crossModDenied, grantedOk, granteeValue,
                    revokeReSeals, uncapableCannotAllocate, nonOwnerCannotGrant, List.copyOf(notes));
        } finally {
            policy.reset();
        }
    }

    private static boolean deniedOpen(MemoryDomainRegistry registry, String modId, MemoryDomainHandle domain) {
        try {
            registry.open(modId, domain.domainId());
            return false;
        } catch (SecurityViolationException expected) {
            return true;
        }
    }

    private static String shortId(MemoryDomainHandle h) {
        return h.domainId().toString().substring(0, 8) + "…";
    }
}
