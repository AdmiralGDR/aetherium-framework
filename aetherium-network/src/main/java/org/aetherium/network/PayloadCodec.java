/*
 * Aetherium Framework — network payload codec.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

/**
 * Encoder/decoder for a {@link NetworkPayload}, written against the loader-agnostic
 * {@link PayloadSink}/{@link PayloadSource}. The loader adapts these to a platform {@code StreamCodec}.
 */
public interface PayloadCodec<T extends NetworkPayload> {

    String channelId();

    void encode(T payload, PayloadSink sink);

    T decode(PayloadSource source);
}
