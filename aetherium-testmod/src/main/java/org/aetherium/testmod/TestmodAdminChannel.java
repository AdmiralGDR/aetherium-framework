/*
 * Aetherium Framework — test mod: a serverbound (client → server) admin channel.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.network.NetworkPayload;
import org.aetherium.network.PayloadCodec;
import org.aetherium.network.PayloadSink;
import org.aetherium.network.PayloadSource;

/**
 * A minimal serverbound payload + codec — the exact shape a mod's in-game admin screen uses to push a setting
 * to a dedicated server (). Pure: no {@code net.minecraft}/{@code net.neoforged} import.
 *
 * <p>EN: {@code SetMaxMembers(value)} carries one int over the loader-agnostic {@link PayloadSink}/
 * {@link PayloadSource}. The loader bridges the channel to NeoForge's {@code playToServer}, and hands the
 * server handler the sender's {@code PlayerHandle} so it can gate the change on permission.
 *
 * <p>RU: {@code SetMaxMembers(value)} — один int по загрузчик-агностичным SPK. Загрузчик мостит канал к
 * {@code playToServer} и передаёт обработчику {@code PlayerHandle} отправителя для проверки прав.
 */
public final class TestmodAdminChannel {

    /** The serverbound channel id (namespaced, per the duplicate-channel discipline). */
    public static final String CHANNEL = "aetherium_testmod:admin";

    private TestmodAdminChannel() {
    }

    /** A client → server admin request: set the faction's max members. */
    public record SetMaxMembers(int value) implements NetworkPayload {
        @Override
        public String channelId() {
            return CHANNEL;
        }
    }

    /** The codec over the pure sink/source SPI. */
    public static final class Codec implements PayloadCodec<SetMaxMembers> {
        @Override
        public String channelId() {
            return CHANNEL;
        }

        @Override
        public void encode(SetMaxMembers payload, PayloadSink sink) {
            sink.writeInt(payload.value());
        }

        @Override
        public SetMaxMembers decode(PayloadSource source) {
            return new SetMaxMembers(source.readInt());
        }
    }
}
