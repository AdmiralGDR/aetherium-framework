/*
 * Aetherium Framework — client-only resolver for the local player ().
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader.client;

import net.minecraft.client.Minecraft;
import org.aetherium.edge.PlayerHandle;

import java.util.Optional;

/**
 * Resolves {@code Minecraft.getInstance().player} into a loader-agnostic {@link PlayerHandle} — the client
 * half of {@link org.aetherium.edge.PlayerAccess#local()} ().
 *
 * <p>EN: This is a <strong>client-only</strong> class. It references {@code net.minecraft.client.Minecraft},
 * so it must never be loaded on a dedicated server — and it is not: {@code NeoForgePlayerAccess.local()} calls
 * {@link #current()} only after {@code FMLEnvironment.dist.isClient()} passes, so the JVM never links this
 * class on a server (the same isolation {@code NeoForgeUiAccess} uses). Returns empty before a world is joined
 * (no player yet).
 * RU: <strong>Только клиент.</strong> Ссылается на {@code Minecraft}, поэтому не должен грузиться на выделенном
 * сервере — и не грузится: {@code NeoForgePlayerAccess.local()} зовёт {@link #current()} лишь после проверки
 * {@code FMLEnvironment.dist.isClient()}. Возвращает пусто до входа в мир (игрока ещё нет).
 */
public final class ClientLocalPlayer {

    private ClientLocalPlayer() {
    }

    /** The local client player as a {@link PlayerHandle}, or empty when no player exists yet. */
    public static Optional<PlayerHandle> current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return Optional.empty();
        }
        return Optional.of(new ClientPlayerHandle(mc.player));
    }
}
