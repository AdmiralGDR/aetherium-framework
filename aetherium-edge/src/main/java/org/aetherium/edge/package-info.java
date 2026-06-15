/*
 * Aetherium Framework — Platform Abstraction Layer (PAL).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * The Platform Abstraction Layer (PAL) — the "edge" between Aetherium and vanilla Minecraft.
 *
 * <p><b>EN.</b> Strictly abstract, loader-agnostic. {@link org.aetherium.edge.Platform#bridge()}
 * returns the active {@link org.aetherium.edge.PlatformBridge} ({@link org.aetherium.edge.EntityAccess}
 * + {@link org.aetherium.edge.EdgeEvents}), letting mods read/write vanilla entities and hook events
 * without importing any NeoForge/Fabric/Minecraft type. {@code aetherium-loader} provides the NeoForge
 * implementation; outside a game a safe no-op bridge is used.
 *
 * <p><b>RU.</b> Строго абстрактный, независимый от загрузчика. {@link org.aetherium.edge.Platform#bridge()}
 * возвращает активный {@link org.aetherium.edge.PlatformBridge}, позволяя модам читать/писать
 * ванильные сущности и подписываться на события без импорта типов NeoForge/Fabric/Minecraft.
 * {@code aetherium-loader} предоставляет реализацию NeoForge; вне игры используется безопасный no-op.
 */
package org.aetherium.edge;
