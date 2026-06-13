package org.aetherium.core;

/**
 * A concrete provider of a {@link Capability} at a specific {@link CapabilityTier}.
 *
 * <p>EN: {@link #isAvailable()} is the probe run once during the load phase; it may touch native
 * libraries or FFM linkers and is allowed to fail (a thrown exception is treated as "not
 * available" by {@link FallbackChain}). {@link #priority()} defaults to the tier ordinal so the
 * preferred tier wins automatically.
 *
 * <p>RU: {@link #isAvailable()} — зонд, выполняемый один раз на фазе загрузки; может обращаться к
 * нативным библиотекам или линковщикам FFM и вправе упасть (брошенное исключение трактуется
 * {@link FallbackChain} как «недоступно»). {@link #priority()} по умолчанию равен порядковому
 * номеру уровня, поэтому предпочтительный уровень побеждает автоматически.
 */
public interface CapabilityProvider {

    /** The tier this provider implements. */
    CapabilityTier tier();

    /**
     * Probe whether this provider can run in the current environment. Run once at load time.
     * Implementations should be side-effect-free beyond what the probe inherently requires.
     */
    boolean isAvailable();

    /** Lower wins. Defaults to the tier ordinal (FFM &lt; JNI &lt; PURE_JAVA &lt; DISABLED). */
    default int priority() {
        return tier().ordinal();
    }
}
