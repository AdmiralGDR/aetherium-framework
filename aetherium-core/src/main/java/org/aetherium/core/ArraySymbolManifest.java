package org.aetherium.core;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Array-backed {@link SymbolManifest}. Package-private; constructed only via the builder.
 *
 * <p>EN: {@code byId} is a bare array index — the {@code O(1)} runtime guarantee with zero hashing
 * and zero allocation. The reverse index is consulted only during the load phase.
 * RU: {@code byId} — голый индекс массива: гарантия {@code O(1)} во время выполнения без
 * хеширования и без аллокаций. Обратный индекс используется только на фазе загрузки.
 */
final class ArraySymbolManifest implements SymbolManifest {

    private final Symbol[] byId;
    private final Map<String, Integer> index;

    ArraySymbolManifest(Symbol[] byId, Map<String, Integer> index) {
        this.byId = byId;
        this.index = index;
    }

    @Override
    public Symbol byId(int id) {
        // Bounds are guaranteed by the dispatch table; an out-of-range id is a hard programming
        // error and we let it surface as ArrayIndexOutOfBoundsException rather than mask it.
        return byId[id];
    }

    @Override
    public OptionalInt idOf(String qualifiedName) {
        Integer id = index.get(qualifiedName);
        return id == null ? OptionalInt.empty() : OptionalInt.of(id);
    }

    @Override
    public int size() {
        return byId.length;
    }

    @Override
    public List<Symbol> symbols() {
        return List.of(byId);
    }
}
