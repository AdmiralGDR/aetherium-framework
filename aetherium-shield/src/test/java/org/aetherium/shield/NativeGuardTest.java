/*
 * Aetherium Framework — native guard test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NativeGuardTest {

    @Test
    void checksumMatchesAcrossNativeAndJava() {
        NativeGuard g = NativeGuard.get();
        byte[] data = "Anomaly Core — редстоун-ядро".getBytes(StandardCharsets.UTF_8);
        // The native and pure-Java FNV-1a must agree byte-for-byte (same seed/prime), so verification is
        // comparable regardless of which path is live.
        assertEquals(NativeGuard.fnv1aJava(data), g.checksum(data),
                "native and Java FNV-1a must produce the identical checksum");
    }

    @Test
    void tracerPidIsWellDefined() {
        NativeGuard g = NativeGuard.get();
        int t = g.tracerPid();
        assertTrue(t >= -1, "tracerPid is -1 (unavailable), 0 (clean), or a positive pid");
        // instrumentationDetected() is just tracerPid()>0 — must not throw.
        g.instrumentationDetected();
    }

    @Test
    void nativeLibraryLoadsWhenBundled() {
        // The Zig .so is bundled under native/ on the test classpath, so the native path should be live here.
        // If a host lacks the toolchain the build skips it and this asserts the graceful fallback instead.
        NativeGuard g = NativeGuard.get();
        if (g.isNative()) {
            assertEquals(1, g.abiVersion(), "native ABI must be 1");
        } else {
            assertEquals(0, g.abiVersion(), "fallback reports ABI 0");
        }
    }
}
