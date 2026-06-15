/*
 * Aetherium Framework — test mod.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructField;
import org.aetherium.core.compute.StructLayout;
import org.aetherium.core.mod.AetheriumContext;
import org.aetherium.core.mod.AetheriumMod;
import org.aetherium.gfx.RenderRegistry;
import org.aetherium.network.NetworkRegistry;
import org.aetherium.network.StructArenaSyncCodec;
import org.aetherium.network.StructArenaSyncPacket;

/**
 * End-to-end Aetherium test mod — exercises compute + network + gfx with <strong>no</strong> NeoForge
 * or Minecraft import.
 *
 * <p>EN: On init it (1) computes a dummy entity position into an off-heap {@link StructArena},
 * (2) registers a zero-GC {@link StructArenaSyncPacket} channel that the loader bridges to the
 * platform's packet system (decoding straight into a pre-allocated client mirror arena), and
 * (3) registers an {@link org.aetherium.gfx.AetheriumEntityRenderer} that draws the entity as a
 * cuboid via the loader-agnostic render context. The loader wires all three to NeoForge; this class
 * stays pure and portable.
 *
 * <p>RU: При инициализации мод (1) вычисляет позицию сущности в off-heap {@link StructArena},
 * (2) регистрирует zero-GC канал {@link StructArenaSyncPacket}, который загрузчик мостит к сетевой
 * системе платформы (декодируя прямо в заранее выделенную клиентскую арену-зеркало), и
 * (3) регистрирует рендер, рисующий сущность кубоидом через загрузчик-агностичный контекст. Всё
 * связывание с NeoForge — в загрузчике; этот класс остаётся чистым и переносимым.
 */
public final class HelloAetheriumMod implements AetheriumMod {

    private static final StructLayout ENTITY = StructLayout.builder()
            .doubles("x").doubles("y").doubles("z").build();
    private static final StructField X = ENTITY.field("x");
    private static final StructField Y = ENTITY.field("y");
    private static final StructField Z = ENTITY.field("z");

    @Override
    public String id() {
        return "aetherium_testmod";
    }

    @Override
    public void onInitialize(AetheriumContext context) {
        // (1) Off-heap compute: a dummy entity position lives in contiguous FFM memory (zero GC).
        StructArena serverArena = StructArena.allocate(ENTITY, 1);
        serverArena.setDouble(0, X, 100.5);
        serverArena.setDouble(0, Y, 64.0);
        serverArena.setDouble(0, Z, -200.25);
        StructArenaSyncPacket packet = new StructArenaSyncPacket(serverArena, 1);
        context.log("computed off-heap entity pos; sync payload = " + packet.payloadBytes() + " bytes");

        // (2) Network: a pre-allocated client mirror receives synced rows with no per-packet allocation.
        StructArena clientMirror = StructArena.allocate(ENTITY, 1);
        NetworkRegistry.register(new StructArenaSyncCodec(clientMirror),
                received -> context.log("client mirror synced rows=" + received.rowCount()
                        + " x=" + received.arena().getDouble(0, X)));

        // (3) GFX: draw the entity as a glowing cuboid via the loader-agnostic render context.
        RenderRegistry.register("minecraft:armor_stand", (ctx, partialTick) -> {
            ctx.pushPose();
            ctx.setColor(0.2f, 0.8f, 1.0f, 1.0f);
            ctx.drawCuboid(0.6, 1.8, 0.6);
            ctx.popPose();
        });

        context.log(id() + " wired: off-heap compute + network (" + NetworkRegistry.size()
                + " channel) + gfx (" + RenderRegistry.size() + " renderer) on tier " + context.computeTier());
    }
}
