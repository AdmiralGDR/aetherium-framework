package org.aetherium.core;

import java.util.Objects;

/**
 * A structured, host-safe diagnostic record.
 *
 * <p>EN: Used throughout the framework instead of free-form strings so failures are machine-
 * routable and can be surfaced to mods without leaking host paths ({@code ARCHITECTURE.md} ,
 * confidentiality). The {@code code} is a stable, greppable identifier; {@code message} is the
 * human-readable, already-redacted text.
 *
 * <p>RU: Используется во всём фреймворке вместо произвольных строк, чтобы сбои были
 * машинно-маршрутизируемыми и могли показываться модам без утечки путей хоста
 * ({@code ARCHITECTURE.md} , конфиденциальность). {@code code} — стабильный идентификатор для
 * поиска; {@code message} — человекочитаемый, уже вычищенный текст.
 *
 * @param severity importance level
 * @param code     stable identifier (e.g. {@code "AE-TRANSFORM-001"})
 * @param message  redacted, human-readable detail
 */
public record Diagnostic(Severity severity, String code, String message) {

    public enum Severity { INFO, WARN, ERROR }

    public Diagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public static Diagnostic info(String code, String message) {
        return new Diagnostic(Severity.INFO, code, message);
    }

    public static Diagnostic warn(String code, String message) {
        return new Diagnostic(Severity.WARN, code, message);
    }

    public static Diagnostic error(String code, String message) {
        return new Diagnostic(Severity.ERROR, code, message);
    }
}
