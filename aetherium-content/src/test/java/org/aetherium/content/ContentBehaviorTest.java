/*
 * Aetherium Framework — content-behavior tests (self-test + processor end-to-end).
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

final class ContentBehaviorTest {

    @Test
    void behaviorSelfTestPasses() {
        ContentBehaviorSelfTest.Result r = ContentBehaviorSelfTest.run();
        assertTrue(r.passed(), () -> "content behavior self-test failed: " + r.notes());
        assertEquals(2, r.smeltCount());
    }

    @Test
    void processorEmitsMachineLogicBehaviorIndex(@TempDir Path dir) throws IOException {
        // A modder writes only an @AetheriumBlock with a machine-logic behavior class.
        Path src = Files.writeString(dir.resolve("Machines.java"), """
                package demo;
                import org.aetherium.content.AetheriumBlock;
                import org.aetherium.content.AetheriumMachineLogic;
                import org.aetherium.content.MachineContext;

                class FurnaceLogic implements AetheriumMachineLogic {
                    public void tick(MachineContext ctx) { ctx.state().increment("p", 1); }
                }

                @AetheriumBlock(name = "iron_furnace", behavior = FurnaceLogic.class)
                final class IronFurnace { }
                """);

        Path classes = Files.createDirectories(dir.resolve("classes"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int rc = javac.run(null, null, err, new String[]{
                "-classpath", System.getProperty("java.class.path"),
                "-processor", "org.aetherium.content.AetheriumContentProcessor",
                "-Aaetherium.modId=demo",
                "-d", classes.toString(),
                src.toString()
        });
        assertEquals(0, rc, () -> "javac failed:\n" + err);

        // The processor must have written a behaviors index marking the block as machine logic.
        Path index = classes.resolve("META-INF/aetherium/behaviors.index");
        assertTrue(Files.exists(index), "behaviors.index missing");
        String body = Files.readString(index);
        assertTrue(body.contains("BLOCK|demo|iron_furnace|demo.FurnaceLogic|true"),
                () -> "unexpected behaviors.index:\n" + body);
    }
}
