/*
 * Aetherium Framework — declarative HUD overlay (loader-agnostic, follow-up).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * A persistent overlay painted over the game every frame — the "draw a HUD element outside a screen" capability
 * the feedback flagged as the natural next feature.
 *
 * <p>EN: Unlike an {@link AetheriumScreen} (which pauses input, dims the world, and is opened/closed), a HUD is
 * a lightweight, always-on widget tree drawn on top of the running game. A mod builds one and registers it with
 * {@link AetheriumUi#addHud}; the loader paints it each frame through the same {@link UiRuntime} + renderer the
 * screens use, with <strong>no scrim and no input capture</strong>. Position it by laying out within the
 * viewport (e.g. a corner-anchored {@code Container}). {@link #visible()} lets a mod toggle it without
 * unregistering. Fully loader-agnostic and testable headless — no Minecraft type crosses the boundary.
 * RU: В отличие от {@link AetheriumScreen} (пауза ввода, затемнение мира, открытие/закрытие), HUD — лёгкое
 * всегда-включённое дерево виджетов поверх идущей игры. Мод строит его и регистрирует через
 * {@link AetheriumUi#addHud}; загрузчик рисует его каждый кадр тем же {@link UiRuntime} и рендерером, что и
 * экраны, <strong>без scrim и без перехвата ввода</strong>. {@link #visible()} позволяет скрыть без снятия
 * регистрации. Полностью независим от загрузчика и тестируем headless.
 */
public abstract class AetheriumHud {

    /**
     * Build the overlay's widget tree for the current window {@code viewport}. Called every frame, so keep it
     * cheap; hold mutable widgets across frames as fields, exactly like {@link AetheriumScreen#build(Rect)}.
     */
    public abstract Widget<?> build(Rect viewport);

    /** Whether the overlay is drawn this frame. Override to toggle a HUD without unregistering it. */
    public boolean visible() {
        return true;
    }
}
