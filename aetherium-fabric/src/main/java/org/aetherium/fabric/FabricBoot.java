/*
 * Aetherium Framework — loader-neutral boot, driven from the Fabric entrypoint (WS-5).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fabric;

import org.aetherium.bytecode.runtime.DispatchTable;
import org.aetherium.core.CapabilityTier;
import org.aetherium.core.SymbolManifest;
import org.aetherium.core.mod.AetheriumContext;
import org.aetherium.core.mod.AetheriumMod;
import org.aetherium.shield.IntegrityManifest;
import org.aetherium.shield.ModVerifier;
import org.aetherium.transformer.AetheriumSymbols;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * The framework's boot sequence, run under Fabric — <strong>identical</strong> to what the NeoForge entrypoint
 * runs, which is the whole point: the initialization, the {@code O(1)} dispatch table, the
 * {@link AetheriumMod} SPI, and the Shield integrity enforcement are all loader-agnostic. Only the outer
 * shell (a {@code ModInitializer} here, a {@code @Mod} there) differs.
 *
 * <p>EN: Uses only shared, Minecraft-free modules ({@code core}/{@code bytecode}/{@code transformer}/
 * {@code shield}), so it compiles and runs without Fabric Loom or a remapped Minecraft — the boot-agnosticism
 * is provable offline. It installs the same {@link AetheriumSymbols#MANIFEST}-keyed dispatch table both
 * loaders share, then discovers every {@link AetheriumMod} via {@code ServiceLoader}, refuses tampered mods
 * (integrity manifest), and initializes the rest with an {@link AetheriumContext}. {@code java.util.logging}
 * keeps it zero-extra-dependency (Fabric provides SLF4J, but the boot must not require it).
 * RU: Использует только общие модули без Minecraft, поэтому компилируется и работает без Fabric Loom и
 * ремапнутого Minecraft — агностичность загрузки доказуема офлайн. Ставит ту же таблицу диспатча по
 * {@link AetheriumSymbols#MANIFEST}, что и оба загрузчика, находит все {@link AetheriumMod} через
 * {@code ServiceLoader}, отказывает подделанным (манифест целостности) и инициализирует остальные.
 */
public final class FabricBoot {

    private static final Logger LOG = Logger.getLogger("Aetherium/Fabric");

    private FabricBoot() {
    }

    /** Outcome of a Fabric boot: how many dispatch handles were installed and how many mods initialized. */
    public record Result(int dispatchHandles, int modsInitialized) {
    }

    /** Reference dispatch target: doubles its input — the same symbol both loaders wire into the table. */
    static int doubler(int x) {
        return x * 2;
    }

    /** Run the full framework boot under Fabric. Never throws — a boot failure degrades, never crashes. */
    public static Result boot() {
        int handles = installDispatchTable();
        ClassLoader cl = FabricBoot.class.getClassLoader();
        int mods = initializeMods(ServiceLoader.load(AetheriumMod.class, cl), CapabilityTier.PURE_JAVA);
        LOG.info("Aetherium (Fabric) booted: " + handles + " dispatch handle(s), " + mods + " mod(s).");
        return new Result(handles, mods);
    }

    /**
     * Install the {@code invokedynamic} dispatch table from the SHARED {@link AetheriumSymbols#MANIFEST} — the
     * identical table {@code DispatchBootstrap} installs on NeoForge, so a lowered API call resolves the same
     * way under either loader. Never throws.
     */
    public static int installDispatchTable() {
        try {
            SymbolManifest manifest = AetheriumSymbols.MANIFEST;
            MethodHandle[] handles = new MethodHandle[manifest.size()];
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            handles[manifest.idOf("compute:doubler").orElseThrow()] =
                    lookup.findStatic(FabricBoot.class, "doubler", MethodType.methodType(int.class, int.class));
            DispatchTable.install(handles);
            return DispatchTable.size();
        } catch (Throwable t) {
            LOG.warning("Aetherium (Fabric) dispatch table install failed; lowered calls degrade (" + t + ").");
            return 0;
        }
    }

    /**
     * Discover + initialize {@link AetheriumMod}s — identical to the NeoForge path. Package-visible with an
     * explicit {@code Iterable} so a self-test can drive it without a real {@code ServiceLoader} entry.
     */
    static int initializeMods(Iterable<AetheriumMod> mods, CapabilityTier tier) {
        AetheriumContext context = new FabricContext(tier);
        final boolean enforce =
                !"false".equalsIgnoreCase(System.getProperty("aetherium.shield.enforce", "true"));
        final ClassLoader cl = FabricBoot.class.getClassLoader();
        final IntegrityManifest manifest = ModVerifier.loadManifest(cl);

        int initialized = 0;
        for (AetheriumMod mod : mods) {
            try {
                if (ModVerifier.verifyClass(cl, manifest, mod.getClass().getName())
                        == ModVerifier.Verdict.TAMPERED) {
                    if (enforce) {
                        LOG.severe("Aetherium (Fabric) REFUSES tampered mod '" + mod.id()
                                + "' — bytes do not match the Shield integrity manifest.");
                        continue;
                    }
                    LOG.warning("Aetherium (Fabric) mod '" + mod.id() + "' is TAMPERED; continuing (enforce off).");
                }
                mod.onInitialize(context);
                initialized++;
            } catch (Throwable modFailure) {
                // One bad mod must never crash the launch.
                LOG.warning("Aetherium (Fabric) mod '" + mod.id() + "' failed to initialize; skipping ("
                        + modFailure + ").");
            }
        }
        return initialized;
    }

    /** The Fabric-side {@link AetheriumContext} — mirrors the NeoForge {@code LoggingContext}. */
    private record FabricContext(CapabilityTier tier) implements AetheriumContext {
        @Override
        public void log(String message) {
            LOG.info("[mod] " + message);
        }

        @Override
        public CapabilityTier computeTier() {
            return tier;
        }
    }
}
