/*
 * Aetherium Framework — UI navigation SPI (open a screen in game).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * The loader-agnostic entry point that actually shows an {@link AetheriumScreen} to the player — the missing
 * navigation surface the feedback () called out.
 *
 * <p>EN: A mod builds a declarative {@link AetheriumScreen}; {@code AetheriumUi.open(screen)} asks the active
 * platform to display it. The loader provides the implementation (a {@code GuiGraphics}-backed renderer +
 * a real {@code Screen}); off-client, or in a headless test, {@link #isAvailable()} is {@code false} and
 * {@link #open} is a safe no-op. This mirrors {@code Platform.bridge()} — the pure {@code aetherium-ui} module
 * defines the contract, {@code aetherium-loader} implements it, and no Minecraft type crosses the boundary.
 * RU: Мод строит декларативный {@link AetheriumScreen}; {@code AetheriumUi.open(screen)} просит активную
 * платформу показать его. Загрузчик даёт реализацию (рендерер поверх {@code GuiGraphics} + настоящий
 * {@code Screen}); вне клиента или в headless-тесте {@link #isAvailable()} == {@code false}, а {@link #open}
 * — безопасная заглушка. Зеркалит {@code Platform.bridge()}.
 */
public interface UiAccess {

    /** True if a real client display is present (false off-client / headless). */
    boolean isAvailable();

    /** Show {@code screen} to the player. A no-op when {@link #isAvailable()} is false. */
    void open(AetheriumScreen screen);

    /** Close the current Aetherium screen, if one is open. */
    default void close() {
        // no-op by default
    }
}
