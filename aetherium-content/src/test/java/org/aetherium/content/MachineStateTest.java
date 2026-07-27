/*
 * Aetherium Framework — MachineState remove/contains tests ().
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MachineStateTest {

    @Test
    void absentIsDistinctFromZero() {
        MachineState s = new MachineState();
        // "unclaimed" (absent) must be distinguishable from a value that happens to be 0.
        assertFalse(s.hasLong("owner"));
        assertEquals(-1L, s.getLong("owner", -1L)); // fallback used because absent
        s.setLong("owner", 0L);
        assertTrue(s.hasLong("owner"));
        assertEquals(0L, s.getLong("owner", -1L)); // present and zero, not the fallback
        s.removeLong("owner");
        assertFalse(s.hasLong("owner"));
        assertEquals(-1L, s.getLong("owner", -1L)); // absent again
    }

    @Test
    void stringRemoveAndContains() {
        MachineState s = new MachineState();
        assertFalse(s.hasString("faction"));
        s.setString("faction", "neutral");
        assertTrue(s.hasString("faction"));
        s.removeString("faction");
        assertFalse(s.hasString("faction"));
        assertEquals("none", s.getString("faction", "none"));
    }
}
