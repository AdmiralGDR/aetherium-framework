/*
 * Aetherium Framework — key-constant tests ().
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class KeysTest {

    @Test
    void constantsMatchGlfwCodes() {
        // The feedback's exact example: G is 71. Pin the anchors so a typo in the table is caught.
        assertEquals(71, Keys.G, "Keys.G must be the GLFW code 71 (the feedback's example)");
        assertEquals(65, Keys.A);
        assertEquals(90, Keys.Z);
        assertEquals(48, Keys.NUM_0);
        assertEquals(290, Keys.F1);
        assertEquals(265, Keys.UP);
        assertEquals(340, Keys.LEFT_SHIFT);
        assertEquals(256, Keys.ESCAPE);
    }
}
