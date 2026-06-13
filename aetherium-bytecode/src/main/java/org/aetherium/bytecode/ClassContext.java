package org.aetherium.bytecode;

import org.aetherium.core.SymbolManifest;
import org.objectweb.asm.tree.ClassNode;

import java.util.Objects;

/**
 * Everything a {@link ClassTransformer} is allowed to see about the class under transformation.
 *
 * <p>EN: Deliberately narrow — the parsed {@link ClassNode}, its internal name, and the
 * {@link SymbolManifest} for ID lookups. No host paths, no file handles, no loader internals; this
 * is the confidentiality boundary of {@code ARCHITECTURE.md} A transformer cannot reach outside
 * the class graph it was handed.
 *
 * <p>RU: Намеренно узкий — разобранный {@link ClassNode}, его внутреннее имя и
 * {@link SymbolManifest} для поиска ID. Никаких путей хоста, файловых дескрипторов или внутренностей
 * загрузчика; это граница конфиденциальности из {@code ARCHITECTURE.md} Трансформер не может
 * выйти за пределы переданного ему графа классов.
 *
 * @param node         the parsed, mutable class
 * @param internalName the class's JVM internal name (e.g. {@code com/example/Foo})
 * @param manifest     the symbol manifest backing ID lookups
 */
public record ClassContext(ClassNode node, String internalName, SymbolManifest manifest) {

    public ClassContext {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(manifest, "manifest");
    }
}
