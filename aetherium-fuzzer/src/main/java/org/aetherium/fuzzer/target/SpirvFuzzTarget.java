/*
 * Aetherium Framework — fuzz target: the SPIR-V structural verifier + header accessors + dispatch.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer.target;

import org.aetherium.compute.SpirvModule;
import org.aetherium.compute.SpirvVulkanDispatch;
import org.aetherium.fuzzer.FuzzTarget;
import org.aetherium.native_bridge.VulkanProbe;

import java.util.random.RandomGenerator;

/**
 * Feeds arbitrary bytes to {@link SpirvModule#wrap(byte[])} and exercises every read path on the result.
 *
 * <p>EN: Wraps hostile input (empties, runts, unaligned lengths, valid-magic-then-garbage, bit-flips of a
 * real module) and then calls <em>all</em> header accessors ({@code magic/version/idBound/wordCount/
 * headerHex}), {@link SpirvModule#verify()}, and offline {@link SpirvVulkanDispatch dispatch}. The
 * contract is the strongest possible: <strong>nothing may throw</strong> — the accessors are bounds-safe
 * and {@code verify()} returns a result object rather than throwing. (This target is what surfaced the
 * pre-bug where the header accessors read past a truncated buffer and threw
 * {@code IndexOutOfBoundsException}; they are now guarded.)
 * RU: Оборачивает враждебный вход и вызывает <em>все</em> аксессоры заголовка, {@link SpirvModule#verify()}
 * и офлайн-{@link SpirvVulkanDispatch диспетч}. Контракт сильнейший: <strong>ничто не должно бросать</strong>.
 * Эта цель выявила добытый до фазы 16 баг, когда аксессоры читали за усечённым буфером и бросали
 * {@code IndexOutOfBoundsException}; теперь они защищены.
 */
public final class SpirvFuzzTarget implements FuzzTarget {

    // SPIR-V magic 0x07230203 as little-endian bytes (so magic-prefixed cases reach the parse path).
    private static final int[] SPIRV_MAGIC = {0x07230203};
    private static final int MAX_LEN = 512;

    private final SpirvVulkanDispatch dispatch = new SpirvVulkanDispatch(VulkanProbe.unavailable(0));

    @Override
    public String name() {
        return "spirv.wrap+verify+dispatch";
    }

    @Override
    public void exercise(RandomGenerator rng) {
        byte[] blob = FuzzBytes.hostile(rng, SPIRV_MAGIC, MAX_LEN);
        SpirvModule module = SpirvModule.wrap(blob);

        // Every accessor must be total (never throw) on unverified bytes.
        module.magic();
        module.version();
        module.idBound();
        module.wordCount();
        module.headerHex();

        SpirvModule.Verification v = module.verify();
        // Dispatch consults verify() internally; a malformed module must be cleanly rejected, not crash.
        SpirvVulkanDispatch.DispatchResult result = dispatch.dispatch(module);
        if (v.valid() != result.uploaded()) {
            // An internal inconsistency between verify() and dispatch is itself a defect worth a crash.
            throw new IllegalStateException("verify/dispatch disagree: valid=" + v.valid()
                    + " uploaded=" + result.uploaded());
        }
    }

    // expects(): default — nothing may throw here.
}
