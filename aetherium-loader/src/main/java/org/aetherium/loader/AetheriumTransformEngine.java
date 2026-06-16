/*
 * Aetherium Framework — loader-side engine holder.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import org.aetherium.bytecode.BytecodeEngine;
import org.aetherium.bytecode.DiagnosticSink;
import org.aetherium.bytecode.transform.DispatchLoweringTransformer;
import org.aetherium.core.Diagnostic;
import org.aetherium.core.SymbolManifest;
import org.aetherium.injector.AetheriumInjector;
import org.aetherium.injector.InjectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * Owns the single, pure {@link BytecodeEngine} the loader delegates to — the bridge between
 * ModLauncher and {@code aetherium-bytecode}.
 *
 * <p>EN: This class (and the launch plugin) speak ModLauncher; the engine does not. The engine is
 * built once with the framework {@link #MANIFEST} and a {@link DispatchLoweringTransformer} that
 * lowers static calls to the Aetherium API facade into {@code invokedynamic}. {@link #transform}
 * delegates to the engine, which already retains the original bytes and returns them on any failure
 * — so the bytes ModLauncher gets back are always valid. Diagnostics are logged here.
 *
 * <p>RU: Этот класс (и launch-plugin) говорят на ModLauncher; движок — нет. Движок строится один раз
 * с {@link #MANIFEST} фреймворка и {@link DispatchLoweringTransformer}, понижающим статические вызовы
 * фасада API Aetherium в {@code invokedynamic}. {@link #transform} делегирует движку, который уже
 * сохраняет исходные байты и возвращает их при любом сбое — поэтому байты, получаемые ModLauncher,
 * всегда валидны. Диагностика логируется здесь.
 */
final class AetheriumTransformEngine {

    private static final Logger LOG = LoggerFactory.getLogger("Aetherium/Transform");

    /** JVM internal name of the abstract API facade whose static calls get lowered. */
    static final String API_OWNER_INTERNAL = "org/aetherium/api/AetheriumApi";

    /** Manifest namespace for API symbols. */
    static final String API_NAMESPACE = "compute";

    /**
     * The framework symbol manifest — the single source of truth shared by the dispatch-lowering
     * transformer and the {@link DispatchBootstrap dispatch table}, so IDs always agree.
     */
    static final SymbolManifest MANIFEST = SymbolManifest.builder()
            .add(API_NAMESPACE, "doubler", "(I)I")
            .build();

    private final BytecodeEngine engine;
    private final DiagnosticSink sink;
    private final AetheriumInjector injector;

    private AetheriumTransformEngine() {
        this.sink = new LoggingDiagnosticSink(LOG);

        // Aggregate every mod's programmatic injections (the "Mixin killer") via the loader-agnostic
        // SPI, then bind the hook dispatch table once so injected invokedynamic sites can link.
        this.injector = discoverInjections();
        int installedHooks = injector.installHooks();
        if (!injector.rules().isEmpty()) {
            LOG.info("Aetherium injector: {} rule(s), {} hook(s) installed.",
                    injector.rules().size(), installedHooks);
        }

        this.engine = BytecodeEngine.builder()
                .manifest(MANIFEST)
                .transformer(new DispatchLoweringTransformer(API_OWNER_INTERNAL, API_NAMESPACE, MANIFEST, 100))
                // Injection runs after API lowering; it executes inside the engine's verification
                // sandbox, so a bad injection reverts to the original bytes (never crashes the JVM).
                .transformer(injector.toTransformer(200))
                .classLoader(AetheriumTransformEngine.class.getClassLoader())
                .build();
    }

    static AetheriumTransformEngine create() {
        return new AetheriumTransformEngine();
    }

    /** Transform class bytes; never throws — returns the original bytes on any failure. */
    byte[] transform(byte[] original) {
        return engine.transformClass(original, sink);
    }

    /** Whether a programmatic injection rule targets the given class (lets vanilla targets through). */
    boolean hasInjectionFor(String internalName) {
        return injector.hasRuleFor(internalName);
    }

    /** Discover mod-supplied {@link InjectionProvider}s and let each populate a shared injector. */
    private static AetheriumInjector discoverInjections() {
        AetheriumInjector injector = AetheriumInjector.create();
        try {
            ServiceLoader<InjectionProvider> providers =
                    ServiceLoader.load(InjectionProvider.class, AetheriumTransformEngine.class.getClassLoader());
            for (InjectionProvider provider : providers) {
                try {
                    provider.configure(injector);
                } catch (Throwable bad) {
                    // One bad provider must never abort the launch.
                    LOG.warn("Aetherium injection provider {} failed; skipping ({}).",
                            provider.getClass().getName(), bad.toString());
                }
            }
        } catch (Throwable discovery) {
            LOG.warn("Aetherium injection discovery failed; continuing with none ({}).", discovery.toString());
        }
        return injector;
    }

    /** A {@link DiagnosticSink} that routes engine diagnostics to the SLF4J log. */
    private record LoggingDiagnosticSink(Logger log) implements DiagnosticSink {
        @Override
        public void accept(Diagnostic diagnostic) {
            String line = "[" + diagnostic.code() + "] " + diagnostic.message();
            switch (diagnostic.severity()) {
                case ERROR -> log.error(line);
                case WARN -> log.warn(line);
                case INFO -> log.info(line);
            }
        }
    }
}
