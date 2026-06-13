package org.aetherium.bytecode;

import org.aetherium.core.Diagnostic;
import org.objectweb.asm.tree.ClassNode;

import java.util.Objects;

/**
 * The outcome of applying a single {@link ClassTransformer}.
 *
 * <p>EN: A sealed, closed set of outcomes so the engine driver can match exhaustively with no
 * defensive {@code default} branch ({@code docs/en/bytecode-engine.md} ). {@link Applied} carries
 * the (in-place mutated) node; {@link Skipped} means the transformer had nothing to do;
 * {@link Failed} carries a structured {@link Diagnostic} and triggers the engine's revert-to-original
 * fallback.
 *
 * <p>RU: Запечатанный закрытый набор исходов, чтобы драйвер движка мог исчерпывающе сопоставлять
 * без защитной ветки {@code default}. {@link Applied} несёт (изменённый на месте) узел;
 * {@link Skipped} означает, что трансформеру нечего было делать; {@link Failed} несёт
 * структурированный {@link Diagnostic} и запускает откат движка к оригиналу.
 */
public sealed interface TransformResult {

    /** The transformer changed the class. The node is mutated in place; carried for clarity. */
    record Applied(ClassNode node) implements TransformResult {
        public Applied {
            Objects.requireNonNull(node, "node");
        }
    }

    /** The transformer chose not to change the class; {@code reason} aids diagnostics. */
    record Skipped(String reason) implements TransformResult {
        public Skipped {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** The transformer failed; the engine will log {@code diagnostic} and revert to the original. */
    record Failed(Diagnostic diagnostic) implements TransformResult {
        public Failed {
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }
}
