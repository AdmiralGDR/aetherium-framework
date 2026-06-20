/*
 * Aetherium Framework — JFR method-timing event (ephemeral probe payload).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.probe;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * The JFR event emitted by a woven probe — a method-execution timing sample.
 *
 * <p>EN: A standard {@link jdk.jfr.Event} (no incubator). When a probe is active, {@link ProbeWeaver}
 * weaves {@code begin()} at method entry and {@code commit()} before every return, so JFR records the
 * method's wall-clock duration with zero allocation churn beyond the event object itself. Because the
 * event class is referenced <em>only</em> from woven bytecode, a method with no active probe carries no
 * reference to it at all — that is the zero-static-overhead guarantee.
 *
 * <p>RU: Стандартное событие {@link jdk.jfr.Event} (без инкубатора). Когда зонд активен,
 * {@link ProbeWeaver} вплетает {@code begin()} на входе в метод и {@code commit()} перед каждым
 * возвратом, и JFR записывает длительность метода по «стенным часам». Поскольку класс события ссылается
 * <em>только</em> из вплетённого байт-кода, метод без активного зонда не содержит ссылки на него вовсе —
 * это и есть гарантия нулевых статических накладных расходов.
 */
@Name("org.aetherium.MethodTiming")
@Label("Aetherium Method Timing")
@Category({"Aetherium", "Ephemeral Probes"})
@Description("Wall-clock duration of a method instrumented by an ephemeral Aetherium probe.")
@StackTrace(false)
public final class AetheriumMethodEvent extends Event {

    @Label("Method")
    public String method;
}
