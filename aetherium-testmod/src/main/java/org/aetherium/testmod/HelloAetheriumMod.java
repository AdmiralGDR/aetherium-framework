/*
 * Aetherium Framework — test mod.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.core.mod.AetheriumContext;
import org.aetherium.core.mod.AetheriumMod;

/**
 * A minimal in-game test mod — proves the Aetherium API surface end to end.
 *
 * <p>EN: Implements only {@link AetheriumMod}; imports <strong>nothing</strong> from NeoForge or
 * Minecraft. When the game starts, the Aetherium loader discovers this via {@code ServiceLoader} and
 * calls {@link #onInitialize}, where a single Aetherium API call ({@link AetheriumContext#log})
 * prints a message. This is the "zero boilerplate, loader-agnostic mod" in its simplest form.
 *
 * <p>RU: Реализует только {@link AetheriumMod}; не импортирует <strong>ничего</strong> из NeoForge
 * или Minecraft. При старте игры загрузчик Aetherium находит это через {@code ServiceLoader} и
 * вызывает {@link #onInitialize}, где единственный вызов API Aetherium ({@link AetheriumContext#log})
 * печатает сообщение. Это простейшая форма «мода без шаблонного кода, независимого от загрузчика».
 */
public final class HelloAetheriumMod implements AetheriumMod {

    @Override
    public String id() {
        return "hello-aetherium";
    }

    @Override
    public void onInitialize(AetheriumContext context) {
        context.log("Hello from " + id() + "! Aetherium is running on compute tier "
                + context.computeTier() + ".");
    }
}
