/*
 * Aetherium Framework — codec for the hierarchical tree-sync packet.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

/**
 * {@link PayloadCodec} for {@link TreeSyncPacket} — delegates the whole payload to {@link TreeCodec}.
 */
public final class TreeSyncCodec implements PayloadCodec<TreeSyncPacket> {

    @Override
    public String channelId() {
        return TreeSyncPacket.CHANNEL;
    }

    @Override
    public void encode(TreeSyncPacket payload, PayloadSink sink) {
        TreeCodec.encode(payload.root(), sink);
    }

    @Override
    public TreeSyncPacket decode(PayloadSource source) {
        return new TreeSyncPacket(TreeCodec.decode(source));
    }
}
