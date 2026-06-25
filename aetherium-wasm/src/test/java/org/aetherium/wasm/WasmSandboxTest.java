/*
 * Aetherium Framework — WASM sandbox tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.wasm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EN: Verifies the security contract (deny FS/network), the loader's magic check, and that the
 * StructArena bridge round-trips a sandboxed physics computation.
 * RU: Проверяет контракт безопасности (запрет ФС/сети), проверку магии загрузчиком и round-trip
 * изолированного физического вычисления через мост StructArena.
 */
class WasmSandboxTest {

    @Test
    void strictPolicyDeniesFilesystemAndNetwork() {
        WasmSecurityPolicy p = WasmSecurityPolicy.strict();
        assertFalse(p.filesystem(), "filesystem must be denied");
        assertFalse(p.network(), "network must be denied");
        assertTrue(p.memory() && p.compute(), "memory + compute must be allowed");
        p.assertStrict();
    }

    @Test
    void permissivePolicyIsRejected() {
        assertThrows(SecurityException.class,
                () -> new WasmSecurityPolicy(true, false, true, true).assertStrict());
        assertThrows(SecurityException.class,
                () -> new WasmSecurityPolicy(false, true, true, true).assertStrict());
    }

    @Test
    void loaderRejectsNonWasm() {
        WasmModuleLoader loader = new WasmModuleLoader();
        assertThrows(IllegalArgumentException.class,
                () -> loader.loadBytes(new byte[]{1, 2, 3, 4}, "bogus.bin"));
        assertTrue(loader.loadBytes(new byte[]{0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00}, "ok.wasm")
                .hasWasmMagic());
    }

    @Test
    void selfTestPasses() {
        WasmSelfTest.Result r = WasmSelfTest.run();
        assertTrue(r.passed(), () -> "wasm self-test failed: " + r.notes());
        assertTrue(r.ioDenied(), "FS/network must be denied");
        assertTrue(r.physicsCorrect(), "bridge physics must compute x += vx");
    }
}
