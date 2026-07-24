/*
 * Aetherium Framework — shield batch renamer (class + private-member obfuscation).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renames classes and their private members to opaque names, consistently across the whole class set.
 *
 * <p>EN: This is the deepest anti-analysis pass and the most careful. Class names are the map an analyst (or
 * an AI) reconstructs first; erasing them — and the package structure — removes the primary semantic anchor.
 * But renaming is dangerous: anything resolved <em>by name</em> at runtime must be preserved, so the
 * {@link KeepList} pins {@code ServiceLoader} implementations, the generated entrypoint, and content classes.
 * Only <em>private</em> methods and fields are renamed (they have no external references and cannot be
 * overridden), and records are left intact (their component/accessor/constructor names are load-bearing).
 * Every reference — including method handles behind lambdas — is rewritten together, so the result still
 * loads and runs.
 * RU: Самый глубокий проход против анализа и самый аккуратный. Имена классов — первая карта, которую
 * восстанавливает аналитик (или ИИ); их стирание (и структуры пакетов) убирает главную семантическую опору.
 * Но переименование опасно: всё, что разрешается по имени в рантайме, сохраняется через {@link KeepList}.
 * Переименовываются только приватные методы/поля; записи (records) не трогаются. Все ссылки переписываются
 * согласованно.
 */
final class Renamer {

    private Renamer() {
    }

    /** Result of a batch rename: classes keyed by their NEW binary name, plus the old→new class mapping. */
    record Result(Map<String, byte[]> classes, Map<String, String> classRenames) {
    }

    static Result rename(Map<String, byte[]> input, KeepList keep, boolean renamePrivateMembers) {
        OpaqueNames names = new OpaqueNames("o/");
        Map<String, String> classMap = new LinkedHashMap<>();   // oldInternal -> newInternal
        Map<String, String> methodMap = new LinkedHashMap<>();  // owner.name.desc -> newName
        Map<String, String> fieldMap = new LinkedHashMap<>();   // owner.name -> newName

        // Pass 1: read metadata and build the rename maps.
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : input.entrySet()) {
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(e.getValue()).accept(node, ClassReader.SKIP_CODE);
            nodes.put(node.name, node);
        }
        for (ClassNode node : nodes.values()) {
            if (keep.isKept(node.name)) {
                continue; // keep the class name; still remapped for its references below
            }
            classMap.put(node.name, names.nextClass());
            if (!renamePrivateMembers || isRecord(node)) {
                continue;
            }
            for (MethodNode m : node.methods) {
                if (isRenameableMethod(m)) {
                    methodMap.put(node.name + '.' + m.name + '.' + m.desc, names.nextMember());
                }
            }
            for (FieldNode f : node.fields) {
                if ((f.access & Opcodes.ACC_PRIVATE) != 0) {
                    fieldMap.put(node.name + '.' + f.name, names.nextMember());
                }
            }
        }

        Remapper remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                return classMap.getOrDefault(internalName, internalName);
            }

            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                return methodMap.getOrDefault(owner + '.' + name + '.' + descriptor, name);
            }

            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                return fieldMap.getOrDefault(owner + '.' + name, name);
            }
        };

        // Pass 2: rewrite every class (kept classes included — their references need remapping too).
        Map<String, byte[]> out = new LinkedHashMap<>();
        Map<String, String> classRenames = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : input.entrySet()) {
            ClassReader reader = new ClassReader(e.getValue());
            ClassWriter writer = new ClassWriter(0);
            // Pass the watermark prototype so the author attribute is preserved through the remap (an
            // unknown attribute would otherwise be silently dropped when re-read here).
            reader.accept(new ClassRemapper(writer, remapper),
                    new org.objectweb.asm.Attribute[]{new WatermarkAttribute()}, 0);
            String oldInternal = reader.getClassName();
            String newInternal = classMap.getOrDefault(oldInternal, oldInternal);
            out.put(newInternal.replace('/', '.'), writer.toByteArray());
            classRenames.put(oldInternal.replace('/', '.'), newInternal.replace('/', '.'));
        }
        return new Result(out, classRenames);
    }

    private static boolean isRecord(ClassNode node) {
        return "java/lang/Record".equals(node.superName);
    }

    private static boolean isRenameableMethod(MethodNode m) {
        if ((m.access & Opcodes.ACC_PRIVATE) == 0) {
            return false;
        }
        return !"<init>".equals(m.name) && !"<clinit>".equals(m.name);
    }
}
