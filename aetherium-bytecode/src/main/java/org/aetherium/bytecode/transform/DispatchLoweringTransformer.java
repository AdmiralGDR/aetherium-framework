package org.aetherium.bytecode.transform;

import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.TransformResult;
import org.aetherium.core.SymbolManifest;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Lowers abstract Aetherium API calls into {@code invokedynamic} — the core transform.
 *
 * <p>EN: Finds every {@code INVOKESTATIC} to the configured abstract API owner (the loader-agnostic
 * facade mods compile against) and rewrites it into an {@code invokedynamic} bound to
 * {@code AetheriumBootstraps.bootstrapDispatch}, passing the symbol's <strong>dense integer ID</strong>
 * read from the {@link SymbolManifest} (never a hardcoded literal). After the JVM links the site
 * once, the call is a direct, JIT-inlinable dispatch through the {@code DispatchTable} —
 * the {@code O(1)} runtime guarantee ({@code ARCHITECTURE.md} ). Mods write a plain static call;
 * all of this is invisible to them (zero-boilerplate goal).
 *
 * <p>RU: Находит каждый {@code INVOKESTATIC} к настроенному абстрактному владельцу API (фасаду,
 * независимому от загрузчика, под который компилируются моды) и переписывает его в
 * {@code invokedynamic}, привязанный к {@code AetheriumBootstraps.bootstrapDispatch}, передавая
 * <strong>плотный целочисленный ID</strong> символа из {@link SymbolManifest} (никогда не
 * зашитый литерал). После однократной линковки JVM вызов становится прямой, встраиваемой JIT
 * диспетчеризацией через {@code DispatchTable} — гарантия {@code O(1)}. Моды пишут обычный
 * статический вызов; всё это для них невидимо.
 */
public final class DispatchLoweringTransformer implements ClassTransformer {

    /** The bootstrap handle every lowered call site targets. */
    private static final Handle BOOTSTRAP = new Handle(
            Opcodes.H_INVOKESTATIC,
            "org/aetherium/bytecode/runtime/AetheriumBootstraps",
            "bootstrapDispatch",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;I)"
                    + "Ljava/lang/invoke/CallSite;",
            false);

    private final String apiOwnerInternalName;
    private final String namespace;
    private final SymbolManifest manifest;
    private final int order;

    /**
     * @param apiOwnerInternalName JVM internal name of the abstract API facade (e.g.
     *                             {@code "org/aetherium/api/Api"})
     * @param namespace            manifest namespace these symbols live under
     * @param manifest             source of truth for symbol IDs
     * @param order                chain ordering priority
     */
    public DispatchLoweringTransformer(String apiOwnerInternalName, String namespace, SymbolManifest manifest, int order) {
        this.apiOwnerInternalName = Objects.requireNonNull(apiOwnerInternalName, "apiOwnerInternalName");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.order = order;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public boolean handles(ClassContext context) {
        for (MethodNode method : context.node().methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (isApiCall(insn)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public TransformResult apply(ClassContext context) {
        int rewritten = 0;
        for (MethodNode method : context.node().methods) {
            AbstractInsnNode insn = method.instructions.getFirst();
            while (insn != null) {
                AbstractInsnNode next = insn.getNext();
                if (isApiCall(insn)) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    OptionalInt id = manifest.idOf(namespace + ":" + call.name);
                    if (id.isPresent()) {
                        // Same name/descriptor → identical stack effect, so frames recompute cleanly.
                        InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                                call.name, call.desc, BOOTSTRAP, id.getAsInt());
                        method.instructions.set(insn, indy);
                        rewritten++;
                    }
                    // Unknown symbol: deliberately leave the original call intact rather than guess.
                }
                insn = next;
            }
        }
        return rewritten > 0
                ? new TransformResult.Applied(context.node())
                : new TransformResult.Skipped("no resolvable Aetherium API call sites");
    }

    private boolean isApiCall(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESTATIC
                && apiOwnerInternalName.equals(call.owner);
    }
}
