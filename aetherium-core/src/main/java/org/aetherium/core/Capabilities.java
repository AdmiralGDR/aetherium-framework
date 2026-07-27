/*
 * Aetherium Framework — the one-line FFM/preview capability helper ().
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core;

import java.util.function.Supplier;

/**
 * Choose the fast off-heap/FFM path when it is available, else a safe fallback — correctly, in one line.
 *
 * <p>EN: A preview-compiled class (anything touching {@code java.lang.foreign} / SIMD on Java 21) throws
 * {@link UnsupportedClassVersionError} on a launcher without {@code --enable-preview}. That is an
 * {@link Error}, <em>not</em> an {@link Exception} — so the obvious {@code catch (RuntimeException)} misses it —
 * and it surfaces at the first <em>use</em>, deep inside init, not at class-load time where one looks for it.
 * Every FFM consumer hits this trap exactly once, painfully (a downstream mod did, and had to hand-roll a probe).
 * {@link #ffm} makes it impossible to get wrong: it runs {@code preview} and, on <strong>any
 * {@link Throwable}</strong>, returns {@code fallback}. {@link #ffmLazy} is the "probe once, cache the verdict,
 * degrade every call site" form. Complements {@link FallbackChain} (for multi-tier {@link CapabilityProvider}
 * chains) with the common two-way FFM-or-pure case, in the {@link CapabilityTier} vocabulary (FFM → PURE_JAVA).
 * Pure and zero-dependency.
 *
 * <p>RU: Preview-класс (всё, что трогает {@code java.lang.foreign}/SIMD на Java 21) бросает
 * {@link UnsupportedClassVersionError} на лаунчере без {@code --enable-preview}. Это {@link Error}, а
 * <em>не</em> {@link Exception} — очевидный {@code catch (RuntimeException)} его не ловит — и всплывает он при
 * первом <em>использовании</em>, глубоко внутри инициализации. Каждый потребитель FFM попадает в эту ловушку.
 * {@link #ffm} делает ошибку невозможной: выполняет {@code preview} и при <strong>любом {@link Throwable}</strong>
 * возвращает {@code fallback}. {@link #ffmLazy} — форма «проба один раз, кэш вердикта». Чистый, без зависимостей.
 */
public final class Capabilities {

    private Capabilities() {
    }

    /**
     * Run {@code preview}; if it throws <strong>anything</strong> — including {@link Error} such as
     * {@link UnsupportedClassVersionError} from a preview class on a stock launcher — return {@code fallback}
     * instead. Use it to pick an implementation once at init:
     * {@code var engine = Capabilities.ffm(OffHeapEngine::new, PureJavaEngine::new);}
     */
    public static <T> T ffm(Supplier<T> preview, Supplier<T> fallback) {
        try {
            return preview.get();
        } catch (Throwable ffmUnavailable) {
            return fallback.get();
        }
    }

    /**
     * A memoized supplier that probes {@code preview} on its first {@code get()} and, thereafter, always uses
     * whichever path worked — so a degraded launch never re-attempts (and re-fails) the preview class load.
     * The first call returns the real {@code preview} value when it works (no wasted probe).
     */
    public static <T> Supplier<T> ffmLazy(Supplier<T> preview, Supplier<T> fallback) {
        return new Supplier<>() {
            private volatile Boolean previewWorks;

            @Override
            public T get() {
                Boolean works = previewWorks;
                if (works == null) {
                    try {
                        T value = preview.get();
                        previewWorks = Boolean.TRUE;
                        return value;
                    } catch (Throwable ffmUnavailable) {
                        previewWorks = Boolean.FALSE;
                        return fallback.get();
                    }
                }
                return works ? preview.get() : fallback.get();
            }
        };
    }

    /**
     * Probe once whether an FFM/preview code path loads and runs, catching any {@link Throwable}. Use it for a
     * feature flag when there is no value to produce: {@code if (Capabilities.available(SimdMath::warmUp)) …}.
     */
    public static boolean available(Runnable probe) {
        try {
            probe.run();
            return true;
        } catch (Throwable unavailable) {
            return false;
        }
    }
}
