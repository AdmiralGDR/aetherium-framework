/*
 * Aetherium Framework — NeoForge UiAccess (open a screen on the client).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader.client;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;
import org.aetherium.ui.AetheriumScreen;
import org.aetherium.ui.UiAccess;

/**
 * The NeoForge-backed {@link UiAccess} — shows an {@link AetheriumScreen} via {@code Minecraft.setScreen}.
 *
 * <p>EN: Discovered by {@code ServiceLoader} ({@code META-INF/services/org.aetherium.ui.UiAccess}) so
 * {@code AetheriumUi.open(screen)} reaches it. Guarded by {@link FMLEnvironment#dist}: on a dedicated server
 * there is no client, so {@link #open} is a safe no-op. The class references client types only inside method
 * bodies, so it loads fine on a server (the client code just never runs there). Opening is marshalled onto
 * the render thread with {@code Minecraft.execute}.
 * RU: Обнаруживается через {@code ServiceLoader}, поэтому {@code AetheriumUi.open} доходит до него. На
 * выделенном сервере клиента нет → {@link #open} безопасно ничего не делает. Открытие переносится на
 * render-поток через {@code Minecraft.execute}.
 */
public final class NeoForgeUiAccess implements UiAccess {

    /** Back-stack for push/pop navigation (). Client thread only. */
    private final java.util.Deque<AetheriumScreen> stack = new java.util.ArrayDeque<>();

    /** Public no-arg constructor for {@code ServiceLoader}. */
    public NeoForgeUiAccess() {
    }

    @Override
    public boolean isAvailable() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public void open(AetheriumScreen screen) {
        if (!isAvailable() || screen == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new AetheriumScreenAdapter(screen)));
    }

    @Override
    public void close() {
        if (!isAvailable()) {
            return;
        }
        stack.clear();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(null));
    }

    @Override
    public void push(AetheriumScreen screen) {
        if (!isAvailable() || screen == null) {
            return;
        }
        stack.push(screen);
        open(screen);
    }

    @Override
    public void pop() {
        if (!isAvailable()) {
            return;
        }
        if (!stack.isEmpty()) {
            stack.pop(); // drop the current screen
        }
        AetheriumScreen previous = stack.peek();
        if (previous != null) {
            open(previous);
        } else {
            close();
        }
    }

    @Override
    public void registerKeybind(String translationKey, String category, int defaultKey, Runnable action) {
        if (!isAvailable() || translationKey == null || action == null) {
            return;
        }
        // Queue as a pure request; the client ClientKeybinds handler realises it on RegisterKeyMappingsEvent.
        ClientKeybinds.enqueue(new ClientKeybinds.Request(translationKey, category, defaultKey, action));
    }
}
