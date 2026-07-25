/*
 * Aetherium Framework — NeoForge @Mod entrypoint.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.aetherium.core.CapabilityTier;
import org.aetherium.core.mod.AetheriumContext;
import org.aetherium.core.mod.AetheriumMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * The single point where Aetherium meets NeoForge.
 *
 * <p>EN: This is the <strong>only</strong> class in the framework that references NeoForge types.
 * Constructed by FML, it hooks {@code FMLConstructModEvent} — the earliest mod-lifecycle phase — and
 * then, in order: (1) runs the {@link PreFlightCheck} (ASM + native self-test, total/non-throwing),
 * (2) installs the {@code invokedynamic} {@link DispatchBootstrap dispatch table} so transformed mod
 * classes can link, and (3) discovers every {@link AetheriumMod} via {@code ServiceLoader} and calls
 * {@code onInitialize} with a loader-agnostic {@link AetheriumContext}. Mods never see this class or
 * any NeoForge API.
 *
 * <p>RU: Это <strong>единственный</strong> класс фреймворка, ссылающийся на типы NeoForge.
 * Конструируется FML, перехватывает {@code FMLConstructModEvent} — самую раннюю фазу жизненного
 * цикла мода — и затем по порядку: (1) выполняет {@link PreFlightCheck} (самопроверка ASM + native,
 * тотальная/не бросающая), (2) устанавливает таблицу диспетчеризации {@code invokedynamic}
 * ({@link DispatchBootstrap}), чтобы преобразованные классы модов могли линковаться, и (3) находит
 * каждый {@link AetheriumMod} через {@code ServiceLoader} и вызывает {@code onInitialize} с
 * независимым от загрузчика {@link AetheriumContext}. Моды никогда не видят этот класс или API
 * NeoForge.
 */
@Mod(AetheriumNeoForgeEntrypoint.MOD_ID)
public final class AetheriumNeoForgeEntrypoint {

    public static final String MOD_ID = "aetherium";

    private static final Logger LOG = LoggerFactory.getLogger("Aetherium");

    private volatile CapabilityTier tier = CapabilityTier.PURE_JAVA;

