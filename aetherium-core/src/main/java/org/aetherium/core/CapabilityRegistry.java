package org.aetherium.core;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry mapping each {@link Capability} to its resolved {@link CapabilityProvider}.
 *
 * <p>EN: Capabilities are registered with a {@link FallbackChain}; the first {@link #resolve} call
 * probes the chain once and memoizes the winning provider, so every later lookup is {@code O(1)}
 * with no re-probing. {@link #tierOf} reports {@link CapabilityTier#DISABLED} for anything that
 * cannot be resolved, keeping callers branch-light and crash-free.
 *
 * <p>RU: Возможности регистрируются с {@link FallbackChain}; первый вызов {@link #resolve}
 * зондирует цепочку один раз и запоминает победивший провайдер, поэтому каждый последующий поиск —
 * {@code O(1)} без повторного зондирования. {@link #tierOf} возвращает
 * {@link CapabilityTier#DISABLED} для неразрешимого, оставляя вызывающий код простым и
 * устойчивым к сбоям.
 */
public final class CapabilityRegistry {

    private final Map<String, FallbackChain<?>> chains = new ConcurrentHashMap<>();
    private final Map<String, CapabilityProvider> resolved = new ConcurrentHashMap<>();

    /** Register the fallback chain that backs a capability. Later registration replaces earlier. */
    public <P extends CapabilityProvider> void register(Capability capability, FallbackChain<P> chain) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(chain, "chain");
        chains.put(capability.id(), chain);
        resolved.remove(capability.id()); // invalidate any memoized resolution
    }

    /** Resolve (and memoize) the active provider for a capability. */
    public Optional<CapabilityProvider> resolve(Capability capability) {
        Objects.requireNonNull(capability, "capability");
        CapabilityProvider cached = resolved.get(capability.id());
        if (cached != null) {
            return Optional.of(cached);
        }
        FallbackChain<?> chain = chains.get(capability.id());
        if (chain == null) {
            return Optional.empty();
        }
        Optional<? extends CapabilityProvider> winner = chain.resolve();
        winner.ifPresent(provider -> resolved.put(capability.id(), provider));
        return winner.map(provider -> (CapabilityProvider) provider);
    }

    /** Active tier for a capability, or {@link CapabilityTier#DISABLED} if unresolved. */
    public CapabilityTier tierOf(Capability capability) {
        return resolve(capability).map(CapabilityProvider::tier).orElse(CapabilityTier.DISABLED);
    }

    /** True if the capability resolves to anything other than {@link CapabilityTier#DISABLED}. */
    public boolean isEnabled(Capability capability) {
        return tierOf(capability) != CapabilityTier.DISABLED;
    }
}
