package org.aetherium.core;

/**
 * The fallback ladder, highest-preference first.
 *
 * <p>EN: Declaration order <em>is</em> the preference order — {@link FallbackChain} sorts by
 * {@link #ordinal()}. {@code FFM} (java.lang.foreign) is preferred; {@code JNI} is the portable
 * native fallback; {@code PURE_JAVA} trades speed for guaranteed correctness; {@code DISABLED} is
 * the graceful no-op so a missing capability never crashes the launch ({@code ARCHITECTURE.md} ).
 *
 * <p>RU: Порядок объявления <em>и есть</em> порядок предпочтения — {@link FallbackChain} сортирует
 * по {@link #ordinal()}. {@code FFM} предпочтителен; {@code JNI} — переносимый нативный откат;
 * {@code PURE_JAVA} жертвует скоростью ради гарантированной корректности; {@code DISABLED} —
 * корректный no-op, чтобы отсутствующая возможность никогда не роняла запуск.
 */
public enum CapabilityTier {
    /** java.lang.foreign downcalls — preferred on GraalVM 21. */
    FFM,
    /** Classic native methods backed by a {@code .so}. */
    JNI,
    /** Correctness-first pure-Java implementation. */
    PURE_JAVA,
    /** Capability unavailable; calls degrade gracefully. */
    DISABLED
}
