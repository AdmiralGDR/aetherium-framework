/*
 * Aetherium Framework — curated index of common vanilla injection points (LSP autocomplete source).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.lsp;

import java.util.List;
import java.util.Locale;

/**
 * A curated, loader-agnostic catalogue of frequently-injected vanilla methods for IDE autocomplete.
 *
 * <p>EN: Hand-picked hot injection targets (entity/level ticking, damage, rendering, world load) with the
 * anchors that are valid for each. It is intentionally <em>data only</em> — JVM internal names and
 * descriptors, never a Minecraft import — so the CLI can offer "highlight valid injection points" without
 * the game on the classpath. A real deployment would augment this from the mapped Minecraft jar; the
 * curated seed makes the feature useful and testable offline today.
 * RU: Кураторский, независимый от загрузчика каталог часто-инъецируемых ванильных методов для
 * автодополнения IDE. Намеренно <em>только данные</em> — JVM-имена и дескрипторы, без импортов Minecraft —
 * чтобы CLI подсвечивал валидные точки инъекции без игры на classpath. В реальном развёртывании дополняется
 * из маппленного jar Minecraft; кураторский набор делает функцию полезной и тестируемой офлайн уже сейчас.
 */
public final class VanillaMethodIndex {

    private static final List<String> HEAD_RETURN = List.of("HEAD", "RETURN");
    private static final List<String> HEAD_ONLY = List.of("HEAD");

    private static final List<InjectionPoint> POINTS = List.of(
            new InjectionPoint("net/minecraft/world/entity/Entity", "tick", "()V", HEAD_RETURN,
                    "Per-tick entity update — the canonical async-tick / movement injection point."),
            new InjectionPoint("net/minecraft/world/entity/LivingEntity", "tick", "()V", HEAD_RETURN,
                    "Living-entity tick (AI, effects); cancel HEAD to fully suppress vanilla updates."),
            new InjectionPoint("net/minecraft/world/entity/LivingEntity", "hurt",
                    "(Lnet/minecraft/world/damagesource/DamageSource;F)Z", HEAD_RETURN,
                    "Damage application — cancel to grant invulnerability or rewrite the amount."),
            new InjectionPoint("net/minecraft/world/entity/player/Player", "hurt",
                    "(Lnet/minecraft/world/damagesource/DamageSource;F)Z", HEAD_RETURN,
                    "Player damage — shields/absorption mods commonly inject HEAD here."),
            new InjectionPoint("net/minecraft/server/level/ServerLevel", "tick",
                    "(Ljava/util/function/BooleanSupplier;)V", HEAD_RETURN,
                    "Server world tick — batch entity/physics passes attach here."),
            new InjectionPoint("net/minecraft/client/Minecraft", "tick", "()V", HEAD_RETURN,
                    "Client game-loop tick (client dist only)."),
            new InjectionPoint("net/minecraft/world/level/Level", "getBlockState",
                    "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", HEAD_ONLY,
                    "Block-state lookup — hot path; inject HEAD to virtualize/override blocks."),
            new InjectionPoint("net/minecraft/server/MinecraftServer", "tickServer",
                    "(Ljava/util/function/BooleanSupplier;)V", HEAD_RETURN,
                    "Top-level server tick — global per-tick scheduling."),
            new InjectionPoint("net/minecraft/world/level/chunk/LevelChunk", "setBlockState",
                    "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)"
                            + "Lnet/minecraft/world/level/block/state/BlockState;", HEAD_RETURN,
                    "Chunk block write — capture changes for delta-sync / observers."),
            new InjectionPoint("net/minecraft/world/entity/Entity", "remove",
                    "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", HEAD_ONLY,
                    "Entity removal — release off-heap StructArena rows here."));

    private VanillaMethodIndex() {
    }

    /** Every known injection point. */
    public static List<InjectionPoint> all() {
        return POINTS;
    }

    /**
     * EN: Fuzzy prefix match over the {@code owner.method} target — case-insensitive substring, so typing
     * {@code "tick"} or {@code "player::hurt"} both narrow the list. Empty query returns everything.
     * RU: Нечёткое совпадение по {@code owner.method} — регистронезависимая подстрока.
     */
    public static List<InjectionPoint> complete(String query) {
        if (query == null || query.isBlank()) {
            return POINTS;
        }
        String q = query.toLowerCase(Locale.ROOT).replace('/', '.');
        return POINTS.stream()
                .filter(p -> p.label().toLowerCase(Locale.ROOT).contains(q)
                        || p.target().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    /** All injection points declared on the given owner (internal name or dotted), or empty. */
    public static List<InjectionPoint> forOwner(String owner) {
        if (owner == null) {
            return List.of();
        }
        String internal = owner.replace('.', '/');
        return POINTS.stream().filter(p -> p.owner().equals(internal)).toList();
    }

    /** Whether {@code anchor} is valid for the given {@code owner::method} target ({@code HEAD}/{@code RETURN}). */
    public static boolean isValidAnchor(String target, String anchor) {
        return POINTS.stream()
                .filter(p -> p.target().equals(target.replace('/', '.')))
                .anyMatch(p -> p.anchors().contains(anchor));
    }
}
