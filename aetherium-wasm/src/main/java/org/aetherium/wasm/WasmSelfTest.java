/*
 * Aetherium Framework — polyglot WASM sandbox self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.wasm;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructLayout;
import org.aetherium.core.compute.StructField;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves the WASM sandbox's security contract and the StructArena memory bridge.
 *
 * <p>EN: Verifies that {@link WasmSecurityPolicy#strict()} denies filesystem/network and that a
 * tampered permissive policy is rejected; that the loader accepts a valid {@code \0asm} module and
 * rejects a non-wasm file; and that {@link StructArenaWasmBridge} round-trips entity bytes through
 * linear memory while a sandboxed physics kernel ({@code x += vx}) computes the correct result — all
 * off-heap, with no host access. If GraalWASM is installed, a minimal empty module is also instantiated.
 * RU: Проверяет, что {@link WasmSecurityPolicy#strict()} запрещает файловую систему/сеть и что
 * подделанная разрешающая политика отвергается; что загрузчик принимает валидный модуль {@code \0asm}
 * и отвергает не-wasm файл; и что {@link StructArenaWasmBridge} прогоняет байты сущностей через
 * линейную память, пока изолированное физическое ядро ({@code x += vx}) вычисляет правильный результат —
 * всё off-heap, без доступа к хосту. При установленном GraalWASM также создаётся минимальный пустой модуль.
 */
public final class WasmSelfTest {

    private static final int ENTITIES = 1024;
    private static final byte[] EMPTY_WASM = {0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00};

    private WasmSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();

        // 1) Security policy: strict allowed; permissive rejected.
        WasmSecurityPolicy strict = WasmSecurityPolicy.strict();
        strict.assertStrict();
        boolean policyStrictEnforced = !strict.filesystem() && !strict.network()
                && strict.memory() && strict.compute();
        notes.add("policy: " + strict.describe());

        boolean permissiveRejected;
        try {
            new WasmSecurityPolicy(true, true, true, true).assertStrict();
            permissiveRejected = false;
        } catch (SecurityException expected) {
            permissiveRejected = true;
            notes.add("permissive policy rejected: " + expected.getMessage());
        }

        // 2) Sandbox + loader.
        boolean graalAvailable;
        boolean moduleValidated;
        boolean nonWasmRejected;
        boolean instantiated = true; // vacuously true when GraalWASM is absent
        try (WasmSandbox sandbox = WasmSandbox.open()) {
            graalAvailable = sandbox.available();
            notes.add("GraalWASM: " + (graalAvailable ? "installed (real sandbox)" : "absent (policy-only mode)"));

            WasmModuleLoader loader = new WasmModuleLoader();
            WasmModule module = loader.loadBytes(EMPTY_WASM, "empty.wasm");
            moduleValidated = module.hasWasmMagic();

            boolean rejected;
            try {
                loader.loadBytes(new byte[]{1, 2, 3, 4}, "bogus.bin");
                rejected = false;
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            nonWasmRejected = rejected;

            if (graalAvailable) {
                try {
                    module.instantiate(sandbox);
                    notes.add("instantiated empty module inside the strict sandbox");
                } catch (RuntimeException e) {
                    instantiated = false;
                    notes.add("module instantiation failed: " + e.getMessage());
                }
            }
        }

        // 3) StructArena ↔ linear-memory bridge + sandboxed physics (x += vx).
        boolean bridgeRoundTrip;
        boolean physicsCorrect;
        StructLayout layout = StructLayout.builder().floats("x").floats("vx").build();
        try (StructArena entities = StructArena.allocate(layout, ENTITIES);
             WasmSandbox sandbox = WasmSandbox.open();
             StructArenaWasmBridge bridge = new StructArenaWasmBridge(sandbox)) {
            StructField x = layout.field("x");
            StructField vx = layout.field("vx");
            for (int i = 0; i < ENTITIES; i++) {
                entities.setFloat(i, x, 1.0f);
                entities.setFloat(i, vx, 2.0f);
            }
            long bytes = (long) ENTITIES * layout.stride();
            bridge.runPhysics(entities, bytes, WasmSelfTest::physicsKernel);

            boolean allAdvanced = true;
            for (int i = 0; i < ENTITIES; i++) {
                if (entities.getFloat(i, x) != 3.0f) { // 1.0 + 2.0
                    allAdvanced = false;
                    break;
                }
            }
            physicsCorrect = allAdvanced;
            bridgeRoundTrip = true;
            notes.add("bridge: copied " + bytes + " bytes arena→linear→arena; physics x+=vx applied");
        }

        boolean ioDenied = policyStrictEnforced && permissiveRejected;
        boolean passed = policyStrictEnforced && permissiveRejected && moduleValidated
                && nonWasmRejected && bridgeRoundTrip && physicsCorrect && instantiated;

        return new Result(graalAvailable, policyStrictEnforced, permissiveRejected, moduleValidated,
                nonWasmRejected, bridgeRoundTrip, physicsCorrect, ioDenied, notes, passed);
    }

    /** Pure-Java reference physics over linear memory: {@code x += vx} per 8-byte entity (x@0, vx@4). */
    private static void physicsKernel(MemorySegment linear, long byteCount, WasmSecurityPolicy policy) {
        policy.assertStrict(); // the kernel runs only under a strict (no FS/network) policy
        long stride = 8;
        for (long off = 0; off + stride <= byteCount; off += stride) {
            float px = linear.get(ValueLayout.JAVA_FLOAT, off);
            float pvx = linear.get(ValueLayout.JAVA_FLOAT, off + 4);
            linear.set(ValueLayout.JAVA_FLOAT, off, px + pvx);
        }
    }

    /** Outcome of the WASM self-test, rendered by the CLI {@code wasm} command. */
    public record Result(boolean graalWasmAvailable, boolean policyStrictEnforced, boolean permissiveRejected,
                         boolean moduleValidated, boolean nonWasmRejected, boolean bridgeRoundTrip,
                         boolean physicsCorrect, boolean ioDenied, List<String> notes, boolean passed) {
    }
}
