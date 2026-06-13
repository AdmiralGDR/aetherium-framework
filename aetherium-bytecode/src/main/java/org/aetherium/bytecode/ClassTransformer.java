package org.aetherium.bytecode;

/**
 * A pure, isolated class transformer — the engine's unit of work and its extension point (SPI).
 *
 * <p>EN: <b>Open by design.</b> The earlier draft sketched a {@code sealed} interface, but the
 * loader (and, later, mods) must be able to contribute their own transformers from other modules
 * without {@code aetherium-bytecode} knowing about them — so this is an open SPI. It stays modular:
 * implementations may depend only on {@code core} + ASM, never on loader internals
 * ({@code docs/en/bytecode-engine.md} ).
 *
 * <p>Contract: an implementation must behave as a pure function of its {@link ClassContext} —
 * no shared mutable state, safe to run on its own virtual thread, and re-runnable. It mutates the
 * supplied {@link org.objectweb.asm.tree.ClassNode} in place (the ASM tree idiom) and reports the
 * outcome via {@link TransformResult}.
 *
 * <p>RU: <b>Открыт намеренно.</b> Ранний черновик намечал {@code sealed}-интерфейс, но загрузчик
 * (а позднее и моды) должны иметь возможность поставлять свои трансформеры из других модулей, о
 * которых {@code aetherium-bytecode} не знает — поэтому это открытый SPI. Он остаётся модульным:
 * реализации могут зависеть только от {@code core} + ASM, но не от внутренностей загрузчика.
 *
 * <p>Контракт: реализация обязана вести себя как чистая функция от своего {@link ClassContext} —
 * без разделяемого изменяемого состояния, безопасная для запуска в собственном виртуальном потоке
 * и повторяемая. Она изменяет переданный {@link org.objectweb.asm.tree.ClassNode} на месте (идиома
 * дерева ASM) и сообщает исход через {@link TransformResult}.
 */
public interface ClassTransformer {

    /**
     * Ordering priority — lower runs first. EN: assigned by the build/registration, never a magic
     * literal inside transformation logic. RU: назначается сборкой/регистрацией, а не магическим
     * литералом внутри логики трансформации.
     */
    int order();

    /** Cheap pre-filter: does this transformer have any work to do for the class? */
    boolean handles(ClassContext context);

    /** Apply the transformation. Must not throw for ordinary control flow — return {@link TransformResult.Failed}. */
    TransformResult apply(ClassContext context);

    /** Stable id for diagnostics; defaults to the simple class name. */
    default String id() {
        return getClass().getSimpleName();
    }
}
