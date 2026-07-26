/*
 * Aetherium Framework — a declarative machine behavior (E2E dispatch sample). 
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.content.AetheriumMachineLogic;
import org.aetherium.content.MachineContext;
import org.aetherium.edge.InteractionResult;
import org.aetherium.edge.PlayerHandle;

/**
 * Proves the machine system dispatches in-game (): a behaviour whose {@code tick} counts server
 * ticks, {@code onPlaced} records the placer's name, and {@code onUse} counts right-clicks — all into the
 * persistent {@link org.aetherium.content.MachineState}. The real headless server boot asserts these fire.
 * No Minecraft type appears; the whole machine is testable from a plain {@code main}.
 */
public final class TestMachineLogic implements AetheriumMachineLogic {

    /** Public no-arg constructor — the loader instantiates the behaviour by reflection. */
    public TestMachineLogic() {
    }

    @Override
    public void tick(MachineContext ctx) {
        if (!ctx.isClient()) {
            ctx.state().increment("ticks", 1);
        }
    }

    @Override
    public void onPlaced(MachineContext ctx) {
        ctx.state().setString("owner", ctx.placer().map(PlayerHandle::name).orElse("unknown"));
    }

    @Override
    public InteractionResult onUse(MachineContext ctx, PlayerHandle player) {
        if (!ctx.isClient()) {
            ctx.state().increment("uses", 1);
        }
        return InteractionResult.CANCEL;
    }
}