    /** FML invokes this constructor, handing us the mod event bus. */
    public AetheriumNeoForgeEntrypoint(IEventBus modEventBus) {
        LOG.info("Aetherium loader constructing — hooking FMLConstructModEvent.");
        modEventBus.addListener(this::onConstruct);
        // Bridge the loader-agnostic network SPI to NeoForge's payload system (mod-bus event).
        modEventBus.addListener(AetheriumNetworkBridge::register);
        // Auto-register declarative @AetheriumBlock/@AetheriumItem content (mod-bus RegisterEvent).
        // The registrar reads the build-time content index; no-op when a mod declares nothing.
        AetheriumContentRegistrar contentRegistrar = new AetheriumContentRegistrar();
        if (contentRegistrar.hasContent()) {
            modEventBus.addListener(contentRegistrar::onRegister);
        }
        // Renderer bridging touches client-only Blaze3D types — register it only on the client dist.
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(AetheriumRenderBridge::register);
        }
    }

    /** Earliest safe hook: prepare the framework before normal mods construct. */
    private void onConstruct(FMLConstructModEvent event) {
        // (1) Pre-Flight Check — never throws; degrades gracefully.
        PreFlightCheck.Report report = PreFlightCheck.run();
        report.lines().forEach(LOG::info);
        this.tier = report.tier();
        if (!report.launchAllowed()) {
            LOG.error("Aetherium Pre-Flight Check did not allow launch; continuing in safe mode.");
        }

        // Sleek one-time boot banner with live hardware-acceleration status (never spams the log).
        logBootBanner(report);

        // (2) Install the invokedynamic dispatch table before any lowered mod call runs.
        int handles = DispatchBootstrap.installDefaultTable();
        LOG.info("Installed Aetherium dispatch table ({} handle(s)).", handles);

        // (3) Wire the Platform Abstraction Layer: feed NeoForge game events into the edge PAL so
        //     Aetherium mods can reach vanilla entities/events through the loader-agnostic bridge.
        NeoForge.EVENT_BUS.register(new NeoForgePlatformEvents());
        // Translate the loader-agnostic EdgeCommands SPI into Brigadier on RegisterCommandsEvent.
        NeoForge.EVENT_BUS.register(new NeoForgeCommandBridge());
        // Register the built-in /aetherium <mods|verify|inspect> command (in-game verification & analysis).
        new AetheriumCommands(getClass().getClassLoader()).register(NeoForgeCommandBridge.commands());
        LOG.info("Aetherium PAL bridge registered (platform=neoforge).");

        // (4) Discover and initialize loader-agnostic Aetherium mods.
        initializeAetheriumMods();
    }

    /** Compose the dynamic boot banner from the pre-flight report + live SIMD/AppCDS probes. */
    private void logBootBanner(PreFlightCheck.Report report) {
        boolean simdActive = org.aetherium.core.simd.SimdMath.isVectorApiAvailable();
        int simdBits = org.aetherium.core.simd.SimdMath.simdFloatBits();
        int appCdsEntries = appCdsEntryCount();
        BootBanner.Status status = new BootBanner.Status(
                version(), simdActive, simdBits, appCdsEntries,
                report.vulkanAvailable(), report.vulkanDeviceCount(), tier.name());
        BootBanner.render(status).forEach(LOG::info);
    }

    /** Cached transformed-class count, or {@code -1} if the AppCDS cache is disabled/unavailable. */
    private static int appCdsEntryCount() {
        if ("false".equalsIgnoreCase(System.getProperty("aetherium.cds.enabled", "true"))) {
            return -1;
        }
        try {
            return AppCdsManager.open(AppCdsManager.defaultDir()).stats().entries();
        } catch (Throwable unavailable) {
            return -1;
        }
    }

    /** Framework version from the jar manifest, with a sane fallback outside a packaged jar. */
    private static String version() {
        String v = AetheriumNeoForgeEntrypoint.class.getPackage().getImplementationVersion();
        return v != null ? v : "1.0.0-SNAPSHOT";
    }

    private void initializeAetheriumMods() {
        AetheriumContext context = new LoggingContext(LOG, tier);
        // Runtime integrity enforcement (the active half of the Shield). A class whose bytes no longer match
        // its ship-time integrity manifest was patched after protection — a cracked jar or injected backdoor.
        // With enforcement on (default), such a mod is REFUSED; set -Daetherium.shield.enforce=false for a
        // report-only launch. Mods without a manifest (unsigned) are unaffected.
        final boolean enforce = !"false".equalsIgnoreCase(System.getProperty("aetherium.shield.enforce", "true"));
        final ClassLoader cl = getClass().getClassLoader();
        final org.aetherium.shield.IntegrityManifest manifest = org.aetherium.shield.ModVerifier.loadManifest(cl);

        int initialized = 0;
        for (AetheriumMod mod : ServiceLoader.load(AetheriumMod.class, cl)) {
            try {
                if (org.aetherium.shield.ModVerifier.verifyClass(cl, manifest, mod.getClass().getName())
                        == org.aetherium.shield.ModVerifier.Verdict.TAMPERED) {
                    if (enforce) {
                        LOG.error("Aetherium REFUSES tampered mod '{}' — its bytes do not match the Shield "
                                + "integrity manifest. Set -Daetherium.shield.enforce=false to override.", mod.id());
                        continue;
                    }
                    LOG.warn("Aetherium mod '{}' is TAMPERED (integrity mismatch); continuing (enforce off).",
                            mod.id());
                }
                LOG.info("Initializing Aetherium mod: {}", mod.id());
                mod.onInitialize(context);
                initialized++;
            } catch (Throwable modFailure) {
                // One bad mod must never crash the launch.
                LOG.warn("Aetherium mod '{}' failed to initialize; skipping ({}).",
                        mod.id(), modFailure.toString());
            }
        }
        LOG.info("Aetherium initialized {} mod(s) on compute tier {}.", initialized, tier);
    }

    /** Loader-supplied {@link AetheriumContext} backed by the NeoForge-provided SLF4J logger. */
    private record LoggingContext(Logger logger, CapabilityTier tier) implements AetheriumContext {
        @Override
        public void log(String message) {
            logger.info("[mod] {}", message);
        }

        @Override
        public CapabilityTier computeTier() {
            return tier;
        }
    }
}
