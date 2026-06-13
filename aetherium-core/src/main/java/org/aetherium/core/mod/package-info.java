/*
 * Aetherium Framework — loader-agnostic mod SPI.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * The loader-agnostic mod entrypoint SPI.
 *
 * <p><b>EN.</b> {@link org.aetherium.core.mod.AetheriumMod} + {@link org.aetherium.core.mod.AetheriumContext}
 * are the entire contract a mod targets. They live in {@code aetherium-core} (the pure leaf) and
 * reference no Minecraft/NeoForge types, so a mod compiled against them runs on any loader the
 * Aetherium loader supports.
 *
 * <p><b>RU.</b> {@link org.aetherium.core.mod.AetheriumMod} + {@link org.aetherium.core.mod.AetheriumContext}
 * — весь контракт, на который ориентируется мод. Они находятся в {@code aetherium-core} (чистый
 * лист) и не ссылаются на типы Minecraft/NeoForge, поэтому мод, скомпилированный под них, работает
 * на любом загрузчике, поддерживаемом загрузчиком Aetherium.
 */
package org.aetherium.core.mod;
