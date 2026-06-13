/*
 * Aetherium Framework — data-oriented struct layout.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.compute;

import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The schema of a data-oriented entity struct (Array-of-Structs, contiguous, off-heap).
 *
 * <p>EN: Built once with a fluent builder; computes each field's offset (natural alignment) and the
 * element {@code stride}. Backing an {@link StructArena} with this layout stores N entities
 * <strong>contiguously</strong> in off-heap memory, so iterating entities walks memory linearly —
 * maximizing CPU L1/L2 cache hits for massive per-tick updates (the anti-cache-miss design). No
 * per-entity Java objects, so no GC pressure and no pointer-chasing.
 *
 * <p>RU: Строится один раз fluent-builder-ом; вычисляет смещение каждого поля (естественное
 * выравнивание) и шаг элемента {@code stride}. {@link StructArena} с этим макетом хранит N
 * сущностей <strong>непрерывно</strong> в off-heap памяти, поэтому обход сущностей идёт линейно по
 * памяти — максимизируя попадания в кэш L1/L2 CPU при массовых обновлениях за тик (дизайн против
 * cache-miss). Нет Java-объектов на сущность, поэтому нет давления на GC и блужданий по указателям.
 */
public final class StructLayout {

    private final List<StructField> fields;
    private final Map<String, StructField> byName;
    private final long stride;
    private final long maxAlignment;

    private StructLayout(List<StructField> fields, long stride, long maxAlignment) {
        this.fields = List.copyOf(fields);
        this.stride = stride;
        this.maxAlignment = maxAlignment;
        Map<String, StructField> map = new LinkedHashMap<>();
        for (StructField f : fields) {
            map.put(f.name(), f);
        }
        this.byName = Map.copyOf(map);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Look up a field by name (O(1)). Throws if unknown. */
    public StructField field(String name) {
        StructField f = byName.get(name);
        if (f == null) {
            throw new IllegalArgumentException("Unknown struct field: " + name + " (have " + byName.keySet() + ")");
        }
        return f;
    }

    /** Bytes per element (aligned). */
    public long stride() {
        return stride;
    }

    /** Strongest field alignment — used to align the whole arena. */
    public long maxAlignment() {
        return maxAlignment;
    }

    public List<StructField> fields() {
        return fields;
    }

    private static long align(long offset, long alignment) {
        return (offset + (alignment - 1)) & ~(alignment - 1);
    }

    /** Fluent schema builder. Fields are laid out in declaration order with natural alignment. */
    public static final class Builder {
        private final List<StructField> fields = new ArrayList<>();
        private long offset = 0;
        private long maxAlignment = 1;

        private Builder() {
        }

        /** Add a field of the given FFM value layout (e.g. {@link ValueLayout#JAVA_DOUBLE}). */
        public Builder field(String name, ValueLayout layout) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(layout, "layout");
            long alignment = layout.byteAlignment();
            offset = align(offset, alignment);
            fields.add(new StructField(name, layout, offset));
            offset += layout.byteSize();
            maxAlignment = Math.max(maxAlignment, alignment);
            return this;
        }

        // Convenience typed shortcuts (zero-boilerplate DX).
        public Builder ints(String name) { return field(name, ValueLayout.JAVA_INT); }
        public Builder longs(String name) { return field(name, ValueLayout.JAVA_LONG); }
        public Builder floats(String name) { return field(name, ValueLayout.JAVA_FLOAT); }
        public Builder doubles(String name) { return field(name, ValueLayout.JAVA_DOUBLE); }

        public StructLayout build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("a struct must have at least one field");
            }
            long stride = align(offset, maxAlignment);
            return new StructLayout(fields, stride, maxAlignment);
        }
    }
}
