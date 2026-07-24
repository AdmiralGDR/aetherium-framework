/*
 * Aetherium Framework — Kotlin surface for the Consistency contracts (@Requires/@Ensures parity).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
@file:JvmName("Contracts")

package org.aetherium.ktx

import org.aetherium.injector.contract.Constraint

/**
 * EN: Kotlin-first names for the ACID Consistency contracts. The annotations themselves are the Java
 * ones (the CLI's `ContractAnalyzer` reads exactly those descriptors from the bytecode), so a Kotlin
 * hook annotates identically — these aliases and constants simply remove the imports:
 * RU: Kotlin-имена для контрактов согласованности ACID. Сами аннотации — Java-аннотации (CLI-шный
 * `ContractAnalyzer` читает из байткода именно эти дескрипторы), поэтому Kotlin-хук аннотируется так
 * же — псевдонимы и константы лишь убирают импорты:
 *
 * ```
 * @Ensures(Constraint.NON_NEGATIVE)
 * fun lightLevel(ctx: HookContext): Int { ... }
 * ```
 */
typealias Ensures = org.aetherium.injector.contract.Ensures

/** @see Ensures */
typealias Requires = org.aetherium.injector.contract.Requires

/** Convenience re-exports so a DSL user writes `NON_NEGATIVE`, not `Constraint.NON_NEGATIVE`. */
val NON_NEGATIVE: Constraint get() = Constraint.NON_NEGATIVE
val POSITIVE: Constraint get() = Constraint.POSITIVE
val NON_POSITIVE: Constraint get() = Constraint.NON_POSITIVE
val NEGATIVE: Constraint get() = Constraint.NEGATIVE
