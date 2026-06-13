package org.aetherium.bytecode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An immutable, priority-ordered sequence of {@link ClassTransformer}s.
 *
 * <p>EN: Built once and shared across all per-class transform tasks. Because each transformer is a
 * pure function and the chain is immutable, the same chain can be applied concurrently to many
 * classes on virtual threads with no synchronization.
 *
 * <p>RU: Строится один раз и разделяется между всеми задачами трансформации классов. Поскольку
 * каждый трансформер — чистая функция, а цепочка неизменяема, одну цепочку можно применять
 * параллельно к множеству классов на виртуальных потоках без синхронизации.
 */
public final class TransformChain {

    private final List<ClassTransformer> transformers;

    private TransformChain(List<ClassTransformer> transformers) {
        this.transformers = transformers;
    }

    /** Build a chain; transformers are sorted by {@link ClassTransformer#order()} ascending. */
    public static TransformChain of(ClassTransformer... transformers) {
        List<ClassTransformer> sorted = new ArrayList<>(List.of(transformers));
        sorted.sort(Comparator.comparingInt(ClassTransformer::order));
        return new TransformChain(List.copyOf(sorted));
    }

    /** Immutable, ordered view. */
    public List<ClassTransformer> transformers() {
        return transformers;
    }

    public boolean isEmpty() {
        return transformers.isEmpty();
    }
}
