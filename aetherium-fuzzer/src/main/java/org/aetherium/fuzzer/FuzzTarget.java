/*
 * Aetherium Framework — a single fuzz target (one entry point under adversarial input).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer;

import java.util.random.RandomGenerator;

/**
 * One fuzzable entry point: it knows how to drive itself with one randomized, adversarial case and how
 * to tell a clean contractual rejection apart from a genuine crash.
 *
 * <p>EN: {@link #exercise(RandomGenerator)} runs exactly one case against the target API using the
 * supplied (reproducibly seeded) random source. It may return normally (the input was handled) or throw.
 * {@link #expects(Throwable)} declares which thrown type is the target's <em>documented</em> failure mode
 * (e.g. {@code UnsupportedShaderException} for the compiler) — a clean rejection. Anything else the
 * {@link FuzzEngine} records as a crash. The contract is deliberately narrow: a fuzz target must never
 * itself decide an input is "bad" and skip it — it always hands the raw bytes to the production code.
 *
 * <p>RU: Одна фаззируемая точка входа: умеет прогнать один случайный враждебный случай и отличить
 * чистый контрактный отказ от настоящего краша. {@link #exercise(RandomGenerator)} прогоняет ровно один
 * случай по API, используя воспроизводимо засеянный источник случайности; может вернуться нормально или
 * бросить. {@link #expects(Throwable)} объявляет, какой брошенный тип — <em>документированный</em> режим
 * отказа (чистый отказ); всё прочее {@link FuzzEngine} фиксирует как краш.
 */
public interface FuzzTarget {

    /** A short, stable name used in the report and to derive per-case seeds. */
    String name();

    /** Run exactly one adversarial case. May throw; the engine classifies the throwable via {@link #expects}. */
    void exercise(RandomGenerator rng) throws Throwable;

    /**
     * EN: Whether {@code t} is this target's clean, contractual rejection (true) rather than a crash.
     * Default: nothing is acceptable — the production code must not throw at all (override to relax).
     * RU: Является ли {@code t} чистым контрактным отказом цели (true), а не крашем. По умолчанию ничего
     * не допускается — продакшен-код не должен бросать вовсе (переопределите, чтобы ослабить).
     */
    default boolean expects(Throwable t) {
        return false;
    }
}
