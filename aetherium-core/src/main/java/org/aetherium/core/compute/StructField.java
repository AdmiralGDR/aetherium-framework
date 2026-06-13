/*
 * Aetherium Framework — data-oriented struct field.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.compute;

import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * One named field within a {@link StructLayout}, resolved to a byte offset.
 *
 * <p>EN: A field is a {@code (name, ValueLayout, offset)} triple. The {@code offset} is computed
 * once by the {@link StructLayout} builder (respecting natural alignment); at runtime,
 * {@link StructArena} reads/writes {@code element[index].field} at {@code index * stride + offset}
 * with a single bounds-checked FFM access — no hashing, no reflection on the hot path.
 *
 * <p>RU: Поле — это тройка {@code (имя, ValueLayout, смещение)}. {@code offset} вычисляется один раз
 * builder-ом {@link StructLayout} (с учётом естественного выравнивания); во время выполнения
 * {@link StructArena} читает/пишет {@code element[index].field} по адресу
 * {@code index * stride + offset} одним FFM-доступом с проверкой границ — без хеширования и
 * рефлексии на «горячем пути».
 *
 * @param name   field name
 * @param layout the FFM value layout (carrier + size + alignment)
 * @param offset byte offset within a single struct element
 */
public record StructField(String name, ValueLayout layout, long offset) {

    public StructField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(layout, "layout");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0: " + offset);
        }
    }

    /** Size of this field in bytes. */
    public long byteSize() {
        return layout.byteSize();
    }
}
