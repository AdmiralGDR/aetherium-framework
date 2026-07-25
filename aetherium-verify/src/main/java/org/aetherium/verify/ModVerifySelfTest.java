/*
 * Aetherium Framework — in-game verification self-test (fully offline).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.verify;

import org.aetherium.shield.IntegrityManifest;
import org.aetherium.ui.LaidOut;
import org.aetherium.ui.RecordingUiRenderer;
import org.aetherium.ui.Rect;
import org.aetherium.ui.UiMetrics;
import org.aetherium.ui.UiRuntime;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Proves the whole in-game verification stack with no game present: it synthesizes a mod (a class
 * implementing {@code AetheriumMod}), registers it as a {@code ServiceLoader} service in a temp classpath,
 * writes a Shield integrity manifest, and then drives {@link ModInspector} — asserting SIGNED_INTACT when the
 * bytes match, TAMPERED when a byte is flipped, and UNSIGNED when there is no manifest. Finally it lays out
 * and hit-tests the {@link AetheriumModInspectorScreen} offline (through {@link RecordingUiRenderer}), so the
 * inspector is proven renderable before it ever reaches a player.
 */
public final class ModVerifySelfTest {

    private static final String MOD_BINARY = "demo.DemoMod";
    private static final String MOD_INTERNAL = "demo/DemoMod";

    private ModVerifySelfTest() {
    }

    /** Structured outcome. */
    public record Result(boolean intactVerdict,
                         boolean tamperedVerdict,
                         boolean unsignedVerdict,
                         boolean screenRenders,
                         String author,
                         List<String> notes) {
        public boolean passed() {
            return intactVerdict && tamperedVerdict && unsignedVerdict && screenRenders;
        }
    }

    public static Result run() throws Exception {
        List<String> notes = new ArrayList<>();
        Path dir = Files.createTempDirectory("aetherium-verify-selftest");
        try {
            byte[] modBytes = generateMod();
            Path classFile = dir.resolve(MOD_INTERNAL + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, modBytes);

            // Register the mod as a ServiceLoader service.
            Path svc = dir.resolve("META-INF/services/org.aetherium.core.mod.AetheriumMod");
            Files.createDirectories(svc.getParent());
            Files.writeString(svc, MOD_BINARY + "\n", StandardCharsets.UTF_8);

            Path manifest = dir.resolve("META-INF/aetherium/shield-integrity.txt");
            Files.createDirectories(manifest.getParent());

            // (1) Correct digest → SIGNED_INTACT.
            Files.writeString(manifest, MOD_BINARY + "=" + IntegrityManifest.sha256(modBytes) + "\n",
                    StandardCharsets.UTF_8);
            List<ModReport> intact = snapshot(dir);
            boolean intactVerdict = intact.size() == 1
                    && intact.get(0).verdict() == ModReport.Verdict.SIGNED_INTACT
                    && intact.get(0).modId().equals("demo");
            String author = intact.isEmpty() ? "" : intact.get(0).author();
            notes.add("intact: " + verdictLine(intact));

            // (2) Wrong digest → TAMPERED.
            Files.writeString(manifest, MOD_BINARY + "=" + "0".repeat(64) + "\n", StandardCharsets.UTF_8);
            List<ModReport> tampered = snapshot(dir);
            boolean tamperedVerdict = tampered.size() == 1
                    && tampered.get(0).verdict() == ModReport.Verdict.TAMPERED
                    && tampered.get(0).tamperedClasses().contains(MOD_BINARY);
            notes.add("tampered: " + verdictLine(tampered));

            // (3) No manifest → UNSIGNED.
            Files.delete(manifest);
            List<ModReport> unsigned = snapshot(dir);
            boolean unsignedVerdict = unsigned.size() == 1
                    && unsigned.get(0).verdict() == ModReport.Verdict.UNSIGNED;
            notes.add("unsigned: " + verdictLine(unsigned));

            // (4) The inspector screen lays out + paints offline through the pure UI runtime.
            AetheriumModInspectorScreen screen = new AetheriumModInspectorScreen(intact);
            RecordingUiRenderer renderer = new RecordingUiRenderer();
            LaidOut tree = UiRuntime.render(screen.build(), new Rect(0, 0, 420, 240), UiMetrics.DEFAULT, renderer);
            boolean screenRenders = renderer.textCount() >= 1 && UiRuntime.audit(tree).isEmpty();
            notes.add("inspector screen: text draws=" + renderer.textCount()
                    + ", audit violations=" + UiRuntime.audit(tree).size());

            return new Result(intactVerdict, tamperedVerdict, unsignedVerdict, screenRenders, author, notes);
        } finally {
            deleteTree(dir);
        }
    }

    private static List<ModReport> snapshot(Path dir) throws Exception {
        try (URLClassLoader cl = new URLClassLoader(new URL[]{dir.toUri().toURL()},
                ModVerifySelfTest.class.getClassLoader())) {
            return ModInspector.snapshot(cl);
        }
    }

    private static String verdictLine(List<ModReport> reports) {
        if (reports.isEmpty()) {
            return "(no mods)";
        }
        ModReport r = reports.get(0);
        return r.modId() + " → " + r.verdict() + " (" + r.classesChecked() + " cls, author='" + r.author() + "')";
    }

    /** Generate {@code public final class demo.DemoMod implements AetheriumMod} (id()="demo"). */
    private static byte[] generateMod() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, MOD_INTERNAL, null,
                "java/lang/Object", new String[]{"org/aetherium/core/mod/AetheriumMod"});

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "onInitialize",
                "(Lorg/aetherium/core/mod/AetheriumContext;)V", null, null);
        init.visitCode();
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 2);
        init.visitEnd();

        MethodVisitor id = cw.visitMethod(Opcodes.ACC_PUBLIC, "id", "()Ljava/lang/String;", null, null);
        id.visitCode();
        id.visitLdcInsn("demo");
        id.visitInsn(Opcodes.ARETURN);
        id.visitMaxs(1, 1);
        id.visitEnd();

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
