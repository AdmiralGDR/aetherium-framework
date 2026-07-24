/*
 * Aetherium Framework — the vocabulary of hook value constraints (Consistency contracts).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.contract;

/**
 * The value constraints a hook contract can assert about an integral value — the analyzable vocabulary of
 * {@link Requires} and {@link Ensures}.
 *
 * <p>EN: Kept deliberately small and abstract-interpretable: each constraint corresponds to a region of
 * the sign lattice the CLI's symbolic analyzer reasons over, so it can statically warn when a hook's
 * return value <em>might</em> violate the contract (e.g. an {@code @Ensures(NON_NEGATIVE)} light-level
 * hook that can return {@code -1}) before the game ever runs.
 *
 * <p>RU: Намеренно маленький и пригодный для абстрактной интерпретации набор: каждое ограничение
 * соответствует области знаковой решётки, по которой рассуждает символический анализатор CLI, чтобы
 * статически предупредить, когда возвращаемое значение хука <em>может</em> нарушить контракт (например,
 * {@code @Ensures(NON_NEGATIVE)} хук уровня освещённости, способный вернуть {@code -1}), ещё до запуска
 * игры.
 */
public enum Constraint {

    /** No constraint (the default) — never warns. */
    ANY,

    /** The value must be {@code >= 0} (e.g. a light level, a count, an index). */
    NON_NEGATIVE,

    /** The value must be {@code > 0}. */
    POSITIVE,

    /** The value must be {@code <= 0}. */
    NON_POSITIVE,

    /** The value must be {@code < 0}. */
    NEGATIVE
}
