/*
 * Aetherium Framework — paints registered HUD overlays over the game each frame (client, follow-up).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.aetherium.ui.AetheriumHud;
import org.aetherium.ui.Rect;
import org.aetherium.ui.UiRuntime;
import org.aetherium.ui.Widget;

/**
 * Draws every registered {@link AetheriumHud} on top of the running game — the loader half of
 * {@code AetheriumUi.addHud} (follow-up).
 *
 * <p>EN: Registered on the NeoForge game bus at boot (client dist only), it runs on {@code RenderGuiEvent.Post}
 * — after vanilla's HUD — and paints each visible HUD through the <em>same</em> {@link NeoForgeUiRenderer} +
 * {@link UiRuntime} the screens use, over the live {@code GuiGraphics}, with no scrim and no input capture. Each
 * HUD is rendered inside a try/catch so a single misbehaving overlay can never break the frame (graceful
 * degradation). No HUD is registered off-client, so the list is empty on a dedicated server and this is a no-op.
 * RU: Регистрируется на игровой шине NeoForge при старте (только клиент), выполняется на {@code RenderGuiEvent.Post}
 * (после ванильного HUD) и рисует каждый видимый HUD тем же {@link NeoForgeUiRenderer} + {@link UiRuntime}, что и
 * экраны, поверх живого {@code GuiGraphics}, без scrim и без перехвата ввода. Каждый HUD — в try/catch, поэтому
 * один сбойный оверлей не ломает кадр.
 */
public final class ClientHudRenderer {

    public void onRenderGui(RenderGuiEvent.Post event) {
        var huds = NeoForgeUiAccess.huds();
        if (huds.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.font == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Rect viewport = new Rect(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        NeoForgeUiRenderer renderer = new NeoForgeUiRenderer(graphics, mc.font);
        NeoForgeUiMetrics metrics = new NeoForgeUiMetrics(mc.font);
        for (AetheriumHud hud : huds) {
            try {
                if (!hud.visible()) {
                    continue;
                }
                Widget<?> root = hud.build(viewport);
                UiRuntime.render(root, viewport, metrics, renderer);
            } catch (Throwable oneBadHud) {
                // A misbehaving overlay must never break the render loop or take the game down.
            }
        }
    }
}
