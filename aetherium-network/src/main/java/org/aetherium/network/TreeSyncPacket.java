/*
 * Aetherium Framework — hierarchical sync packet (alongside the flat StructArena delta).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import java.util.Objects;

/**
 * A {@link NetworkPayload} carrying a hierarchical {@link TreeNode} (faction data, skill trees, quests) on
 * a <strong>mod-supplied, namespaced channel</strong>.
 *
 * <p>EN: The irregular-data counterpart to {@code StructArenaDeltaPacket}. The channel id is a
 * <em>constructor parameter</em>, not a shared constant — each mod passes its own {@code "mymod:state"} so
 * two mods can never cross-talk (see {@link Channels}). Encoded by {@link TreeSyncCodec}. <em>Breaking
 * change:</em> the old {@code TreeSyncPacket(TreeNode)} + process-wide {@code CHANNEL} constant are gone.
 *
 * <p>RU: Аналог для нерегулярных данных к {@code StructArenaDeltaPacket}. Идентификатор канала — параметр
 * конструктора, а не общая константа: каждый мод передаёт свой {@code "mymod:state"}, поэтому два мода не
 * могут пересечься (см. {@link Channels}). Кодируется {@link TreeSyncCodec}. <em>Ломающее изменение:</em>
 * старые {@code TreeSyncPacket(TreeNode)} и глобальная константа {@code CHANNEL} удалены.
 */
public record TreeSyncPacket(String channelId, TreeNode root) implements NetworkPayload {

    public TreeSyncPacket {
        Channels.validate(channelId);
        Objects.requireNonNull(root, "root");
    }

    @Override
    public String channelId() {
        return channelId;
    }
}
