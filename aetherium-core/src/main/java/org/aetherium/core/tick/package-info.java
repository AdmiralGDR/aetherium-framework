/*
 * Aetherium Framework — async tick engine.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * Async tick dispatch on virtual threads with a 50 ms Sync Barrier.
 *
 * <p><b>EN.</b> {@link org.aetherium.core.tick.AetheriumTickEngine} offloads heavy per-tick logic
 * (physics, fluid cellular automata) onto Java 21 virtual threads, joins them at a Sync Barrier, and
 * commits results on the main thread — no {@code ConcurrentModificationException}. Modders just
 * annotate a method {@link org.aetherium.core.tick.AetheriumAsyncTick} or implement
 * {@link org.aetherium.core.tick.AsyncTickTask}.
 *
 * <p><b>RU.</b> {@link org.aetherium.core.tick.AetheriumTickEngine} выгружает тяжёлую логику тика
 * (физика, клеточные автоматы жидкостей) на виртуальные потоки Java 21, объединяет их Sync-барьером
 * и фиксирует результаты на главном потоке — без {@code ConcurrentModificationException}. Моддеры
 * лишь аннотируют метод {@link org.aetherium.core.tick.AetheriumAsyncTick} или реализуют
 * {@link org.aetherium.core.tick.AsyncTickTask}.
 */
package org.aetherium.core.tick;
