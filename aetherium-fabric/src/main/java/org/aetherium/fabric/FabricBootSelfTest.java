/*
 * Aetherium Framework — Fabric loader-agnosticism self-test (WS-5).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fabric;

import org.aetherium.bytecode.runtime.DispatchTable;
import org.aetherium.core.CapabilityTier;
import org.aetherium.core.mod.AetheriumContext;
import org.aetherium.core.mod.AetheriumMod;
import org.aetherium.transformer.AetheriumSymbols;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Proves the framework boots identically under Fabric — offline, without Loom or Minecraft.
 *
 * <p>EN: Drives {@link FabricBoot} with a fake {@link AetheriumMod} and asserts (1) the shared dispatch table
 * is installed from {@link AetheriumSymbols#MANIFEST} and actually resolves the {@code compute:doubler}
 * handle, and (2) the mod SPI initializes with a real {@link AetheriumContext} — the same sequence the
 * NeoForge entrypoint runs. Because it touches only shared modules, this is a genuine loader-agnosticism
 * proof, not a mock.
 * RU: Прогоняет {@link FabricBoot} с фиктивным {@link AetheriumMod} и проверяет: (1) общая таблица диспатча
 * установлена из {@link AetheriumSymbols#MANIFEST} и действительно разрешает хэндл {@code compute:doubler};
 * (2) SPI мода инициализируется с настоящим {@link AetheriumContext} — та же последовательность, что у
 * точки входа NeoForge. Настоящее доказательство loader-агностичности, не мок.
 */
public final class FabricBootSelfTest {

    private FabricBootSelfTest() {
    }

    /** Structured outcome. */
    public record Result(boolean dispatchInstalled, boolean dispatchResolves, boolean modInitialized,
                         boolean contextTierExposed, List<String> notes) {
        public boolean passed() {
            return dispatchInstalled && dispatchResolves && modInitialized && contextTierExposed;
        }
    }

    public static Result run() {
        List<String> notes = new java.util.ArrayList<>();

        // (1) Install the SHARED dispatch table (identical to NeoForge's DispatchBootstrap).
        int handles = FabricBoot.installDispatchTable();
        boolean dispatchInstalled = handles > 0 && handles == AetheriumSymbols.MANIFEST.size();
        notes.add("dispatch table: installed " + handles + " handle(s) from the shared manifest ("
                + AetheriumSymbols.MANIFEST.size() + " expected)");

        // (1b) The installed handle must actually resolve + compute — proving the O(1) table is loader-neutral.
        boolean dispatchResolves = false;
        try {
            int id = AetheriumSymbols.MANIFEST.idOf("compute:doubler").orElseThrow();
            int doubled = (int) DispatchTable.handle(id).invokeExact(21);
            dispatchResolves = doubled == 42;
            notes.add("dispatch handle compute:doubler(21) = " + doubled + " (want 42)");
        } catch (Throwable t) {
            notes.add("dispatch handle failed to resolve: " + t);
        }

        // (2) Initialize a fake AetheriumMod through the loader-neutral path.
        AtomicBoolean initialized = new AtomicBoolean(false);
        AtomicBoolean sawTier = new AtomicBoolean(false);
        AetheriumMod fake = new AetheriumMod() {
            @Override
            public void onInitialize(AetheriumContext context) {
                context.log("fabric self-test mod up");
                sawTier.set(context.computeTier() != null);
                initialized.set(true);
            }

            @Override
            public String id() {
                return "aetherium_fabric_selftest";
            }
        };
        int count = FabricBoot.initializeMods(List.of(fake), CapabilityTier.PURE_JAVA);
        boolean modInitialized = initialized.get() && count == 1;
        notes.add("mod SPI: initialized " + count + " mod via the loader-neutral path (onInitialize ran="
                + initialized.get() + ")");

        return new Result(dispatchInstalled, dispatchResolves, modInitialized, sawTier.get(), notes);
    }
}
