package org.aetherium.core;

import java.util.Objects;

/**
 * The framework's root unchecked exception, always carrying a structured {@link Diagnostic}.
 *
 * <p>EN: Unchecked because Aetherium failures are generally non-recoverable at the call site and
 * are handled by the load-phase fallback machinery, not by per-call {@code try/catch}. The
 * attached {@link Diagnostic} lets handlers route and report without string-parsing.
 *
 * <p>RU: Непроверяемое, поскольку сбои Aetherium обычно невосстановимы в точке вызова и
 * обрабатываются механизмом отката фазы загрузки, а не {@code try/catch} на каждый вызов.
 * Прикреплённый {@link Diagnostic} позволяет обработчикам маршрутизировать и сообщать без
 * разбора строк.
 */
public class AetheriumException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Diagnostic diagnostic;

    public AetheriumException(Diagnostic diagnostic) {
        this(diagnostic, null);
    }

    public AetheriumException(Diagnostic diagnostic, Throwable cause) {
        super(format(diagnostic), cause);
        this.diagnostic = diagnostic;
    }

    private static String format(Diagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        return "[" + diagnostic.severity() + "] " + diagnostic.code() + ": " + diagnostic.message();
    }

    /** The structured diagnostic that triggered this exception. */
    public Diagnostic diagnostic() {
        return diagnostic;
    }
}
