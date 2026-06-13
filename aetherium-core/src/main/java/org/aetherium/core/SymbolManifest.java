package org.aetherium.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The Symbol Manifest: the authoritative, immutable map of abstract API symbols to dense integer
 * IDs.
 *
 * <p>EN: Built once at load time, then read-only. {@link #byId(int)} is the runtime-critical
 * {@code O(1)} path (a flat array index, no hashing); {@link #idOf(String)} is a load-phase
 * convenience used by the bytecode engine to look up the ID for a symbol it is rewriting. The
 * manifest is the single source of truth for IDs — transformers and dispatch tables read it
 * rather than hardcoding numbers ({@code ARCHITECTURE.md} ).
 *
 * <p>RU: Строится один раз во время загрузки, затем только для чтения. {@link #byId(int)} — это
 * критичный для времени выполнения путь {@code O(1)} (индекс плоского массива, без хеширования);
 * {@link #idOf(String)} — удобство фазы загрузки, используемое движком байт-кода для поиска ID
 * переписываемого символа. Манифест — единственный источник истины для ID.
 */
public sealed interface SymbolManifest permits ArraySymbolManifest {

    /** {@code O(1)} dense-array lookup. The runtime-critical path. */
    Symbol byId(int id);

    /** Load-phase reverse lookup by {@code "namespace:name"}. Empty if unknown. */
    OptionalInt idOf(String qualifiedName);

    /** Number of registered symbols; valid IDs are {@code [0, size())}. */
    int size();

    /** Immutable snapshot of all symbols, ordered by ID. */
    List<Symbol> symbols();

    /** Start building a manifest. IDs are assigned densely in insertion order. */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Incremental builder. EN: not thread-safe by design — manifests are assembled on a single
     * load-phase thread, then published immutably. RU: намеренно не потокобезопасен — манифесты
     * собираются в одном потоке фазы загрузки, затем публикуются неизменяемо.
     */
    final class Builder {
        private final List<Symbol> ordered = new ArrayList<>();
        private final Map<String, Integer> index = new HashMap<>();

        private Builder() {
        }

        /** Append a symbol; its ID is the current size. Rejects duplicate qualified names. */
        public Builder add(String namespace, String name, String descriptor) {
            int id = ordered.size();
            Symbol symbol = new Symbol(id, namespace, name, descriptor);
            Integer previous = index.putIfAbsent(symbol.qualifiedName(), id);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate symbol '" + symbol.qualifiedName() + "' (ids " + previous + " and " + id + ")");
            }
            ordered.add(symbol);
            return this;
        }

        public SymbolManifest build() {
            return new ArraySymbolManifest(ordered.toArray(Symbol[]::new), Map.copyOf(index));
        }
    }
}
