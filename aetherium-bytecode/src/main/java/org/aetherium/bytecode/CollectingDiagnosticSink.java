package org.aetherium.bytecode;

import org.aetherium.core.Diagnostic;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A thread-safe {@link DiagnosticSink} that accumulates diagnostics for later inspection.
 *
 * <p>EN: Backed by a {@link ConcurrentLinkedQueue} so it is safe to share across the engine's
 * virtual-thread workers. Useful for tests, the self-test harness, and batch load reports.
 * RU: На базе {@link ConcurrentLinkedQueue}, поэтому безопасен для разделения между
 * виртуально-поточными воркерами движка. Полезен для тестов, харнесса самопроверки и пакетных
 * отчётов загрузки.
 */
public final class CollectingDiagnosticSink implements DiagnosticSink {

    private final Queue<Diagnostic> diagnostics = new ConcurrentLinkedQueue<>();

    @Override
    public void accept(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    /** Immutable snapshot of everything collected so far. */
    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public boolean isEmpty() {
        return diagnostics.isEmpty();
    }

    public int count() {
        return diagnostics.size();
    }
}
