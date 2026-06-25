/*
 * Aetherium Framework — end-to-end test of the @AetheriumInit zero-config processor (real javac run).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real annotation processor through an in-process {@code javac} run and asserts that a
 * developer who writes only {@code @AetheriumInit} methods gets a fully-generated, reflection-free
 * entrypoint + {@code ServiceLoader} registration — the zero-config claim, proven end-to-end.
 */
final class AetheriumInitProcessorTest {

    @Test
    void generatesEntrypointAndServiceFromAnnotatedMethodsOnly(@TempDir Path dir) throws IOException {
        // A mod author writes NO AetheriumMod class and NO services file — just annotated static methods.
        Path src = Files.writeString(dir.resolve("DemoMod.java"), """
                package demo;
                import org.aetherium.core.mod.AetheriumContext;
                import org.aetherium.core.mod.AetheriumInit;
                public final class DemoMod {
                    @AetheriumInit(runAfter = "DemoMod.registry")
                    public static void render(AetheriumContext ctx) { ctx.log("render"); }
                    @AetheriumInit
                    public static void registry(AetheriumContext ctx) { ctx.log("registry"); }
                }
                """);

        Path classes = Files.createDirectories(dir.resolve("classes"));
        Path genSrc = Files.createDirectories(dir.resolve("generated"));

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        // Run javac with our processor on the current runtime classpath (core + datagen + this processor).
        int rc = javac.run(null, null, err, new String[]{
                "-classpath", System.getProperty("java.class.path"),
                "-processor", "org.aetherium.content.AetheriumInitProcessor",
                "-Aaetherium.modId=demo",
                "-d", classes.toString(),
                "-s", genSrc.toString(),
                src.toString()
        });
        assertEquals(0, rc, () -> "javac failed:\n" + err);

        // (1) A generated, mod-id-scoped AetheriumMod source was emitted.
        Path generated = genSrc.resolve("org/aetherium/generated/AetheriumGeneratedInit_demo.java");
        assertTrue(Files.exists(generated), "generated entrypoint missing");
        String code = Files.readString(generated);
        assertTrue(code.contains("implements org.aetherium.core.mod.AetheriumMod"), code);
        // (2) Direct static calls, in DAG order: registry (no deps) before render (runAfter registry).
        assertTrue(code.contains("demo.DemoMod.registry(context);"), code);
        assertTrue(code.contains("demo.DemoMod.render(context);"), code);
        assertTrue(code.indexOf("registry(context)") < code.indexOf("render(context)"), code);
        // (3) Zero runtime reflection in the generated entrypoint.
        assertTrue(!code.contains("java.lang.reflect") && !code.contains(".invoke("), code);
        // (4) ServiceLoader registration was generated — no hand-written services file.
        Path service = classes.resolve("META-INF/services/org.aetherium.core.mod.AetheriumMod");
        assertTrue(Files.exists(service), "generated services file missing");
        assertTrue(Files.readString(service).contains(
                "org.aetherium.generated.AetheriumGeneratedInit_demo"));
        // (5) The generated entrypoint itself compiles cleanly (it was emitted to -d as a .class too).
        assertTrue(Files.exists(classes.resolve("org/aetherium/generated/AetheriumGeneratedInit_demo.class")),
                "generated entrypoint did not compile");
    }

    @Test
    void rejectsABadSignatureAtCompileTime(@TempDir Path dir) throws IOException {
        // Non-static @AetheriumInit — must be a compile error, not silently ignored.
        Path src = Files.writeString(dir.resolve("BadMod.java"), """
                package demo;
                import org.aetherium.core.mod.AetheriumContext;
                import org.aetherium.core.mod.AetheriumInit;
                public final class BadMod {
                    @AetheriumInit
                    public void notStatic(AetheriumContext ctx) { }
                }
                """);
        Path classes = Files.createDirectories(dir.resolve("classes"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int rc = javac.run(null, null, err, new String[]{
                "-classpath", System.getProperty("java.class.path"),
                "-processor", "org.aetherium.content.AetheriumInitProcessor",
                "-d", classes.toString(),
                src.toString()
        });
        assertTrue(rc != 0, "expected a compile error for a non-static @AetheriumInit method");
        assertTrue(err.toString().contains("public static"), err::toString);
    }

    @Test
    void missingModIdFailsTheBuild(@TempDir Path dir) throws IOException {
        // An @AetheriumInit method but NO -Aaetherium.modId → must be a hard error (no silent default).
        Path src = Files.writeString(dir.resolve("NoIdMod.java"), """
                package demo;
                import org.aetherium.core.mod.AetheriumContext;
                import org.aetherium.core.mod.AetheriumInit;
                public final class NoIdMod {
                    @AetheriumInit
                    public static void setup(AetheriumContext ctx) { }
                }
                """);
        Path classes = Files.createDirectories(dir.resolve("classes"));
        Path genSrc = Files.createDirectories(dir.resolve("generated"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int rc = javac.run(null, null, err, new String[]{
                "-classpath", System.getProperty("java.class.path"),
                "-processor", "org.aetherium.content.AetheriumInitProcessor",
                // deliberately NO -Aaetherium.modId
                "-d", classes.toString(), "-s", genSrc.toString(),
                src.toString()
        });
        assertTrue(rc != 0, "expected a build failure when -Aaetherium.modId is missing");
        assertTrue(err.toString().contains("aetherium.modId"), err::toString);
        // And nothing collision-prone was generated.
        assertTrue(!Files.exists(genSrc.resolve("org/aetherium/generated")));
    }

    @Test
    void noAnnotationsGeneratesNothing(@TempDir Path dir) throws IOException {
        Path src = Files.writeString(dir.resolve("Plain.java"), """
                package demo;
                public final class Plain { public static void main(String[] a) { } }
                """);
        Path classes = Files.createDirectories(dir.resolve("classes"));
        Path genSrc = Files.createDirectories(dir.resolve("generated"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        int rc = javac.run(null, null, null, new String[]{
                "-classpath", System.getProperty("java.class.path"),
                "-processor", "org.aetherium.content.AetheriumInitProcessor",
                "-d", classes.toString(), "-s", genSrc.toString(),
                src.toString()
        });
        assertEquals(0, rc);
        // Nothing under the generated package, and no AetheriumMod service.
        assertTrue(!Files.exists(genSrc.resolve("org/aetherium/generated")));
        assertTrue(!Files.exists(classes.resolve("META-INF/services/org.aetherium.core.mod.AetheriumMod")));
    }
}
