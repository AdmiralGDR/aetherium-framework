/*
 * Aetherium Framework — Fabric loader-agnosticism test (WS-5).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class FabricBootTest {

    @Test
    void frameworkBootsIdenticallyUnderFabric() {
        FabricBootSelfTest.Result r = FabricBootSelfTest.run();
        assertTrue(r.dispatchInstalled(), () -> "shared dispatch table not installed: " + r.notes());
        assertTrue(r.dispatchResolves(), () -> "dispatch handle did not resolve to 42: " + r.notes());
        assertTrue(r.modInitialized(), () -> "AetheriumMod SPI did not initialize: " + r.notes());
        assertTrue(r.contextTierExposed(), () -> "AetheriumContext tier missing: " + r.notes());
        assertTrue(r.passed(), () -> "Fabric boot self-test failed: " + r.notes());
    }
}
