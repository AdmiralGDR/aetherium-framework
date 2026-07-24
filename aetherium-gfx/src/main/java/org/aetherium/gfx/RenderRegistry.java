/*
 * Aetherium Framework — render registry.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

import org.aetherium.core.AetheriumException;
import org.aetherium.core.Diagnostic;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
    /** Claims an entity-type key atomically so two mods can't bind the same renderer key. */
    private static final ConcurrentHashMap<String, Boolean> CLAIMED = new ConcurrentHashMap<>();

    /**
     * Bind {@code renderer} to {@code entityTypeKey}. Registrations for <em>different</em> keys are additive
     * (multiple mods coexist); a <em>duplicate</em> key is rejected with an {@link AetheriumException} so two
     * mods don't silently fight over one entity's renderer.
     */
    public static void register(String entityTypeKey, AetheriumEntityRenderer renderer) {
        Objects.requireNonNull(entityTypeKey, "entityTypeKey");
        Objects.requireNonNull(renderer, "renderer");
        if (CLAIMED.putIfAbsent(entityTypeKey, Boolean.TRUE) != null) {
            throw new AetheriumException(Diagnostic.error("AE-GFX-RENDERER-DUP",
                    "A renderer is already registered for entity key '" + entityTypeKey
                            + "'. Two mods cannot bind the same key."));
        }
        ENTRIES.add(new Entry(entityTypeKey, renderer));
    }

    /** Snapshot of registered renderers, for the loader to bridge. */
    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static int size() {
        return ENTRIES.size();
    }

    /** Test/loader hook: forget all registrations (and key claims). */
    public static void reset() {
        ENTRIES.clear();
        CLAIMED.clear();
    }
}
