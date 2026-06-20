/*
 * Aetherium Framework — an immutable capability grant for one mod.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The set of {@link Capability}s a single mod has been granted — immutable once built.
 *
 * <p>EN: Produced by the {@link SecurityPolicy} from a mod's declared, user-approved permissions. It is
 * the authority a mod actually holds; the guards consult it. {@link #none(String)} is the secure
 * default (a mod with no manifest permissions can do nothing privileged).
 *
 * <p>RU: Создаётся {@link SecurityPolicy} из заявленных, одобренных пользователем разрешений мода. Это
 * фактические полномочия мода; охраны сверяются с ним. {@link #none(String)} — безопасный дефолт.
 *
 * @param modId        the owning mod's id
 * @param capabilities the granted capabilities (defensively copied)
 */
public record CapabilityGrant(String modId, Set<Capability> capabilities) {

    public CapabilityGrant {
        Objects.requireNonNull(modId, "modId");
        capabilities = capabilities == null || capabilities.isEmpty()
                ? EnumSet.noneOf(Capability.class)
                : EnumSet.copyOf(capabilities);
    }

    /** A grant with no privileges (the secure default). */
    public static CapabilityGrant none(String modId) {
        return new CapabilityGrant(modId, EnumSet.noneOf(Capability.class));
    }

    /** A grant of exactly the given capabilities. */
    public static CapabilityGrant of(String modId, Capability... caps) {
        EnumSet<Capability> set = EnumSet.noneOf(Capability.class);
        for (Capability c : caps) {
            set.add(c);
        }
        return new CapabilityGrant(modId, set);
    }

    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }
}
