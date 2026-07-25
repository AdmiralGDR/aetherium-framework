/*
 * Aetherium Framework — config store tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.config;

import org.aetherium.core.AetheriumException;
import org.aetherium.network.Tree;
import org.aetherium.network.TreeNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigStoreTest {

    @Test
    void fullLifecycle() {
        ConfigSelfTest.Result r = ConfigSelfTest.run();
        assertTrue(r.passed(), () -> "config self-test failed: " + r.notes());
        assertTrue(r.wroteDefaults());
        assertTrue(r.roundTrip());
        assertTrue(r.validatorClamped());
        assertTrue(r.hotReloaded());
        assertTrue(r.containedBadEdit());
        assertTrue(r.reloadResultOk());
    }

    @Test
    void jsonRoundTripThroughTree() {
        TreeNode tree = Tree.object()
                .put("name", "Iron Vanguard")
                .put("level", 7L)
                .put("rate", 0.25)
                .put("open", true)
                .put("members", Tree.list(Tree.of("Steve"), Tree.of("Alex")))
                .build();
        String json = TreeJson.write(tree);
        assertEquals(tree, TreeJson.parse(json), "JSON must round-trip byte-exact through TreeNode");
    }

    @Test
    void malformedJsonIsRejected() {
        assertThrows(AetheriumException.class, () -> TreeJson.parse("{ \"a\": }"));
        assertThrows(AetheriumException.class, () -> TreeJson.parse("[1,2,3")); // unterminated
        assertThrows(AetheriumException.class, () -> TreeJson.parse("{} garbage")); // trailing content
    }

    @Test
    void deeplyNestedJsonIsRejectedNotStackOverflow() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < TreeJson.MAX_DEPTH + 50; i++) {
            deep.append("[");
        }
        assertThrows(AetheriumException.class, () -> TreeJson.parse(deep.toString()));
    }
}
