/*
 * Aetherium Framework — client keybind bridge ().
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns loader-agnostic {@code AetheriumUi.registerKeybind(...)} requests into real NeoForge key mappings.
 *
 * <p>EN: — a mod's UI needs a way to be <em>found</em>. A mod enqueues a request (a pure record,
 * no client type) during init; on the client, {@link #onRegisterKeyMappings} creates a {@link KeyMapping}
 * for each and registers it, so the binding appears in vanilla Controls, and {@link #onClientTick} runs the
 * bound action whenever the key is pressed. All client types live in method bodies / this client-only class,
 * so nothing here loads on a dedicated server. One bad handler can never break the client tick.
 * RU: — интерфейс мода нужно как-то <em>найти</em>. Мод ставит запрос в очередь (чистая запись, без
 * клиентских типов) при инициализации; на клиенте {@link #onRegisterKeyMappings} создаёт {@link KeyMapping}
 * и регистрирует его (клавиша появляется в ванильном управлении), а {@link #onClientTick} выполняет действие
 * при нажатии. Клиентские типы только внутри этого клиентского класса — на сервере ничего не грузится.
 */
public final class ClientKeybinds {

    /** A pending keybind request — pure (no client type), safe to build off-thread / off-client. */
    public record Request(String translationKey, String category, int defaultKey, Runnable action) {
    }

    private static final List<Request> PENDING = new ArrayList<>();
    private final Map<KeyMapping, Runnable> active = new LinkedHashMap<>();

    /** Queue a keybind request; realised on the next {@link RegisterKeyMappingsEvent}. */
    public static synchronized void enqueue(Request request) {
        if (request != null && request.translationKey() != null && request.action() != null) {
            PENDING.add(request);
        }
    }

    /** Mod-bus: create + register a {@link KeyMapping} for each queued request. */
    public void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        List<Request> requests;
        synchronized (ClientKeybinds.class) {
            requests = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Request r : requests) {
            KeyMapping mapping = new KeyMapping(
                    r.translationKey(), InputConstants.Type.KEYSYM, r.defaultKey(), r.category());
            event.register(mapping);
            active.put(mapping, r.action());
        }
    }

    /** Game-bus: run the bound action for every press consumed this tick. */
    public void onClientTick(ClientTickEvent.Post event) {
        for (Map.Entry<KeyMapping, Runnable> e : active.entrySet()) {
            while (e.getKey().consumeClick()) {
                try {
                    e.getValue().run();
                } catch (Throwable bad) {
                    // A misbehaving keybind handler must never break the client tick loop.
                }
            }
        }
    }
}
