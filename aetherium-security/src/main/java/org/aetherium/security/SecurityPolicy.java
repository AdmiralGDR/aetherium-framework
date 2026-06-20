/*
 * Aetherium Framework — the default-deny capability policy registry.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The central, default-deny registry mapping each mod to its {@link CapabilityGrant} — the policy the
 * guards enforce.
 *
 * <p>EN: A mod that was never granted anything resolves to {@link CapabilityGrant#none}, so every
 * privileged action is refused unless explicitly allowed (Confidentiality + Integrity by default).
 * {@link #require(String, Capability)} is the single checkpoint the framework calls before acting on a
 * mod's behalf; it throws {@link SecurityViolationException} when authority is missing. The policy is a
 * process-wide singleton populated once at load time from approved mod manifests.
 *
 * <p>RU: Центральный реестр default-deny, сопоставляющий каждому моду его {@link CapabilityGrant}.
 * Незарегистрированный мод получает {@link CapabilityGrant#none}, поэтому любое привилегированное
 * действие запрещено, пока явно не разрешено. {@link #require(String, Capability)} — единственная
 * контрольная точка; бросает {@link SecurityViolationException} при отсутствии полномочий.
 */
public final class SecurityPolicy {

    private static final SecurityPolicy INSTANCE = new SecurityPolicy();

    private final Map<String, CapabilityGrant> grants = new ConcurrentHashMap<>();

    private SecurityPolicy() {
    }

    public static SecurityPolicy global() {
        return INSTANCE;
    }

    /** Register (or replace) a mod's grant. Called once at load time from approved manifests. */
    public void grant(CapabilityGrant grant) {
        Objects.requireNonNull(grant, "grant");
        grants.put(grant.modId(), grant);
    }

    /** The grant for a mod, or {@link CapabilityGrant#none} if it was never registered (default deny). */
    public CapabilityGrant grantFor(String modId) {
        return grants.getOrDefault(modId, CapabilityGrant.none(modId));
    }

    /** True if {@code modId} holds {@code capability}. */
    public boolean allows(String modId, Capability capability) {
        return grantFor(modId).has(capability);
    }

    /**
     * The enforcement checkpoint: returns normally if {@code modId} holds {@code capability}, otherwise
     * throws {@link SecurityViolationException}.
     */
    public void require(String modId, Capability capability) {
        if (!allows(modId, capability)) {
            throw new SecurityViolationException(
                    "mod '" + modId + "' lacks capability " + capability + " (default-deny policy)");
        }
    }

    /** Clear all grants (test/relaunch support). */
    public void reset() {
        grants.clear();
    }
}
