/*
 * Aetherium Framework — shield ↔ name-based index rewrite test (feedback ).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the fix: with {@code --rename}, {@code content.index} (a name-based text registry the
 * framework's own content processor wrote) is rewritten through the rename map, so the class it names still
 * loads — no green-build-broken-jar.
 */
final class ShieldIndexTest {

    private static final String BINARY = "com.example.block.AnomalyCoreBlock";
    private static final String INTERNAL = "com/example/block/AnomalyCoreBlock";

    @Test
    void renameRewritesContentIndexSoTheClassStillResolves() throws Exception {
        Path dir = Files.createTempDirectory("aetherium-shield-index");
        try {
            // A mod block class (pure — no MC — so obfuscation won't revert it).
            Path classFile = dir.resolve(INTERNAL + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, sampleClass());

            // The content processor's index naming that class (field 3 = the FQ class name).
            Path index = dir.resolve("META-INF/aetherium/content.index");
            Files.createDirectories(index.getParent());
            Files.writeString(index,
                    "# generated\nBLOCK|mymod|anomaly_core|" + BINARY + "|4.0|1200.0|true|true|64|Anomaly Core\n",
                    StandardCharsets.UTF_8);

            ShieldDirectory.protect(dir, "RedstoneTeam", true);

            // The index line's class field is now the opaque renamed name...
            List<String> lines = Files.readAllLines(index, StandardCharsets.UTF_8);
            String data = lines.stream().filter(l -> l.startsWith("BLOCK|")).findFirst().orElseThrow();
            String newName = data.split("\\|", -1)[3];
            assertNotEquals(BINARY, newName, "the index must have been remapped away from the original name");

            // ...and that renamed class actually exists and loads from the output directory.
            try (URLClassLoader cl = new URLClassLoader(new URL[]{dir.toUri().toURL()}, null)) {
                assertDoesNotThrow(() -> Class.forName(newName, false, cl),
                        "the class named in the rewritten index must resolve");
                assertThrows(ClassNotFoundException.class, () -> Class.forName(BINARY, false, cl),
                        "the original class name must be gone (it was renamed)");
            }
        } finally {
            deleteTree(dir);
        }
    }

    private static byte[] sampleClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, INTERNAL, null, "java/lang/Object", null);
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        MethodVisitor hardness = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "hardness", "()F", null, null);
        hardness.visitCode();
        hardness.visitLdcInsn(4.0f);
        hardness.visitInsn(Opcodes.FRETURN);
        hardness.visitMaxs(1, 0);
        hardness.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void deleteTree(Path dir) throws Exception {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort
                }
            });
        }
    }
}
