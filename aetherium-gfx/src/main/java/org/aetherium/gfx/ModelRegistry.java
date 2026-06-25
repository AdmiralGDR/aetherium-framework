/*
 * Aetherium Framework — registry of skeletal/animation models bound to entity keys.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loader-agnostic registry binding an {@link AetheriumModel} to an entity-type key — the animation
 * counterpart of {@link RenderRegistry}.
 *
 * <p>EN: A mod (or animation engine) registers a model for {@code namespace:path}; the loader reads
 * {@link #entries()} during renderer registration and drives each model's {@link AetheriumModel#render}
 * with a real {@code PoseStack}/{@code VertexConsumer}. Pure data — testable off-platform.
 * RU: Мод (или движок анимации) регистрирует модель для {@code namespace:path}; загрузчик читает
 * {@link #entries()} и вызывает {@link AetheriumModel#render} с реальными {@code PoseStack}/{@code VertexConsumer}.
 */
public final class ModelRegistry {

    private ModelRegistry() {
    }

    /** One registered model bound to an entity-type key. */
    public record Entry(String entityTypeKey, AetheriumModel model) {
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    public static void register(String entityTypeKey, AetheriumModel model) {
        ENTRIES.add(new Entry(entityTypeKey, model));
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static int size() {
        return ENTRIES.size();
    }
}
