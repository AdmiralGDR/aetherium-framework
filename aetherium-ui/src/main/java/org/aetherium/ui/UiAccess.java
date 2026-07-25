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

    /**
     * Navigate to {@code screen}, remembering the current one so {@link #pop()} returns to it ().
     *
     * <p>EN: Removes a whole class of navigation bugs — without a stack, each screen must capture and re-open
     * its predecessor by hand. The default just {@link #open(AetheriumScreen)}s (no history); the platform
     * implementation keeps a real back-stack. A no-op off-client.
     * RU: Убирает целый класс ошибок навигации: без стека каждый экран сам запоминает и переоткрывает
     * предшественника. По умолчанию — просто {@link #open(AetheriumScreen)}; реализация платформы ведёт стек.
     */
    default void push(AetheriumScreen screen) {
        open(screen);
    }

    /** Return to the previous pushed screen, or {@link #close()} if the stack is empty (). */
    default void pop() {
        close();
    }

    /**
     * Register a client keybind so a player can discover and open a screen from the vanilla Controls menu
     * (). Without this, a mod's UI is reachable only by a chat command or a lucky right-click.
     *
     * <p>EN: {@code translationKey} names the binding (e.g. {@code "key.mymod.open_panel"}), {@code category}
     * groups it in Controls (e.g. {@code "key.categories.mymod"}), {@code defaultKey} is a GLFW key code
     * ({@code -1} = unbound, which still lists the mod in Controls), and {@code action} runs when the key is
     * pressed. The default is a no-op (headless/server); the platform impl maps it to a real key mapping.
     * RU: Регистрирует клиентскую клавишу, чтобы игрок нашёл и открыл экран из ванильного меню управления
     * (). {@code defaultKey} — код GLFW ({@code -1} = не назначено, но мод всё равно виден в управлении).
     * По умолчанию — no-op (headless/сервер); реализация платформы отображает это в настоящий KeyMapping.
     */
    default void registerKeybind(String translationKey, String category, int defaultKey, Runnable action) {
        // no client to bind on
    }
}
