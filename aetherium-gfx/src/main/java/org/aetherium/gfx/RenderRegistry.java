/*
 * Aetherium Framework — render registry.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loader-agnostic registry of entity renderers. Mods bind an {@link AetheriumEntityRenderer} to an
 * entity-type key ({@code namespace:path}); the loader reads {@link #entries()} during its renderer
 * registration phase (e.g. NeoForge's {@code EntityRenderersEvent.RegisterRenderers}) and adapts each
 * one to a real Blaze3D {@code EntityRenderer}. Pure data — no game types — so it's testable off-platform.
 */
public final class RenderRegistry {

    private RenderRegistry() {}

    /** One registered renderer bound to an entity-type key. */
    public record Entry(String entityTypeKey, AetheriumEntityRenderer renderer) {}

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    public static void register(String entityTypeKey, AetheriumEntityRenderer renderer) {
        ENTRIES.add(new Entry(entityTypeKey, renderer));
    }

    /** Snapshot of registered renderers, for the loader to bridge. */
    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static int size() {
        return ENTRIES.size();
    }
}
