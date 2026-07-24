/*
 * Aetherium Framework — codec for the hierarchical tree-sync packet.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

/**
 * {@link PayloadCodec} for {@link TreeSyncPacket} — delegates the whole payload to {@link TreeCodec}.
 *
 * <p>EN: Bound to a mod-supplied namespaced channel passed at construction ({@code new
 * TreeSyncCodec("mymod:state")}), so each mod's codec is distinct and {@link NetworkRegistry} can reject a
 * duplicate. The byte format is unchanged (the depth/size-hardened {@link TreeCodec}); only the channel is
 * now per-mod.
 * RU: Привязан к пространственно-именованному каналу мода, передаваемому в конструктор, поэтому кодек
 * каждого мода уникален, а {@link NetworkRegistry} отклоняет дубликат. Формат байтов не изменён.
 */
public final class TreeSyncCodec implements PayloadCodec<TreeSyncPacket> {

    private final String channelId;

    public TreeSyncCodec(String channelId) {
        this.channelId = Channels.validate(channelId);
    }

    @Override
    public String channelId() {
        return channelId;
    }

    @Override
    public void encode(TreeSyncPacket payload, PayloadSink sink) {
        TreeCodec.encode(payload.root(), sink);
    }

    @Override
    public TreeSyncPacket decode(PayloadSource source) {
        return new TreeSyncPacket(channelId, TreeCodec.decode(source));
    }
}
