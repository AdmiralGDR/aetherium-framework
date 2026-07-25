/*
 * Aetherium Framework — declarative screen abstraction (loader-agnostic).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * A loader-agnostic screen: a title and a {@link #build()} that returns the declarative widget tree.
 *
 * <p>EN: A mod subclasses this — never a Minecraft {@code Screen}. The loader wraps it in the platform
 * screen, calls {@link #build()} to get the tree, lays it out to the window via {@link UiRuntime}, paints
 * through a {@code GuiGraphics}-backed {@link UiRenderer}, and forwards clicks to
 * {@link UiRuntime#click}. {@link #onClose()} is the only lifecycle hook most screens need.
 * RU: Мод наследует это — никогда {@code Screen} из Minecraft. Загрузчик оборачивает его в экран
 * платформы, вызывает {@link #build()}, раскладывает через {@link UiRuntime}, рисует через
 * {@link UiRenderer} на основе {@code GuiGraphics} и пересылает клики в {@link UiRuntime#click}.
 */
public abstract class AetheriumScreen {

    /** The screen title (shown by the platform window chrome / narrator). */
    public abstract String title();

    /** Build the declarative widget tree for this screen. Called by the loader to (re)render. */
    public abstract Widget<?> build();

    /**
     * Build the tree for a known viewport () — override this for <em>responsive</em> layout (one
     * row on a wide screen, two on a narrow one). The loader calls this every frame with the real window
     * size; the default delegates to {@link #build()} so fixed-layout screens keep working unchanged.
     */
    public Widget<?> build(Rect viewport) {
        return build();
    }

    /**
     * Global key hook, invoked before the focused widget handles the key. Override to handle screen-level
     * shortcuts (e.g. Escape to close). Return {@code true} to consume the key and stop further handling.
     * The loader forwards the platform key code + modifier bitmask (see {@link UiRuntime} key constants).
     */
    public boolean onKey(int keyCode, int modifiers) {
        return false;
    }

    /** Lifecycle hook invoked when the screen is dismissed. Override to persist state. */
    public void onClose() {
        // default: nothing
    }
}
