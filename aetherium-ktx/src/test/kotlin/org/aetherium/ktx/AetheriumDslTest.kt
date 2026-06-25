/*
 * Aetherium Framework — Kotlin DSL tests (parity with the Java builder APIs).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ktx

import org.aetherium.injector.HookContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AetheriumDslTest {

    @Test
    fun injectorDslBuildsMergedContextHook() {
        val inj = injector {
            inject("net.minecraft.world.entity.Entity::tick") {
                captureArgs()
                hook("mymod:tick_guard") { cancelIf { intArg(0) > 100 } }
            }
        }
        // Dots in the target were lowered to a JVM internal name.
        assertTrue(inj.hasRuleFor("net/minecraft/world/entity/Entity"))
        assertEquals(1, inj.contextHookCount())
        // install() lowers to installHooks() — binds the O(1) invokedynamic table, no reflection.
        assertEquals(inj, inj.install())
    }

    @Test
    fun dagOrderingFlowsThroughToTheMergedGroup() {
        // Two hooks with a runBefore edge must both register and resolve acyclically (no throw).
        val inj = injector {
            inject("net.minecraft.world.entity.player.Player::hurt",
                descriptor = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z") {
                hook("shield:block") { runBefore("armor:absorb") }
                hook("armor:absorb") { }
            }
        }
        assertEquals(2, inj.contextHookCount())
    }

    @Test
    fun dslHookCancelsExactlyWhenPredicateHolds() {
        val hook = HookBody().apply { cancelIf { intArg(0) > 100 } }.toHook()

        val over = HookContext(null, arrayOf<Any>(150))
        hook.invoke(over)
        assertTrue(over.isCancelled)

        val under = HookContext(null, arrayOf<Any>(50))
        hook.invoke(under)
        assertFalse(under.isCancelled)
    }

    @Test
    fun cancelWithSuppliesReturnValue() {
        val hook = HookBody().apply { cancelWith(false) { boolArg(0) } }.toHook()
        val ctx = HookContext(null, arrayOf<Any>(true))
        hook.invoke(ctx)
        assertTrue(ctx.isCancelled)
        assertEquals(false, ctx.returnValue())
    }

    @Test
    fun typedArgAccessorsNeverThrowOutOfRange() {
        val ctx = HookContext(null, arrayOf<Any>(7))
        assertEquals(7, ctx.intArg(0))
        assertEquals(0, ctx.intArg(5))      // out of range → default, no exception
        assertEquals(0L, ctx.longArg(0))    // type mismatch → default
    }

    @Test
    fun structLayoutAndArenaRoundTripOffHeap() {
        val layout = structLayout {
            floats("x")
            floats("vx")
        }
        structArena(layout, 4) {
            val x = field("x")
            val vx = field("vx")
            for (i in 0 until count()) {
                setFloat(i, x, i.toFloat())
                setFloat(i, vx, 1.0f)
            }
            // one physics step: x += vx
            for (i in 0 until count()) {
                setFloat(i, x, getFloat(i, x) + getFloat(i, vx))
            }
            assertEquals(1.0f, getFloat(0, x))
            assertEquals(4.0f, getFloat(3, x))
        }
    }

    @Test
    fun contentDslGeneratesExpectedResourcePaths() {
        val files = content {
            block("mymod", "steel_block", hardness = 5.0f)
            item("mymod", "ruby")
        }.generate()

        assertTrue(files.keys.any { it == "assets/mymod/blockstates/steel_block.json" })
        assertTrue(files.keys.any { it == "assets/mymod/models/item/ruby.json" })
        assertTrue(files.keys.any { it == "data/mymod/loot_table/blocks/steel_block.json" })
        assertTrue(files.keys.any { it == "assets/mymod/lang/en_us.json" })
    }
}
