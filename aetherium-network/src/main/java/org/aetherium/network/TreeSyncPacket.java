/*
 * Aetherium Framework — hierarchical sync packet (alongside the flat StructArena delta).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import java.util.Objects;

/**
 * A {@link NetworkPayload} carrying a hierarchical {@link TreeNode} (faction data, skill trees, quests).
 *
 * <p>EN: The irregular-data counterpart to {@code StructArenaDeltaPacket}; both share the same buffer SPI
 * and the loader registers each {@link #channelId()} the same way. Encoded by {@link TreeSyncCodec}.
 * RU: Аналог для нерегулярных данных к {@code StructArenaDeltaPacket}; оба используют один SPI буфера, и
 * загрузчик регистрирует каждый {@link #channelId()} одинаково. Кодируется {@link TreeSyncCodec}.
 */
public record TreeSyncPacket(TreeNode root) implements NetworkPayload {

    public static final String CHANNEL = "aetherium:tree_sync";

    public TreeSyncPacket {
        Objects.requireNonNull(root, "root");
    }

    @Override
    public String channelId() {
        return CHANNEL;
    }
}
