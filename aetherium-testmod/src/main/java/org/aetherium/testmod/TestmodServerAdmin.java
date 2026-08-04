/*
 * Aetherium Framework — test mod: the serverbound admin entrypoint (+ side model).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.core.mod.AetheriumContext;
import org.aetherium.core.mod.AetheriumMod;
import org.aetherium.core.mod.Side;
import org.aetherium.edge.Network;

/**
 * A second, deliberately <strong>preview-free</strong> Aetherium mod entrypoint that registers the serverbound
 * admin channel and reports its physical side — the live proof of + the side model.
 *
 * <p>EN: {@link HelloAetheriumMod} touches off-heap FFM types at class-init, so on a launcher without
 * {@code --enable-preview} it can't be constructed and the loader (correctly) skips it. This class references
 * <em>only</em> non-preview API ({@link Network}, {@link Side}, {@link TestmodAdminChannel}), so it loads and
 * runs in any launch — proving that a real mod can register a client→server channel and be told its side in the
 * running game, not just offline.
 *
 * <p>RU: {@link HelloAetheriumMod} трогает off-heap FFM-типы при инициализации класса, поэтому на лаунчере без
 * {@code --enable-preview} он не конструируется и загрузчик его (верно) пропускает. Этот класс ссылается лишь
 * на не-preview API, поэтому грузится и выполняется в любом запуске — доказывая, что реальный мод регистрирует
 * серверный канал и узнаёт свою сторону в живой игре, а не только офлайн.
 */
public final class TestmodServerAdmin implements AetheriumMod {

    @Override
    public String id() {
        return "aetherium_testmod_admin";
    }

    @Override
    public void onInitialize(AetheriumContext context) {
        // Serverbound (client → server): the channel a settings screen pushes an admin edit through. The loader
        // wires it to NeoForge's playToServer and hands the handler the sender's PlayerHandle (from the
        // connection, unspoofable), gated on permission; a flood or oversized packet is dropped by the framework
        // before this handler runs.
        Network.registerServerbound(new TestmodAdminChannel.Codec(), (sender, payload) -> {
            if (sender.hasPermission(2)) {
                context.log("admin: " + sender.name() + " set maxMembers=" + payload.value());
            } else {
                context.log("admin: rejected " + sender.name() + " (needs op level 2)");
            }
        });
        context.log("registered serverbound admin channel " + TestmodAdminChannel.CHANNEL);

        // Side model: the loader supplies this JVM's physical side. A client boot logs CLIENT; a dedicated
        // server logs SERVER. This is the runtime the generated @AetheriumInit(side=…) dispatch reads.
        context.log("side = " + context.side() + " (client code runsOnSide(CLIENT)="
                + context.runsOnSide(Side.CLIENT) + ", server code runsOnSide(SERVER)="
                + context.runsOnSide(Side.SERVER) + ")");
    }
}
