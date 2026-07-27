/*
 * Aetherium Framework — UI entry point (resolve the platform UiAccess).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Entry point to the UI navigation layer: {@code AetheriumUi.open(screen)}.
 *
 * <p>EN: Resolves the active {@link UiAccess} once via {@code ServiceLoader}. If none is present (a headless
 * tool, a dedicated server, or a unit test) it returns a safe <strong>no-op</strong> access instead of
 * throwing — so a mod can call {@code AetheriumUi.open(...)} unconditionally. In game on the client,
 * {@code aetherium-loader} registers the NeoForge implementation and it is selected automatically.
 * RU: Разрешает активный {@link UiAccess} один раз через {@code ServiceLoader}. Если его нет (headless,
 * выделенный сервер, юнит-тест) — возвращает безопасную <strong>заглушку</strong>, поэтому мод может
 * вызывать {@code AetheriumUi.open(...)} безусловно. На клиенте загрузчик регистрирует реализацию NeoForge.
 */
public final class AetheriumUi {

    private static final UiAccess ACCESS = resolve();

    private AetheriumUi() {
    }

    /** The active UI access (never null; a no-op off-client). */
    public static UiAccess access() {
        return ACCESS;
    }

    /** Show {@code screen} to the player (no-op off-client). */
    public static void open(AetheriumScreen screen) {
        ACCESS.open(screen);
    }

    /** Close the current Aetherium screen (— symmetric with {@link #open}). */
    public static void close() {
        ACCESS.close();
    }

    /** Navigate to {@code screen}, remembering the current one so {@link #back()} returns (). */
    public static void push(AetheriumScreen screen) {
        ACCESS.push(screen);
    }

    /** Go back to the previous pushed screen, or close if there is none (). */
    public static void back() {
        ACCESS.pop();
    }

    /**
     * Register a client keybind () — makes the mod appear in the vanilla Controls screen, the one
     * place players look for how to open a UI. A no-op off-client. {@code defaultKey} is a GLFW code, or
     * {@code -1} for an unbound (still listed) entry.
     */
    public static void registerKeybind(String translationKey, String category, int defaultKey, Runnable action) {
        ACCESS.registerKeybind(translationKey, category, defaultKey, action);
    }

    /** Add a persistent HUD overlay painted over the game each frame (no-op off-client). */
    public static void addHud(AetheriumHud hud) {
        ACCESS.addHud(hud);
    }

    /** Remove a previously-added HUD overlay (no-op off-client). */
    public static void removeHud(AetheriumHud hud) {
        ACCESS.removeHud(hud);
    }

    /** Whether a real client display is available. */
    public static boolean isAvailable() {
        return ACCESS.isAvailable();
    }

    private static UiAccess resolve() {
        try {
            Optional<UiAccess> found = ServiceLoader.load(UiAccess.class).findFirst();
            return found.orElseGet(NoopUiAccess::new);
        } catch (Throwable t) {
            return new NoopUiAccess();
        }
    }

    /** Safe fallback used when no client UI platform is registered. */
    private static final class NoopUiAccess implements UiAccess {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void open(AetheriumScreen screen) {
            // no client to display on
        }
    }
}
