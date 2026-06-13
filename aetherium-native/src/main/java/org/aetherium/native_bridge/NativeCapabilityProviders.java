/*
 * Aetherium Framework — capability providers for the native tier.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.native_bridge;

import org.aetherium.core.CapabilityProvider;
import org.aetherium.core.CapabilityTier;
import org.aetherium.core.FallbackChain;

/**
 * Capability providers wiring the {@code FFM → PURE_JAVA} ladder for compute capabilities.
 *
 * <p>EN: The {@link FallbackChain} resolves to {@link #ffm()} when the native bridge probes healthy,
 * otherwise to {@link #pureJava()}. This is exactly the graceful-degradation path consumed by the
 * {@code CapabilityRegistry}. The FFM probe is the same one the Pre-Flight Check uses, so the tier
 * decision is consistent.
 *
 * <p>RU: {@link FallbackChain} разрешается в {@link #ffm()}, когда нативный мост проходит зонд,
 * иначе — в {@link #pureJava()}. Это и есть путь мягкой деградации, используемый
 * {@code CapabilityRegistry}. FFM-зонд тот же, что и в Pre-Flight Check, поэтому решение об уровне
 * согласовано.
 */
public final class NativeCapabilityProviders {

    private NativeCapabilityProviders() {
    }

    /** The FFM/native provider — available only if the native probe is healthy. */
    public static CapabilityProvider ffm() {
        return new CapabilityProvider() {
            @Override
            public CapabilityTier tier() {
                return CapabilityTier.FFM;
            }

            @Override
            public boolean isAvailable() {
                return NativeProbe.run().healthy();
            }
        };
    }

    /** The pure-Java provider — always available; the floor of the ladder. */
    public static CapabilityProvider pureJava() {
        return new CapabilityProvider() {
            @Override
            public CapabilityTier tier() {
                return CapabilityTier.PURE_JAVA;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
    }

    /** The full compute fallback chain: prefer FFM, fall back to pure Java. */
    public static FallbackChain<CapabilityProvider> computeChain() {
        return FallbackChain.of(ffm(), pureJava());
    }
}
