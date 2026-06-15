/*
 * LoomThreader demo mod — consumes Aetherium purely via Gradle/Maven coordinates.
 * Copyright (C) 2026 Example authors. Licensed under AGPL-3.0-or-later (inherited from Aetherium).
 * See <https://www.gnu.org/licenses/>.
 */
package com.example.loomthreader;

import org.aetherium.core.compute.StructArena;
import org.aetherium.core.compute.StructLayout;
import org.aetherium.core.compute.StructField;
import org.aetherium.core.mod.AetheriumContext;
import org.aetherium.core.mod.AetheriumMod;
import org.aetherium.edge.Platform;

/**
 * A minimal mod proving the new DevEx: it depends on {@code org.aetherium:aetherium-core} and
 * {@code aetherium-edge} resolved from Maven (no vendored jars), uses the data-oriented
 * {@link StructArena} and the {@link Platform} PAL, and is built with one {@code aetherium { }} block.
 */
public final class LoomThreaderMod implements AetheriumMod {

    private static final StructLayout PARTICLE = StructLayout.builder()
            .doubles("x").doubles("y").doubles("z")
            .build();

    @Override
    public String id() {
        return "loomthreader-demo";
    }

    @Override
    public void onInitialize(AetheriumContext context) {
        StructField x = PARTICLE.field("x");
        try (StructArena particles = StructArena.allocate(PARTICLE, 1_000)) {
            particles.setDouble(0, x, 42.0);
            context.log(id() + " allocated " + particles.count()
                    + " off-heap particles; platform=" + Platform.bridge().platformName()
                    + "; sample x=" + particles.getDouble(0, x));
        }
    }
}
