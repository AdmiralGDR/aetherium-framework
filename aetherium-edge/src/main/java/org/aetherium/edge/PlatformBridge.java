/*
 * Aetherium Framework — PAL platform bridge SPI.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

/**
 * The Platform Abstraction Layer's central SPI — the standardized bridge to a concrete loader.
 *
 * <p>EN: Exactly one implementation is provided per launch (NeoForge, Fabric, …) and discovered via
 * {@code java.util.ServiceLoader} (see {@link Platform}). It exposes {@link EntityAccess},
 * {@link LevelAccess} (blocks/block-entities/levels) and {@link EdgeEvents}, so an Aetherium mod reaches
 * every vanilla concept through this one abstract surface — no {@code net.neoforged}/{@code net.fabricmc}/
 * {@code net.minecraft} imports in mod code.
 * This module only <em>defines</em> the interface; {@code aetherium-loader} implements it.
 *
 * <p>RU: Ровно одна реализация предоставляется на запуск (NeoForge, Fabric, …) и обнаруживается
 * через {@code java.util.ServiceLoader} (см. {@link Platform}). Она экспонирует {@link EntityAccess}
 * и {@link EdgeEvents}, поэтому мод Aetherium достигает любой ванильной концепции через эту единую
 * абстрактную поверхность — без импортов {@code net.neoforged}/{@code net.fabricmc}/
 * {@code net.minecraft} в коде мода. Этот модуль только <em>определяет</em> интерфейс;
 * {@code aetherium-loader} его реализует.
 */
public interface PlatformBridge {

    /** Identifier of the active platform, e.g. {@code "neoforge"}, {@code "fabric"}, {@code "none"}. */
    String platformName();

    /** True if a real game platform is present (false for the no-op bridge used outside the game). */
    default boolean isGameAvailable() {
        return true;
    }

    EntityAccess entities();

    /** Loader-agnostic access to the world's blocks, block entities, and levels (the Block PAL). */
    LevelAccess levels();

    EdgeEvents events();
}
