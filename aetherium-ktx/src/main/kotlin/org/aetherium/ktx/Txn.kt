/*
 * Aetherium Framework — Kotlin DSL over the ACID transactional injection engine (parity).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
@file:JvmName("Txn")

package org.aetherium.ktx

import org.aetherium.injector.AetheriumInjector
import org.aetherium.injector.txn.EngineReport
import org.aetherium.injector.txn.TargetClass
import org.aetherium.injector.txn.TransactionalInjector

/**
 * EN: Build and apply an ACID hook transaction pass from a concise Kotlin block. Each `mod(...)` is one
 * all-or-nothing transaction: if any of its targeted classes fails the verification sandbox, every edit
 * of that mod is rolled back and the mod is disabled, while sibling mods commit independently. `inline`
 * sugar over [TransactionalInjector] — same engine, zero extra dispatch.
 * RU: Строит и применяет проход ACID-транзакций хуков из лаконичного Kotlin-блока. Каждый `mod(...)` —
 * одна транзакция «всё или ничего»: если любой целевой класс не проходит песочницу верификации, все
 * правки этого мода откатываются и мод отключается, а соседние моды коммитятся независимо. `inline`
 * сахар над [TransactionalInjector] — тот же движок, без лишней диспетчеризации.
 *
 * ```
 * val report = transaction(verifyLoader) {
 *     mod("gravity_plus",
 *         "com.example.MockA" to bytesA,
 *         "com.example.MockB" to bytesB) {
 *         inject("com.example.MockA::compute", "()I", anchor = HEAD) {
 *             hook("gravity:boost") { cancelWith(99) }
 *         }
 *     }
 * }
 * report.committed()   // the mods that were published
 * report.rolledBack()  // the mods that were disabled (nothing of theirs applied)
 * ```
 */
inline fun transaction(verifyLoader: ClassLoader?, block: TransactionScope.() -> Unit): EngineReport {
    val engine = TransactionalInjector.create(verifyLoader)
    TransactionScope(engine).block()
    return engine.apply()
}

/** Receiver for the top-level `transaction { ... }` block. */
@AetheriumDsl
class TransactionScope @PublishedApi internal constructor(
    @PublishedApi internal val engine: TransactionalInjector,
) {

    /**
     * Register a mod transaction whose injector is built inline with the standard `inject { ... }` DSL.
     * [targets] pair each binary class name with its pristine (vanilla) bytes; their order is the
     * deterministic hook-application (and reverse-rollback) order.
     */
    fun mod(modId: String, vararg targets: Pair<String, ByteArray>, block: InjectorScope.() -> Unit) {
        mod(modId, injector(block), *targets)
    }

    /** Register a mod transaction around an already-built [AetheriumInjector]. */
    fun mod(modId: String, injection: AetheriumInjector, vararg targets: Pair<String, ByteArray>) {
        require(targets.isNotEmpty()) { "mod(\"$modId\") declared no target classes" }
        engine.mod(modId, injection, targets.map { (name, bytes) -> TargetClass(name, bytes) })
    }
}

// --- Kotlin-friendly views over the transaction outcome --------------------------------------------

/** The published post-transaction bytes for [binaryName], or `null` if no committed mod produced them. */
fun EngineReport.publishedOrNull(binaryName: String): ByteArray? = published(binaryName).orElse(null)

/** True if the given mod's transaction committed (was published in full). */
fun EngineReport.isCommitted(modId: String): Boolean = results()[modId]?.committed() ?: false

/** True if the given mod's transaction was rolled back (disabled, nothing applied). */
fun EngineReport.isRolledBack(modId: String): Boolean = results()[modId]?.rolledBack() ?: false
