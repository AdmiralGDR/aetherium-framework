/*
 * Aetherium Framework — capability-isolated FFM memory domains (ACID Isolation for off-heap memory).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Partitions off-heap memory into per-mod domains that are isolated by capability — no mod can read or
 * write another mod's FFM segment without an explicit, owner-issued grant.
 *
 * <p>EN: The base {@link GuardedSegment} stops a mod from stepping <em>outside</em> a segment it was
 * handed; it does nothing about a mod reaching into a segment that belongs to a <em>different</em> mod.
 * This registry closes that gap — the Isolation pillar of the ACID engine. Each
 * {@link #allocate(String, long) allocate} mints a domain tagged with a random {@link UUID} capability
 * ({@link MemoryDomainHandle}); {@link #open(String, UUID) open} returns a bounds-checked
 * {@link GuardedSegment} <strong>only</strong> to the owner or to a mod the owner has
 * {@link #grantAccess(String, UUID, String) explicitly granted}. Every other request is refused with a
 * contained {@link SecurityViolationException} — a stray or malicious cross-mod pointer becomes a caught
 * error, never silent corruption of a neighbour's state (the "silent state corruption" the ACID engine
 * eradicates). Allocation additionally requires the base {@link Capability#NATIVE_MEMORY} grant.
 *
 * <p>RU: Базовый {@link GuardedSegment} не даёт моду выйти <em>за пределы</em> выданного сегмента, но
 * никак не мешает моду дотянуться до сегмента <em>другого</em> мода. Этот реестр закрывает пробел — столп
 * изоляции. Каждое {@link #allocate(String, long)} создаёт домен со случайным {@link UUID}-возможностью
 * ({@link MemoryDomainHandle}); {@link #open(String, UUID)} возвращает {@link GuardedSegment} с проверкой
 * границ <strong>только</strong> владельцу или моду, которому владелец
 * {@link #grantAccess(String, UUID, String) явно выдал} доступ. Любой другой запрос отклоняется
 * локализованным {@link SecurityViolationException}. Выделение дополнительно требует базовой возможности
 * {@link Capability#NATIVE_MEMORY}.
 */
public final class MemoryDomainRegistry implements AutoCloseable {

    private record Domain(UUID id, String owner, MemorySegment segment) {
    }

    private final SecurityPolicy policy;
    private final Arena arena;
    private final boolean ownArena;
    private final ConcurrentHashMap<UUID, Domain> domains = new ConcurrentHashMap<>();
    // Explicit cross-mod grants: domainId -> set of grantee modIds allowed to open it.
    private final ConcurrentHashMap<UUID, Set<String>> grants = new ConcurrentHashMap<>();

    private MemoryDomainRegistry(SecurityPolicy policy, Arena arena, boolean ownArena) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.arena = Objects.requireNonNull(arena, "arena");
        this.ownArena = ownArena;
    }

    /** A registry backed by its own shared arena (closed with the registry). */
    public static MemoryDomainRegistry create(SecurityPolicy policy) {
        return new MemoryDomainRegistry(policy, Arena.ofShared(), true);
    }

    /** A registry backed by a caller-owned arena (the caller manages its lifetime). */
    public static MemoryDomainRegistry usingArena(SecurityPolicy policy, Arena arena) {
        return new MemoryDomainRegistry(policy, arena, false);
    }

    /**
     * Allocate a fresh, isolated {@code byteSize}-byte domain owned by {@code modId}. The mod must hold
     * {@link Capability#NATIVE_MEMORY}. Returns the owner's capability handle.
     */
    public MemoryDomainHandle allocate(String modId, long byteSize) {
        Objects.requireNonNull(modId, "modId");
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must be >= 0: " + byteSize);
        }
        // Base authority: only mods trusted with native memory may carve off-heap domains at all.
        policy.require(modId, Capability.NATIVE_MEMORY);
        UUID id = UUID.randomUUID();
        MemorySegment segment = arena.allocate(Math.max(byteSize, 1));
        // Present the domain at exactly the requested size (a zero-length domain is still bounds-safe).
        MemorySegment view = segment.asSlice(0, byteSize);
        domains.put(id, new Domain(id, modId, view));
        return new MemoryDomainHandle(id, modId, byteSize);
    }

    /**
     * Open a bounds-checked view of the domain {@code domainId} on behalf of {@code requesterModId}.
     *
     * @throws SecurityViolationException if the domain is unknown, or the requester is neither the owner
     *                                    nor an explicitly granted mod
     */
    public GuardedSegment open(String requesterModId, UUID domainId) {
        Objects.requireNonNull(requesterModId, "requesterModId");
        Objects.requireNonNull(domainId, "domainId");
        Domain domain = domains.get(domainId);
        if (domain == null) {
            throw new SecurityViolationException(
                    "mod '" + requesterModId + "' referenced unknown memory domain " + domainId);
        }
        boolean owner = domain.owner().equals(requesterModId);
        boolean granted = grants.getOrDefault(domainId, Set.of()).contains(requesterModId);
        if (!owner && !granted) {
            throw new SecurityViolationException("mod '" + requesterModId
                    + "' attempted to access memory domain " + domainId + " owned by '" + domain.owner()
                    + "' without a CapabilityGrant (cross-domain isolation)");
        }
        // Reuse the Integrity guard: the view is bounds-checked and tagged with the requester.
        return GuardedSegment.grant(policy, requesterModId, domain.segment());
    }

    /**
     * The owner of {@code domainId} explicitly grants {@code granteeModId} the right to open it.
     *
     * @throws SecurityViolationException if the caller is not the domain's owner
     */
    public void grantAccess(String ownerModId, UUID domainId, String granteeModId) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(granteeModId, "granteeModId");
        Domain domain = requireOwned(ownerModId, domainId, "grant access to");
        grants.computeIfAbsent(domain.id(), k -> ConcurrentHashMap.newKeySet()).add(granteeModId);
    }

    /** The owner of {@code domainId} revokes {@code granteeModId}'s previously granted access. */
    public void revokeAccess(String ownerModId, UUID domainId, String granteeModId) {
        Domain domain = requireOwned(ownerModId, domainId, "revoke access to");
        Set<String> set = grants.get(domain.id());
        if (set != null) {
            set.remove(granteeModId);
        }
    }

    /** True if {@code modId} may open {@code domainId} right now (owner or granted). */
    public boolean canAccess(String modId, UUID domainId) {
        Domain domain = domains.get(domainId);
        if (domain == null) {
            return false;
        }
        return domain.owner().equals(modId)
                || grants.getOrDefault(domainId, Set.of()).contains(modId);
    }

    /** Number of live domains. */
    public int domainCount() {
        return domains.size();
    }

    private Domain requireOwned(String modId, UUID domainId, String action) {
        Domain domain = domains.get(domainId);
        if (domain == null) {
            throw new SecurityViolationException(
                    "mod '" + modId + "' referenced unknown memory domain " + domainId);
        }
        if (!domain.owner().equals(modId)) {
            throw new SecurityViolationException("mod '" + modId + "' cannot " + action + " domain "
                    + domainId + ": it is owned by '" + domain.owner() + "' (only the owner may)");
        }
        return domain;
    }

    @Override
    public void close() {
        domains.clear();
        grants.clear();
        if (ownArena) {
            arena.close();
        }
    }
}
