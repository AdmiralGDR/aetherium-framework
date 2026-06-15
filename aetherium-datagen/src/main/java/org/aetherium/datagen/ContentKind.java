/*
 * Aetherium Framework — content kind.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

/**
 * The kind of registrable content an Aetherium annotation describes.
 *
 * <p>EN: Pure enum (no Minecraft types) shared by the DataGen engine, the annotation processor, and
 * the loader bridge. {@link #BLOCK} additionally implies a {@code BlockItem} (handled by the loader).
 *
 * <p>RU: Чистое перечисление (без типов Minecraft), общее для движка DataGen, аннотационного
 * процессора и моста загрузчика. {@link #BLOCK} дополнительно подразумевает {@code BlockItem}
 * (обрабатывается загрузчиком).
 */
public enum ContentKind {
    BLOCK,
    ITEM
}
