/*
 * Aetherium Framework — datagen package overview.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * The autonomous, build-time asset generator (the "DataGen Engine").
 *
 * <p>EN: Strictly pure — no {@code net.minecraft}/{@code net.neoforged} types and no external JSON
 * library. {@link org.aetherium.datagen.ContentEntry} describes a piece of content;
 * {@link org.aetherium.datagen.AssetGenerator} turns entries into resource-pack/data-pack JSON; and
 * {@link org.aetherium.datagen.ContentIndex} is the line-oriented hand-off the annotation processor
 * writes and the loader reads. Runs entirely outside the game.
 *
 * <p>RU: Строго чистый пакет — без типов {@code net.minecraft}/{@code net.neoforged} и без внешних
 * JSON-библиотек. {@link org.aetherium.datagen.ContentEntry} описывает контент;
 * {@link org.aetherium.datagen.AssetGenerator} превращает записи в JSON ресурс-/дата-пака;
 * {@link org.aetherium.datagen.ContentIndex} — построчная передача от процессора к загрузчику.
 * Работает целиком вне игры.
 */
package org.aetherium.datagen;
