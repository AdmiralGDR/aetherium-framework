/*
 * Aetherium Framework — Screen wrapping an AetheriumScreen (client).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.aetherium.ui.AetheriumScreen;
import org.aetherium.ui.LaidOut;
import org.aetherium.ui.Rect;
import org.aetherium.ui.UiRuntime;
import org.aetherium.ui.Widget;

/**
 * The real Minecraft {@code Screen} that hosts a declarative {@link AetheriumScreen} — the other half of the
 * adapter. Every frame it rebuilds the widget tree, lays it out to the window with font-accurate
 * metrics, and paints it through {@link NeoForgeUiRenderer}; every input event is forwarded to the pure
 * {@link UiRuntime}.
 *
 * <p>EN: Rebuilding {@code screen.build()} per frame is the intended pattern (the mod holds mutable widgets
 * like {@code TextField}/{@code ScrollPanel} across frames). The laid-out tree from the last render is used
 * for hit-testing clicks, keys and scroll between frames. {@code onKey} gets first refusal on a key so a
 * screen can implement shortcuts.
 * RU: Перестроение {@code screen.build()} каждый кадр — задуманный паттерн (мод держит изменяемые виджеты
 * между кадрами). Дерево из последнего кадра используется для hit-test между кадрами.
 */
public final class AetheriumScreenAdapter extends Screen {

    private final AetheriumScreen screen;
    private LaidOut laidOut;

    public AetheriumScreenAdapter(AetheriumScreen screen) {
        super(Component.literal(screen.title()));
        this.screen = screen;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        NeoForgeUiRenderer renderer = new NeoForgeUiRenderer(graphics, this.font);
        NeoForgeUiMetrics metrics = new NeoForgeUiMetrics(this.font);
        Widget<?> root = screen.build();
        this.laidOut = UiRuntime.render(root, new Rect(0, 0, this.width, this.height), metrics, renderer);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (laidOut != null && UiRuntime.click(laidOut, (int) mouseX, (int) mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (screen.onKey(keyCode, modifiers)) {
            return true;
        }
        if (laidOut != null && UiRuntime.keyPressed(laidOut, keyCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (laidOut != null && UiRuntime.charTyped(laidOut, codePoint)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Minecraft: positive scrollY = wheel up; our scroll delta is positive = down, so negate.
        if (laidOut != null && UiRuntime.scroll(laidOut, (int) mouseX, (int) mouseY, (int) -scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        screen.onClose();
        super.onClose();
    }
}
