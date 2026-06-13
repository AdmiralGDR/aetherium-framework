/*
 * Aetherium Framework — ModLauncher transformation service.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

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
 * <p>RU: ModLauncher загружает это через {@code META-INF/services/cpw.mods.modlauncher.api.ITransformationService}
 * на самом раннем этапе bootstrap (фаза загрузки классов JVM), задолго до конструирования модов.
 * Это наше зарегистрированное присутствие в конвейере запуска. <em>Фактическую</em> трансформацию
 * классов выполняет компаньон {@link AetheriumLaunchPlugin} ({@code ILaunchPluginService}):
 * {@link ITransformer} ModLauncher сопоставляет только <strong>точные</strong> имена классов,
 * поэтому он не подходит для «преобразовать любой класс в пространстве имён мода Aetherium».
 * {@code handlesClass} launch-plugin — правильный per-class фильтр (то же разделение использует
 * Mixin). Поэтому {@link #transformers()} намеренно пуст.
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
