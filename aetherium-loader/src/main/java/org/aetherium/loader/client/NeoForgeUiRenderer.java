/*
 * Aetherium Framework — GuiGraphics-backed UiRenderer (client).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.aetherium.ui.UiRenderer;

/**
 * The real loader-side {@link UiRenderer} — draws an Aetherium screen through Minecraft's {@code GuiGraphics}.
 * This is the adapter asked for: the pure {@code aetherium-ui} layout engine finally reaches a
 * player.
 *
 * <p>EN: The whole SPI is four calls. {@code fillRect(x,y,w,h,argb)} → {@code GuiGraphics.fill(x,y,x+w,y+h,argb)}
 * (Minecraft's {@code fill} takes corners, not width/height); {@code drawText} → {@code drawString(font,…)};
 * {@code pushClip/popClip} → {@code enableScissor/disableScissor} (Minecraft maintains the scissor stack).
 * Bound to one {@link GuiGraphics} per frame.
 * RU: Весь SPI — четыре вызова. {@code fillRect} → {@code fill} (углы, не ш×в), {@code drawText} →
 * {@code drawString}, {@code pushClip/popClip} → {@code enableScissor/disableScissor}.
 */
final class NeoForgeUiRenderer implements UiRenderer {

    private final GuiGraphics graphics;
    private final Font font;

    NeoForgeUiRenderer(GuiGraphics graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    @Override
    public void fillRect(int x, int y, int width, int height, int argb) {
        graphics.fill(x, y, x + width, y + height, argb);
    }

    @Override
    public void drawText(int x, int y, String text, int argb) {
        graphics.drawString(font, text, x, y, argb);
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        graphics.enableScissor(x, y, x + width, y + height);
    }

    @Override
    public void popClip() {
        graphics.disableScissor();
    }
}
