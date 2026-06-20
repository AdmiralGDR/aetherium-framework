/*
 * Aetherium Framework — ephemeral JFR probe weaver (zero static overhead).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.probe;

import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.TransformResult;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.Objects;

/**
 * Weaves a JFR {@link AetheriumMethodEvent} (begin/commit) into exactly the methods named by the active
 * {@link ProbeTarget}s — the bytecode half of zero-overhead ephemeral telemetry.
 *
 * <p>EN: This is the crux of the "no hot-path conditional" rule. A metrics framework that checks
 * {@code if (profilingEnabled)} pays a branch on every call forever. Aetherium instead <strong>weaves
 * the probe in only while a profile is requested and removes it afterwards</strong>: when the active set
 * is empty, {@link #handles(ClassContext)} returns {@code false} and the class is byte-for-byte
 * untouched — there is literally no probe code, not even a check. When a target is active, entry gets
 * {@code event.begin()} and every return gets {@code event.commit()} (net-zero operand stack, so the
 * engine's {@code COMPUTE_FRAMES} keeps the class verifiable). {@link DynamicProbeController} flips the
 * set and re-transforms, making the instrumentation truly ephemeral.
 *
 * <p>RU: Суть правила «никаких проверок на горячем пути». Фреймворк метрик с {@code if (включено)} платит
 * ветвление на каждом вызове навсегда. Aetherium же <strong>вплетает зонд только на время запроса
 * профиля и убирает после</strong>: при пустом активном множестве {@link #handles(ClassContext)}
 * возвращает {@code false}, и класс не меняется ни на байт — кода зонда нет вовсе. При активной цели на
 * входе появляется {@code event.begin()}, а перед каждым возвратом — {@code event.commit()} (нулевой
 * баланс стека, поэтому {@code COMPUTE_FRAMES} движка сохраняет верифицируемость).
 */
public final class ProbeWeaver implements ClassTransformer {

    private static final String EVENT_INTERNAL = "org/aetherium/injector/probe/AetheriumMethodEvent";

    private final List<ProbeTarget> targets;
    private final int order;

    public ProbeWeaver(List<ProbeTarget> targets, int order) {
        this.targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        this.order = order;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public boolean handles(ClassContext context) {
        // Empty active set OR no matching target -> class is left byte-for-byte untouched (zero overhead).
        String internal = context.internalName();
        for (ProbeTarget t : targets) {
            if (t.classInternalName().equals(internal)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TransformResult apply(ClassContext context) {
        String internal = context.internalName();
        int woven = 0;
        for (MethodNode method : context.node().methods) {
            if (method.instructions == null || method.instructions.size() == 0) {
                continue; // abstract/native: nothing to time
            }
            boolean matched = false;
            for (ProbeTarget t : targets) {
                if (t.classInternalName().equals(internal) && t.matchesMethod(method.name, method.desc)) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                weave(internal, method);
                woven++;
            }
        }
        return woven > 0
                ? new TransformResult.Applied(context.node())
                : new TransformResult.Skipped("no probe target matched a method in " + internal);
    }

    @Override
    public String id() {
        return "AetheriumProbeWeaver(" + targets.size() + " target(s))";
    }

    private static void weave(String internal, MethodNode method) {
        int eventSlot = method.maxLocals;
        method.maxLocals += 1;
        String label = internal.replace('/', '.') + "#" + method.name;

        // --- entry: AetheriumMethodEvent e = new AetheriumMethodEvent(); e.method = label; e.begin();
        InsnList entry = new InsnList();
        entry.add(new TypeInsnNode(Opcodes.NEW, EVENT_INTERNAL));
        entry.add(new InsnNode(Opcodes.DUP));
        entry.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, EVENT_INTERNAL, "<init>", "()V", false));
        entry.add(new VarInsnNode(Opcodes.ASTORE, eventSlot));
        entry.add(new VarInsnNode(Opcodes.ALOAD, eventSlot));
        entry.add(new LdcInsnNode(label));
        entry.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.PUTFIELD, EVENT_INTERNAL, "method", "Ljava/lang/String;"));
        entry.add(new VarInsnNode(Opcodes.ALOAD, eventSlot));
        entry.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, EVENT_INTERNAL, "begin", "()V", false));
        method.instructions.insertBefore(method.instructions.getFirst(), entry);

        // --- before every return: e.commit();  (commit() sets the end time / duration)
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            int op = insn.getOpcode();
            if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
                InsnList commit = new InsnList();
                commit.add(new VarInsnNode(Opcodes.ALOAD, eventSlot));
                commit.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, EVENT_INTERNAL, "commit", "()V", false));
                method.instructions.insertBefore(insn, commit);
            }
            insn = next;
        }
    }
}
