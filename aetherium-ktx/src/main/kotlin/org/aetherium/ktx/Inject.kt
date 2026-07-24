/*
 * Aetherium Framework — Kotlin injection DSL (zero-overhead sugar over AetheriumInjector / HookDag).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
@file:JvmName("Inject")

package org.aetherium.ktx

import org.aetherium.injector.AetheriumInjector
import org.aetherium.injector.ContextualHook
import org.aetherium.injector.HookContext
import org.aetherium.injector.InjectionAnchor

/**
 * EN: Marks the Aetherium builder receivers so an inner block cannot accidentally call an outer
 * scope's member (standard Kotlin DSL hygiene).
 * RU: Помечает приёмники построителей Aetherium, чтобы внутренний блок не вызвал случайно член
 * внешней области (стандартная гигиена DSL Kotlin).
 */
@DslMarker
annotation class AetheriumDsl

/** Convenience re-exports so a DSL user writes `HEAD`/`RETURN`, not `InjectionAnchor.HEAD`. */
val HEAD: InjectionAnchor get() = InjectionAnchor.HEAD
val RETURN: InjectionAnchor get() = InjectionAnchor.RETURN

/**
 * EN: Build an [AetheriumInjector] from a concise Kotlin block. `inline` so the block is lowered with
 * no extra lambda/closure object; the hooks it registers bind to the exact same O(1) invokedynamic
 * [org.aetherium.injector.HookTable] the Java API uses — zero runtime reflection.
 * RU: Строит [AetheriumInjector] из лаконичного Kotlin-блока. `inline`, поэтому блок встраивается без
 * лишнего объекта-замыкания; зарегистрированные хуки привязываются к той же O(1) invokedynamic
 * [org.aetherium.injector.HookTable], что и Java-API — без рефлексии в рантайме.
 *
 * ```
 * val injector = injector {
 *     inject("net.minecraft.world.entity.Entity::tick") {
 *         captureArgs()
 *         hook("mymod:tick_guard") { cancelIf { intArg(0) > 100 } }
 *     }
 * }.install()
 * ```
 */
inline fun injector(block: InjectorScope.() -> Unit): AetheriumInjector {
    val injector = AetheriumInjector.create()
    InjectorScope(injector).block()
    return injector
}

/** Install this injector's hooks into the global table; returns the installed-hook count (fluent). */
fun AetheriumInjector.install(): AetheriumInjector {
    installHooks()
    return this
}

/** Receiver for the top-level `injector { ... }` block. */
@AetheriumDsl
class InjectorScope @PublishedApi internal constructor(
    @PublishedApi internal val injector: AetheriumInjector,
) {

    /**
     * Declare a merged, DAG-ordered hook group on one target method. [target] is `package.Class::method`
     * (dots or slashes accepted); [descriptor] defaults to `()V`; [anchor] defaults to [HEAD].
     */
    fun inject(
        target: String,
        descriptor: String = "()V",
        anchor: InjectionAnchor = InjectionAnchor.HEAD,
        block: InjectSpec.() -> Unit,
    ) {
        val (internalName, method) = parseTarget(target)
        val spec = InjectSpec().apply(block)
        require(spec.hooks.isNotEmpty()) { "inject(\"$target\") declared no hooks" }

        val builder = injector.inClass(internalName).method(method, descriptor).at(anchor)
        if (spec.captureArgs) builder.captureArguments()
        for (entry in spec.hooks) {
            builder.hook(entry.id, entry.hook)
            if (entry.before.isNotEmpty()) builder.runBefore(*entry.before.toTypedArray())
            if (entry.after.isNotEmpty()) builder.runAfter(*entry.after.toTypedArray())
        }
        builder.commit()
    }
}

/** Receiver for one `inject(...) { ... }` block: collects the hook group before it is committed. */
@AetheriumDsl
class InjectSpec @PublishedApi internal constructor() {
    @PublishedApi internal var captureArgs: Boolean = false
    @PublishedApi internal val hooks: MutableList<HookEntry> = mutableListOf()

    /** Box the target method's arguments into the shared [HookContext] (opt-in; enables `arg`/`intArg`). */
    fun captureArgs() {
        captureArgs = true
    }

