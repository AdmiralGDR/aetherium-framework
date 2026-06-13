package org.aetherium.core;

import java.util.Objects;

/**
 * An abstract API symbol with a <strong>dense, build-assigned integer ID</strong>.
 *
 * <p>EN: The {@code id} is the index into the runtime dispatch table (see {@code ARCHITECTURE.md}
 * ); it is assigned by the {@link SymbolManifest.Builder} in insertion order and is never
 * hardcoded in transformers. {@code namespace}/{@code name} identify the symbol for load-phase
 * lookups; {@code descriptor} is its JVM method/field descriptor.
 *
 * <p>RU: {@code id} — индекс в таблице диспетчеризации времени выполнения (см.
 * {@code ARCHITECTURE.md} ); назначается {@link SymbolManifest.Builder} в порядке добавления и
 * никогда не зашивается в трансформерах. {@code namespace}/{@code name} идентифицируют символ при
 * поиске на фазе загрузки; {@code descriptor} — его JVM-дескриптор метода/поля.
 *
 * @param id         dense, non-negative dispatch-table index
 * @param namespace  logical grouping (e.g. {@code "registry"}, {@code "event"})
 * @param name       symbol name within the namespace
 * @param descriptor JVM type descriptor of the symbol
 */
public record Symbol(int id, String namespace, String name, String descriptor) {

    public Symbol {
        if (id < 0) {
            throw new IllegalArgumentException("Symbol id must be dense and non-negative: " + id);
        }
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
    }

    /** Stable, human-readable key {@code "namespace:name"} used for load-phase lookups. */
    public String qualifiedName() {
        return namespace + ":" + name;
    }
}
