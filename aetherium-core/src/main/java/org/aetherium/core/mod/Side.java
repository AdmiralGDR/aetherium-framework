/*
 * Aetherium Framework — the physical side a mod (or one init) runs on.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.mod;

/**
 * The side an {@link AetheriumInit} (or a feature) is written for — so an author can write a
 * <strong>both-side</strong>, <strong>server-side</strong>, or <strong>client-side</strong> mod without
 * importing a loader's dist type.
 *
 * <p>EN: A running JVM has one <em>physical</em> side — {@link #CLIENT} (the game client, which may also host
 * an integrated single-player server) or {@link #SERVER} (a dedicated server). {@code @AetheriumInit(side=…)}
 * declares which side an init is for, and the generated entrypoint gates the call by {@link #activeOn}:
 * <ul>
 *   <li>{@link #BOTH} — always runs (the default; unchanged behaviour).</li>
 *   <li>{@link #SERVER} — server-side logic (world state, commands). Safe on a dedicated server and on a
 *       client (which can host the integrated server), so it runs on either physical side.</li>
 *   <li>{@link #CLIENT} — client-only code (rendering, HUD, keybinds). It <strong>must not</strong> load on a
 *       dedicated server, whose JVM has no client classes — so it runs only when the physical side is
 *       {@code CLIENT}. This is the gate that turns "a client-side mod crashes a dedicated server" into a
 *       no-op.</li>
 * </ul>
 *
 * <p>RU: У запущенной JVM одна <em>физическая</em> сторона — {@link #CLIENT} (игровой клиент, который может
 * держать встроенный сервер одиночной игры) или {@link #SERVER} (выделенный сервер).
 * {@code @AetheriumInit(side=…)} объявляет, для какой стороны предназначен init, а сгенерированная точка
 * входа ограничивает вызов через {@link #activeOn}: {@link #BOTH} — всегда; {@link #SERVER} — серверная
 * логика, безопасна и на выделенном сервере, и на клиенте (со встроенным сервером); {@link #CLIENT} — только
 * клиент, чтобы клиентский код не грузился на выделенном сервере и не ронял его.
 */
public enum Side {

    /** Runs on both sides (the default). */
    BOTH,

    /** Server-side logic — runs wherever a server is present (dedicated, and a client's integrated server). */
    SERVER,

    /** Client-only code — runs only on the physical client, never on a dedicated server. */
    CLIENT;

    /**
     * Whether something declared for <em>this</em> side should run given the JVM's {@code current} physical
     * side ({@link #CLIENT} or {@link #SERVER}). {@link #BOTH} and {@link #SERVER} run on either physical side
     * (server logic is safe on a client's integrated server); {@link #CLIENT} runs only on a physical client.
     */
    public boolean activeOn(Side current) {
        return switch (this) {
            case BOTH, SERVER -> true;
            case CLIENT -> current == CLIENT;
        };
    }
}
