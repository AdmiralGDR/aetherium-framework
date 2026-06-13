package org.aetherium.core;

import java.util.Objects;

/**
 * A capability the framework may provide at one of several {@link CapabilityTier tiers}.
 *
 * <p>EN: A capability is identified by a stable, namespaced {@code id} (e.g.
 * {@code "aetherium.compute.gpu_compute"}). It is just a descriptor — the actual implementation is
 * supplied by {@link CapabilityProvider}s resolved through a {@link FallbackChain} and registered
 * in the {@link CapabilityRegistry}.
 *
 * <p>RU: Возможность идентифицируется стабильным пространственно-именованным {@code id} (напр.
 * {@code "aetherium.compute.gpu_compute"}). Это лишь дескриптор — реальная реализация
 * поставляется {@link CapabilityProvider}, разрешаемыми через {@link FallbackChain} и
 * регистрируемыми в {@link CapabilityRegistry}.
 *
 * @param id          stable, namespaced identifier
 * @param description human-readable purpose
 */
public record Capability(String id, String description) {

    public Capability {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Capability id must not be blank");
        }
    }
}
