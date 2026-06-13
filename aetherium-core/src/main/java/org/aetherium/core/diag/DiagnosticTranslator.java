package org.aetherium.core.diag;

import org.aetherium.core.Diagnostic;

/**
 * Translates raw JVM / native {@link Throwable}s into bilingual, human-readable {@link Explanation}s.
 *
 * <p>EN: The framework executes untrusted bytecode and native code; failures arrive as cryptic
 * errors like {@link UnsatisfiedLinkError}, {@code ClassFormatError}, or {@code BootstrapMethodError}.
 * This translator maps the throwable's <em>type</em> to a stable code and a plain-language
 * explanation in English and Russian, including what the framework will do about it (usually:
 * degrade gracefully and continue). It is total — every throwable yields an explanation, never
 * another exception — so it can run inside catch blocks on the launch-critical path without risk.
 *
 * <p>RU: Фреймворк исполняет недоверенный байт-код и нативный код; сбои приходят как загадочные
 * ошибки вроде {@link UnsatisfiedLinkError}, {@code ClassFormatError} или {@code BootstrapMethodError}.
 * Транслятор сопоставляет <em>тип</em> throwable со стабильным кодом и понятным объяснением на
 * английском и русском, включая то, что фреймворк предпримет (обычно: мягко деградировать и
 * продолжить). Он тотален — каждый throwable даёт объяснение, никогда не новое исключение — поэтому
 * может выполняться в catch-блоках на критичном для запуска пути без риска.
 */
public final class DiagnosticTranslator {

    private DiagnosticTranslator() {
    }

    /** Translate a throwable into a bilingual explanation. Never throws. */
    public static Explanation translate(Throwable t) {
        if (t == null) {
            return new Explanation("AE-UNKNOWN-000", Diagnostic.Severity.INFO,
                    "No error was reported.",
                    "Ошибка не сообщалась.");
        }

        // Pattern-match on the error type. Ordered most-specific first.
        return switch (t) {
            case UnsatisfiedLinkError e -> new Explanation(
                    "AE-NATIVE-001", Diagnostic.Severity.WARN,
                    "The native acceleration library could not be loaded (a missing or incompatible "
                            + ".so, or an absent OS dependency). Aetherium will continue using the pure-Java "
                            + "path; performance features that need native code are disabled.",
                    "Не удалось загрузить нативную библиотеку ускорения (отсутствует или несовместим "
                            + ".so, либо нет зависимости ОС). Aetherium продолжит работу на чистой Java; "
                            + "функции, требующие нативного кода, отключены.");
            case UnsupportedClassVersionError e -> new Explanation(
                    "AE-JAVA-001", Diagnostic.Severity.ERROR,
                    "A class was compiled for a newer Java version than this runtime. Aetherium needs "
                            + "Java 21 (GraalVM). Please run with a compatible JDK.",
                    "Класс скомпилирован под более новую версию Java, чем эта среда выполнения. "
                            + "Aetherium требует Java 21 (GraalVM). Запустите с совместимым JDK.");
            // ClassCircularityError extends LinkageError but not ClassFormatError; handle the format
            // family explicitly before the generic VerifyError/LinkageError catch-alls.
            case ClassFormatError e -> new Explanation(
                    "AE-BYTECODE-002", Diagnostic.Severity.WARN,
                    "A class file is malformed or was transformed into invalid bytecode. Aetherium "
                            + "reverted that class to its original form and skipped the transformation; "
                            + "the game continues.",
                    "Файл класса повреждён или преобразован в некорректный байт-код. Aetherium вернул "
                            + "этот класс к исходному виду и пропустил трансформацию; игра продолжается.");
            case VerifyError e -> new Explanation(
                    "AE-BYTECODE-003", Diagnostic.Severity.WARN,
                    "Bytecode verification rejected a transformed class. Aetherium reverted it to the "
                            + "original bytes; the affected feature is disabled but the game continues.",
                    "Верификация байт-кода отклонила преобразованный класс. Aetherium вернул исходные "
                            + "байты; затронутая функция отключена, но игра продолжается.");
            case BootstrapMethodError e -> new Explanation(
                    "AE-DISPATCH-001", Diagnostic.Severity.ERROR,
                    "An Aetherium dispatch call site could not be linked (its dispatch table entry was "
                            + "missing). This points to an incomplete load phase; the affected call is unavailable.",
                    "Не удалось слинковать точку вызова диспетчеризации Aetherium (нет записи в таблице "
                            + "диспетчеризации). Это указывает на неполную фазу загрузки; вызов недоступен.");
            case NoClassDefFoundError e -> missingDependency(e.getMessage());
            case ClassNotFoundException e -> missingDependency(e.getMessage());
            case OutOfMemoryError e -> new Explanation(
                    "AE-MEM-001", Diagnostic.Severity.ERROR,
                    "The JVM ran out of memory. Aetherium released its off-heap buffers; consider raising "
                            + "the heap size (-Xmx) or reducing concurrent mods.",
                    "JVM исчерпала память. Aetherium освободил свои off-heap буферы; увеличьте размер "
                            + "кучи (-Xmx) или уменьшите число одновременных модов.");
            default -> new Explanation(
                    "AE-UNKNOWN-001", Diagnostic.Severity.WARN,
                    "An unexpected " + t.getClass().getSimpleName() + " occurred"
                            + (t.getMessage() == null ? "" : ": " + t.getMessage())
                            + ". Aetherium contained it and continued; see logs for detail.",
                    "Произошла неожиданная ошибка " + t.getClass().getSimpleName()
                            + (t.getMessage() == null ? "" : ": " + t.getMessage())
                            + ". Aetherium локализовал её и продолжил; подробности в логах.");
        };
    }

    /** Convenience: translate straight to a structured {@link Diagnostic}. */
    public static Diagnostic toDiagnostic(Throwable t) {
        return translate(t).toDiagnostic();
    }

    private static Explanation missingDependency(String detail) {
        String suffix = detail == null ? "" : " (" + detail + ")";
        return new Explanation(
                "AE-DEP-001", Diagnostic.Severity.ERROR,
                "A required class or dependency was not found" + suffix + ". A mod or library may be "
                        + "missing or incompatible; that component is disabled.",
                "Не найден требуемый класс или зависимость" + suffix + ". Мод или библиотека могут "
                        + "отсутствовать или быть несовместимы; компонент отключён.");
    }
}
