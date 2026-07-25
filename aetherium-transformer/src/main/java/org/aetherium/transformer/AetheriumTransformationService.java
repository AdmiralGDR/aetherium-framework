/*
 * Aetherium Framework — ModLauncher transformation service.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.transformer;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * The ModLauncher {@link ITransformationService} entry — discovered before the game starts.
 *
 * <p>EN: ModLauncher loads this via {@code META-INF/services/cpw.mods.modlauncher.api.ITransformationService}
 * during its earliest bootstrap (the JVM class-loading phase), well before mods construct. It is our
 * registered presence in the launch pipeline. The <em>actual</em> class transformation is performed
 * by the companion {@link AetheriumLaunchPlugin} ({@code ILaunchPluginService}): ModLauncher's
 * {@link ITransformer} matches <strong>exact</strong> class names only, so it is the wrong tool for
 * "transform any class in an Aetherium-mod namespace". The launch-plugin's {@code handlesClass} is
 * the correct per-class filter hook (the same split Mixin uses). Hence {@link #transformers()} is
 * intentionally empty.
 *
 * <p>b/c: this is the FIRST Aetherium code to run, and it is preview-free (it loads on any
 * JVM). {@link #initialize} therefore doubles as the early gate that tells the player, in plain
 * language, when {@code --enable-preview} is missing — instead of leaving them to decode an
 * {@code UnsupportedClassVersionError} deep in a later stack trace (see {@link PreviewSupport}).
 *
 * <p>RU: ModLauncher загружает это через сервис-файл на самом раннем этапе bootstrap, задолго до
 * конструирования модов. Фактическую трансформацию выполняет компаньон {@link AetheriumLaunchPlugin}.
 * b/c: это ПЕРВЫЙ выполняемый код Aetherium, и он без preview (грузится на любой JVM), поэтому
 * {@link #initialize} заодно понятным языком сообщает об отсутствии {@code --enable-preview}, а не
 * оставляет игрока разбирать {@code UnsupportedClassVersionError} в позднем стеке (см. {@link PreviewSupport}).
 */
public final class AetheriumTransformationService implements ITransformationService {

    private static final Logger LOG = LoggerFactory.getLogger("Aetherium/ModLauncher");

    @Override
    public String name() {
        return "aetherium";
    }

    @Override
    public void initialize(IEnvironment environment) {
        LOG.info("Aetherium transformation service initialized; class interception via launch plugin "
                + "(allow-list: {}).", AetheriumNamespaces.allowList());
        // c: warn once, early and clearly, if the JVM lacks --enable-preview. The framework
        // still loads (this module and the registration path are preview-free); only the FFM-backed
        // performance features degrade, so this is a WARN, not a hard failure.
        if (!PreviewSupport.enabled()) {
            LOG.warn(PreviewSupport.advisoryEnglish());
            LOG.warn(PreviewSupport.advisoryRussian());
        }
    }

    @Override
    public void onLoad(IEnvironment environment, Set<String> otherServices) {
        // Nothing to validate; the launch plugin is discovered independently and is self-contained.
    }

    @Override
    public List<? extends ITransformer<?>> transformers() {
        // Broad, namespace-filtered interception is done by AetheriumLaunchPlugin (ILaunchPluginService),
        // which is the correct ModLauncher hook for transforming arbitrary mod classes by namespace.
        return List.of();
    }
}
