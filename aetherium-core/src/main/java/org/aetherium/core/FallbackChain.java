package org.aetherium.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An ordered chain of {@link CapabilityProvider}s tried highest-preference first.
 *
 * <p>EN: {@link #resolve()} returns the first provider whose probe reports available, swallowing
 * probe exceptions so a broken tier degrades to the next one rather than aborting — the layered
 * fallback of {@code ARCHITECTURE.md} Resolution is a load-phase operation; callers cache the
 * result for {@code O(1)} steady-state use.
 *
 * <p>RU: {@link #resolve()} возвращает первого провайдера, чей зонд сообщает о доступности,
 * поглощая исключения зонда, чтобы сломанный уровень деградировал к следующему, а не прерывал
 * работу — слоистый откат из {@code ARCHITECTURE.md} Разрешение — операция фазы загрузки;
 * вызывающая сторона кэширует результат для использования за {@code O(1)} в устойчивом режиме.
 *
 * @param <P> the provider type carried by this chain
 */
public final class FallbackChain<P extends CapabilityProvider> {

    private final List<P> providers;

    private FallbackChain(List<P> providers) {
        this.providers = providers;
    }

    /** Build a chain; providers are sorted by {@link CapabilityProvider#priority()} ascending. */
    @SafeVarargs
    public static <P extends CapabilityProvider> FallbackChain<P> of(P... providers) {
        // Copy element-wise rather than through List.of(providers): passing the non-reifiable P[] into
        // another varargs method is the [varargs] heap-pollution warning javac emits here, which a
        // zero-warning build policy treats as fatal. Exact-capacity allocation also avoids ArrayList
        // regrowth, and requireNonNull preserves List.of's fail-fast on nulls.
        List<P> sorted = new ArrayList<>(providers.length);
        for (P provider : providers) {
            sorted.add(Objects.requireNonNull(provider, "provider"));
        }
        sorted.sort(Comparator.comparingInt(CapabilityProvider::priority));
        return new FallbackChain<>(List.copyOf(sorted));
    }

    /** First available provider, or empty if every tier is unavailable. */
    public Optional<P> resolve() {
        for (P provider : providers) {
            try {
                if (provider.isAvailable()) {
                    return Optional.of(provider);
                }
            } catch (RuntimeException probeFailure) {
                // A failing probe must never crash resolution: fall through to the next tier.
            }
        }
        return Optional.empty();
    }

    /** Immutable, priority-ordered view of the providers in this chain. */
    public List<P> providers() {
        return providers;
    }
}
