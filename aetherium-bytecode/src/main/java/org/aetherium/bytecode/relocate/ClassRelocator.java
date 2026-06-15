/*
 * Aetherium Framework — ASM-based namespace relocator.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.bytecode.relocate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.List;
import java.util.Objects;

/**
 * Rewrites all type references in a class according to a list of {@link Relocation}s (ASM-based).
 *
 * <p>EN: Uses ASM's {@link ClassRemapper} with a prefix {@link Remapper}, so every internal name,
 * descriptor, signature, and constant is rewritten consistently — the correct way to relocate
 * (shade) a library, far safer than text replacement. Public API takes/returns {@code byte[]} and
 * {@link Relocation} only, so callers (e.g. the loader's {@code DependencyFlattener}) never touch
 * ASM. {@code aetherium-bytecode} keeps owning all ASM; nothing leaks.
 *
 * <p>RU: Использует {@link ClassRemapper} из ASM с префиксным {@link Remapper}, поэтому каждое
 * внутреннее имя, дескриптор, сигнатура и константа переписываются согласованно — корректный способ
 * relocate (shade) библиотеки, намного безопаснее текстовой замены. Публичный API принимает/возвращает
 * только {@code byte[]} и {@link Relocation}, поэтому вызывающие (напр. {@code DependencyFlattener}
 * загрузчика) не касаются ASM.
 */
public final class ClassRelocator {

    private final List<Relocation> relocations;

    public ClassRelocator(List<Relocation> relocations) {
        this.relocations = List.copyOf(Objects.requireNonNull(relocations, "relocations"));
    }

    /** Relocate all type references in the given class bytes. */
    public byte[] relocate(byte[] original) {
        Objects.requireNonNull(original, "original");
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(0);
        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                for (Relocation relocation : relocations) {
                    if (relocation.matches(internalName)) {
                        return relocation.apply(internalName);
                    }
                }
                return internalName;
            }
        };
        reader.accept(new ClassRemapper(writer, remapper), 0);
        return writer.toByteArray();
    }
}
