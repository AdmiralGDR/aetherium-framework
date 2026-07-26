/*
 * Aetherium Framework — Fabric mod entrypoint (WS-5).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fabric;

import net.fabricmc.api.ModInitializer;

/**
 * The Fabric entrypoint — the loader-specific shell, declared in {@code fabric.mod.json}.
 *
 * <p>EN: This is the entire Fabric-specific surface: a {@code ModInitializer} whose {@code onInitialize}
 * hands off to the loader-neutral {@link FabricBoot}. The framework's actual work — the dispatch table, the
 * {@code AetheriumMod} SPI, Shield enforcement — is identical to NeoForge and lives in shared modules. That
 * this class is a few lines is the proof that the Platform Abstraction Layer is genuinely loader-agnostic:
 * porting to a new loader is writing a new shell, not a new framework.
 * RU: Это вся Fabric-специфичная поверхность: {@code ModInitializer}, чей {@code onInitialize} передаёт
 * управление loader-нейтральному {@link FabricBoot}. Настоящая работа фреймворка идентична NeoForge и лежит
 * в общих модулях. То, что этот класс — несколько строк, и есть доказательство, что PAL действительно
 * loader-агностичен: порт на новый загрузчик — это новая оболочка, а не новый фреймворк.
 */
public final class AetheriumFabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricBoot.boot();
    }
}
