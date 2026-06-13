/*
 * Aetherium Framework — mod entrypoint SPI.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.mod;

/**
 * The loader-agnostic entrypoint a mod implements to run on Aetherium.
 *
 * <p>EN: A mod implements this and registers it via {@code java.util.ServiceLoader} (a
 * {@code META-INF/services/org.aetherium.core.mod.AetheriumMod} entry). The Aetherium loader
 * discovers every implementation and calls {@link #onInitialize} during the loader's construction
 * phase — handing over an {@link AetheriumContext} that contains <strong>no NeoForge/Minecraft
 * types</strong>. This is the "compile once, run on any loader" contract: the mod never references
 * {@code @Mod}, the mod bus, or any loader API.
 *
 * <p>RU: Мод реализует это и регистрирует через {@code java.util.ServiceLoader} (запись
 * {@code META-INF/services/org.aetherium.core.mod.AetheriumMod}). Загрузчик Aetherium находит все
 * реализации и вызывает {@link #onInitialize} на фазе конструирования загрузчика, передавая
 * {@link AetheriumContext}, не содержащий <strong>типов NeoForge/Minecraft</strong>. Это контракт
 * «скомпилируй один раз — запускай на любом загрузчике»: мод никогда не ссылается на {@code @Mod},
 * шину модов или любой API загрузчика.
 */
public interface AetheriumMod {

    /** Called once by the loader during initialization. Must not throw for normal flow. */
    void onInitialize(AetheriumContext context);

    /** Stable mod identifier for logging/diagnostics; defaults to the implementing class name. */
    default String id() {
        return getClass().getSimpleName();
    }
}
