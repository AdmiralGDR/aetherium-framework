package org.aetherium.core.diag;

import org.aetherium.core.Diagnostic;

import java.util.Objects;

/**
 * A bilingual, human-readable explanation of a low-level failure.
 *
 * <p>EN: Produced by {@link DiagnosticTranslator} from a raw {@link Throwable}. Carries a stable
 * {@code code}, a {@link Diagnostic.Severity}, and plain-language English and Russian text — so the
 * framework can surface "what happened and what we did about it" to players and modders without
 * dumping a stack trace. Convert to the structured {@link Diagnostic} pipeline via {@link #toDiagnostic()}.
 *
 * <p>RU: Создаётся {@link DiagnosticTranslator} из сырого {@link Throwable}. Несёт стабильный
 * {@code code}, {@link Diagnostic.Severity} и понятный текст на английском и русском — чтобы
 * фреймворк показывал игрокам и моддерам «что случилось и что мы сделали» без вываливания стека.
 * Преобразуйте в структурированный {@link Diagnostic} через {@link #toDiagnostic()}.
 *
 * @param code     stable diagnostic identifier
 * @param severity importance level
 * @param english  plain-English explanation (already redacted)
 * @param russian  plain-Russian explanation (already redacted)
 */
public record Explanation(String code, Diagnostic.Severity severity, String english, String russian) {

    public Explanation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(english, "english");
        Objects.requireNonNull(russian, "russian");
    }

    /** Structured diagnostic carrying both languages in the message. */
    public Diagnostic toDiagnostic() {
        return new Diagnostic(severity, code, "EN: " + english + " | RU: " + russian);
    }

    /** Two-line bilingual rendering for console/log output. */
    public String render() {
        return "EN: " + english + System.lineSeparator() + "RU: " + russian;
    }
}
