package org.aetherium.bytecode;

import org.aetherium.core.Diagnostic;

/**
 * A thread-safe destination for {@link Diagnostic}s emitted during transformation.
 *
 * <p>EN: The engine runs transforms on many virtual threads, so implementations must be safe for
 * concurrent {@link #accept} calls. Kept as a tiny SPI so the loader can route diagnostics to its
 * own logging without {@code aetherium-bytecode} depending on any logging framework.
 *
 * <p>RU: Движок выполняет трансформации на многих виртуальных потоках, поэтому реализации обязаны
 * быть безопасны при конкурентных вызовах {@link #accept}. Оставлен крошечным SPI, чтобы загрузчик
 * мог направлять диагностику в собственное логирование без зависимости {@code aetherium-bytecode}
 * от какого-либо фреймворка логирования.
 */
@FunctionalInterface
public interface DiagnosticSink {

    void accept(Diagnostic diagnostic);

    /** A sink that discards everything. */
    static DiagnosticSink noop() {
        return diagnostic -> {
        };
    }
}
