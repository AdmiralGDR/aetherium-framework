/*
 * Aetherium Framework — tests for the @AetheriumInit ordering + source generation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure unit coverage of the zero-config auto-wiring sort and code generator. */
final class InitWiringTest {

    private static InitMethod init(String id, String owner, String method, List<String> before, List<String> after) {
        return new InitMethod(id, owner, method, before, after);
    }

    @Test
    void resolvesRunAfterIntoDeterministicOrder() {
        // Declared deliberately out of order; "render" must follow "registry" which must follow "core".
        List<InitMethod> inits = List.of(
                init("render", "com.x.Render", "init", List.of(), List.of("registry")),
                init("core", "com.x.Core", "boot", List.of(), List.of()),
                init("registry", "com.x.Reg", "setup", List.of(), List.of("core")));

        List<String> order = InitOrdering.order(inits).stream().map(InitMethod::id).toList();
        assertEquals(List.of("core", "registry", "render"), order);
    }

    @Test
    void runBeforeIsTheMirrorOfRunAfter() {
        List<InitMethod> inits = List.of(
                init("a", "com.x.A", "a", List.of("b"), List.of()),
                init("b", "com.x.B", "b", List.of(), List.of()));
        assertEquals(List.of("a", "b"), InitOrdering.order(inits).stream().map(InitMethod::id).toList());
    }

    @Test
    void unknownConstraintTargetsAreSoftlyIgnored() {
        // "ghost" is not present — the edge is ignored, not an error.
        List<InitMethod> inits = List.of(
                init("only", "com.x.Only", "go", List.of(), List.of("ghost")));
        assertEquals(List.of("only"), InitOrdering.order(inits).stream().map(InitMethod::id).toList());
    }

    @Test
    void cyclicConstraintsFailLoudly() {
        List<InitMethod> inits = List.of(
                init("a", "com.x.A", "a", List.of(), List.of("b")),
                init("b", "com.x.B", "b", List.of(), List.of("a")));
        assertThrows(IllegalStateException.class, () -> InitOrdering.order(inits));
    }

    @Test
    void duplicateIdsFailLoudly() {
        List<InitMethod> inits = List.of(
                init("dup", "com.x.A", "a", List.of(), List.of()),
                init("dup", "com.x.B", "b", List.of(), List.of()));
        assertThrows(IllegalStateException.class, () -> InitOrdering.order(inits));
    }

    @Test
    void generatedSourceMakesDirectStaticCallsInOrderAndImplementsAetheriumMod() {
        List<InitMethod> ordered = List.of(
                init("core", "com.x.Core", "boot", List.of(), List.of()),
                init("render", "com.x.Render", "init", List.of(), List.of("core")));

        String src = InitSourceWriter.generate("my-mod", ordered);

        assertTrue(src.contains("implements org.aetherium.core.mod.AetheriumMod"), src);
        assertTrue(src.contains("com.x.Core.boot(context);"), src);
        assertTrue(src.contains("com.x.Render.init(context);"), src);
        // The core call must be emitted before the render call (DAG order is preserved in the source).
        assertTrue(src.indexOf("com.x.Core.boot") < src.indexOf("com.x.Render.init"), src);
        // Class name is mod-id-scoped (no collision between two Aetherium mods on one classpath) and legal.
        assertEquals("org.aetherium.generated.AetheriumGeneratedInit_my_mod",
                InitSourceWriter.qualifiedName("my-mod"));
        // No reflection anywhere in the generated entrypoint.
        assertTrue(!src.contains("java.lang.reflect") && !src.contains(".invoke("), src);
    }

    @Test
    void sideDeclaredInitsAreGatedInTheGeneratedSource() {
        // a BOTH init is an unguarded direct call (backward compatible); a SERVER or CLIENT init is
        // routed through context.runsOnSide(Side.X) so a client-side init never runs on a dedicated server.
        List<InitMethod> ordered = List.of(
                new InitMethod("boot", "com.x.Core", "boot", List.of(), List.of(), "BOTH"),
                new InitMethod("srv", "com.x.Srv", "server", List.of(), List.of(), "SERVER"),
                new InitMethod("cli", "com.x.Cli", "client", List.of(), List.of(), "CLIENT"));

        String src = InitSourceWriter.generate("sided", ordered);

        // BOTH: unguarded direct call.
        assertTrue(src.contains("com.x.Core.boot(context);"), src);
        assertTrue(!src.contains("runsOnSide(org.aetherium.core.mod.Side.BOTH)"), "BOTH must not be gated: " + src);
        // SERVER + CLIENT: guarded by the matching side.
        assertTrue(src.contains("if (context.runsOnSide(org.aetherium.core.mod.Side.SERVER)) {"), src);
        assertTrue(src.contains("if (context.runsOnSide(org.aetherium.core.mod.Side.CLIENT)) {"), src);
        assertTrue(src.contains("com.x.Cli.client(context);"), src);
    }

    @Test
    void generatedClassNameIsAlwaysALegalJavaIdentifier() {
        // Hyphens, dots, leading digits, blanks — every mod id must yield a compilable class name.
        for (String modId : List.of("my-mod", "com.example.cool", "9lives", "", "  ", "weird!@#name")) {
            String cls = InitSourceWriter.className(modId);
            assertTrue(Character.isJavaIdentifierStart(cls.charAt(0)), () -> "bad start: " + cls);
            for (int i = 1; i < cls.length(); i++) {
                assertTrue(Character.isJavaIdentifierPart(cls.charAt(i)), () -> "bad part in: " + cls);
            }
        }
        assertEquals("org.aetherium.generated.AetheriumGeneratedInit_my_mod",
                InitSourceWriter.qualifiedName("my-mod"));
    }
}
