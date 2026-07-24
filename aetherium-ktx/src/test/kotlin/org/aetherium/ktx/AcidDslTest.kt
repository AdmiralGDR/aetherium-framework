/*
 * Aetherium Framework — Kotlin DSL tests for ACID parity (transactions, contracts, PAL).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ktx

import org.aetherium.edge.BlockEntityAccess
import org.aetherium.edge.BlockHandle
import org.aetherium.edge.BlockPos
import org.aetherium.edge.LevelContext
import org.aetherium.injector.AetheriumInjector
import org.aetherium.injector.HookContext
import org.aetherium.injector.contract.Constraint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import java.util.Optional

/**
 * EN: Proves the Kotlin DSL fully exposes the ACID engine: transactional rollback,
 * unconditional value-cancel, the @Requires/@Ensures contract surface, and the LevelContext PAL sugar.
 * RU: Доказывает, что Kotlin DSL полностью открывает движок ACID Фазы 19: транзакционный откат,
 * безусловную отмену со значением, поверхность контрактов @Requires/@Ensures и сахар PAL LevelContext.
 */
class AcidDslTest {

    // --- Atomicity: the transaction DSL ------------------------------------------------------------

    @Test
    fun brokenModRollsBackWhileHealthyModCommits() {
        val bytesA = computeClass("org/aetherium/ktx/demo/KMockA")
        val bytesB = computeClass("org/aetherium/ktx/demo/KMockB")
        val bytesC = computeClass("org/aetherium/ktx/demo/KMockC")

        // The broken mod: a valid cancel-hook on A, then invalid bytecode (stack underflow) on B —
        // built with the raw Java API because deliberately-broken insns are not DSL territory.
        val underflow = InsnList().apply { add(InsnNode(Opcodes.POP)) }
        val broken = AetheriumInjector.create()
            .inClass("org/aetherium/ktx/demo/KMockA").method("compute", "()I")
                .toStart().insertContextHookBefore { ctx -> ctx.cancel(99) }.commit()
            .inClass("org/aetherium/ktx/demo/KMockB").method("compute", "()I")
                .toStart().insertBefore(underflow).commit()

        val report = transaction(javaClass.classLoader) {
            mod("broken_mod", broken,
                "org.aetherium.ktx.demo.KMockA" to bytesA,
                "org.aetherium.ktx.demo.KMockB" to bytesB)
            // The healthy mod is built entirely in the DSL: unconditional value-cancel via cancelWith.
            mod("healthy_mod", "org.aetherium.ktx.demo.KMockC" to bytesC) {
                inject("org.aetherium.ktx.demo.KMockC::compute", "()I", anchor = HEAD) {
                    hook("healthy:boost") { cancelWith(63) }
                }
            }
        }

        // Atomicity: the broken mod published NOTHING (even its valid first hook was rolled back).
        assertTrue(report.isRolledBack("broken_mod"))
        assertNull(report.publishedOrNull("org.aetherium.ktx.demo.KMockA"))
        assertNull(report.publishedOrNull("org.aetherium.ktx.demo.KMockB"))

        // Availability: the healthy mod committed and its transformed bytes are published.
        assertTrue(report.isCommitted("healthy_mod"))
        assertNotNull(report.publishedOrNull("org.aetherium.ktx.demo.KMockC"))
        assertEquals(1, report.committedCount())
        assertEquals(1, report.rolledBackCount())
    }

    // --- cancel(value) parity -----------------------------------------------------------------------

    @Test
    fun unconditionalCancelWithSuppliesTheValue() {
        val hook = HookBody().apply { cancelWith(42) }.toHook()
        val ctx = HookContext(null, arrayOfNulls<Any>(0))
        hook.invoke(ctx)
        assertTrue(ctx.isCancelled)
        assertEquals(42, ctx.returnValue())
    }

    // --- Consistency: the contract surface -----------------------------------------------------------

    @Ensures(Constraint.NON_NEGATIVE)
    @Requires(param = 0, value = Constraint.NON_NEGATIVE)
    fun contractedHook(level: Int): Int = level + 1

    @Test
    fun contractAnnotationsAreReadableFromKotlin() {
        val method = javaClass.declaredMethods.first { it.name == "contractedHook" }
        val ensures = method.getAnnotation(org.aetherium.injector.contract.Ensures::class.java)
        val requires = method.getAnnotation(org.aetherium.injector.contract.Requires::class.java)
        assertEquals(Constraint.NON_NEGATIVE, ensures.value)
        assertEquals(0, requires.param)
        // The re-exported constants are the same enum instances the analyzer checks against.
        assertEquals(NON_NEGATIVE, ensures.value)
        assertEquals(2, contractedHook(1))
    }

    // --- LevelContext PAL extensions ------------------------------------------------------------------

    @Test
    fun levelContextExtensionsLowerToThePalCalls() {
        val level = FakeLevel()
        val (x, y, z) = blockPos(1, 64, -3)          // destructuring
        assertEquals(Triple(1, 64, -3), Triple(x, y, z))

        val pos = blockPos(0, 0, 0) + Triple(2, 3, 4) // operator offset
        assertEquals(BlockPos(2, 3, 4), pos)

        level[pos] = "minecraft:stone"                // operator set → setBlock
        assertEquals("minecraft:stone", level[pos].blockId())          // operator get → blockAt
        assertEquals("minecraft:air", level[9, 9, 9].blockId())        // coordinate get
        assertNull(level.blockEntityOrNull(pos))                       // Optional-free read
    }

    /** A tiny in-memory level: the extensions must route through these PAL methods and nothing else. */
    private class FakeLevel : LevelContext {
        private val blocks = HashMap<BlockPos, String>()

        override fun dimension() = "test:void"
        override fun isClientSide() = false
        override fun isLoaded(pos: BlockPos) = true
        override fun blockAt(pos: BlockPos) = object : BlockHandle {
            override fun pos() = pos
            override fun blockId() = blocks.getOrDefault(pos, "minecraft:air")
            override fun isAir() = !blocks.containsKey(pos)
            override fun destroySpeed() = 1.0f
            override fun property(name: String): Optional<String> = Optional.empty()
        }
        override fun blockEntityAt(pos: BlockPos): Optional<BlockEntityAccess> = Optional.empty()
        override fun setBlock(pos: BlockPos, blockId: String) {
            blocks[pos] = blockId
        }
        override fun scheduleNeighborUpdate(pos: BlockPos) = Unit
    }

    /** `public final class X { public static int compute() { return 21; } }` */
    private fun computeClass(internalName: String): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, internalName, null, "java/lang/Object", null)
        cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(Opcodes.ALOAD, 0)
            visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(Opcodes.RETURN)
            visitMaxs(1, 1)
            visitEnd()
        }
        cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "compute", "()I", null, null).apply {
            visitCode()
            visitIntInsn(Opcodes.BIPUSH, 21)
            visitInsn(Opcodes.IRETURN)
            visitMaxs(1, 0)
            visitEnd()
        }
        cw.visitEnd()
        return cw.toByteArray()
    }
}