    /** Add a named, context-aware hook. Its body declares ordering and (optional) cancellation. */
    fun hook(id: String, block: HookBody.() -> Unit) {
        val body = HookBody().apply(block)
        hooks += HookEntry(id, body.toHook(), body.before.toList(), body.after.toList())
    }
}

/** Receiver for one `hook(id) { ... }` body. */
@AetheriumDsl
class HookBody @PublishedApi internal constructor() {
    @PublishedApi internal val before: MutableList<String> = mutableListOf()
    @PublishedApi internal val after: MutableList<String> = mutableListOf()
    private var action: (HookContext.() -> Unit)? = null
    private var cancelPredicate: (HookContext.() -> Boolean)? = null
    private var cancelValue: (HookContext.() -> Any?)? = null

    /** Order this hook before the named hook id(s) in the merged group. */
    fun runBefore(vararg ids: String) {
        before += ids
    }

    /** Order this hook after the named hook id(s) in the merged group. */
    fun runAfter(vararg ids: String) {
        after += ids
    }

    /** Run an arbitrary side effect against the [HookContext] every time the hook fires. */
    fun run(action: HookContext.() -> Unit) {
        this.action = action
    }

    /** Cancel a `void` target method whenever [predicate] holds (frame-correct early RETURN). */
    fun cancelIf(predicate: HookContext.() -> Boolean) {
        cancelPredicate = predicate
        cancelValue = null
    }

    /** Cancel a value-returning target with [returnValue] whenever [predicate] holds. */
    fun cancelWith(returnValue: Any?, predicate: HookContext.() -> Boolean) {
        cancelPredicate = predicate
        cancelValue = { returnValue }
    }

    /** Unconditionally cancel a value-returning target with [returnValue] — `ctx.cancel(value)` parity. */
    fun cancelWith(returnValue: Any?) {
        cancelWith(returnValue) { true }
    }

    @PublishedApi
    internal fun toHook(): ContextualHook {
        val action = this.action
        val predicate = this.cancelPredicate
        val value = this.cancelValue
        return ContextualHook { ctx ->
            action?.invoke(ctx)
            if (predicate != null && predicate(ctx)) {
                if (value != null) ctx.cancel(value(ctx)) else ctx.cancel()
            }
        }
    }
}

/** Internal carrier from the DSL block to the Java [org.aetherium.injector.MergedHookBuilder]. */
@PublishedApi
internal class HookEntry(
    val id: String,
    val hook: ContextualHook,
    val before: List<String>,
    val after: List<String>,
)

/** Split `package.Class::method` (dots or slashes) into a JVM internal name and a method name. */
@PublishedApi
internal fun parseTarget(target: String): Pair<String, String> {
    val sep = target.indexOf("::")
    require(sep > 0) { "target must be 'package.Class::method', got: \"$target\"" }
    val internalName = target.substring(0, sep).replace('.', '/')
    val method = target.substring(sep + 2)
    require(method.isNotEmpty()) { "missing method name in target: \"$target\"" }
    return internalName to method
}

// --- Type-safe HookContext argument accessors (no casts at the call site, never throw) -------------

/** The boxed argument at [index], or `null` if out of range / uncaptured. */
fun HookContext.argOrNull(index: Int): Any? = arg(index)

/** The `int` argument at [index], or `0` if absent/mismatched — never throws. */
fun HookContext.intArg(index: Int): Int = (arg(index) as? Int) ?: 0

/** The `long` argument at [index], or `0L` if absent/mismatched. */
fun HookContext.longArg(index: Int): Long = (arg(index) as? Long) ?: 0L

/** The `float` argument at [index], or `0f` if absent/mismatched. */
fun HookContext.floatArg(index: Int): Float = (arg(index) as? Float) ?: 0f

/** The `double` argument at [index], or `0.0` if absent/mismatched. */
fun HookContext.doubleArg(index: Int): Double = (arg(index) as? Double) ?: 0.0

/** The `boolean` argument at [index], or `false` if absent/mismatched. */
fun HookContext.boolArg(index: Int): Boolean = (arg(index) as? Boolean) ?: false

/** The argument at [index] cast to [T], or `null` if absent/mismatched. */
inline fun <reified T> HookContext.argAs(index: Int): T? = arg(index) as? T
