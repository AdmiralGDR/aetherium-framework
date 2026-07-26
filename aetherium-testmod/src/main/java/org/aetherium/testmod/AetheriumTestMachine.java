/*
 * Aetherium Framework — declarative machine block (E2E dispatch sample). 
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.content.AetheriumBlock;

/**
 * A machine block declared with a single annotation — {@code behavior = TestMachineLogic.class} — so the
 * loader registers a ticking {@code BlockEntity} that dispatches {@link TestMachineLogic}'s callbacks in-game
 * (). The class body is empty; there is nothing left to write.
 */
@AetheriumBlock(name = "test_machine", modId = "aetherium", hardness = 3.0f, resistance = 3.0f,
        displayName = "Aetherium Test Machine", behavior = TestMachineLogic.class)
public final class AetheriumTestMachine {
}
