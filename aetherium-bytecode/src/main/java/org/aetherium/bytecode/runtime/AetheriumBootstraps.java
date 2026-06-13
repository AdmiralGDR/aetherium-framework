package org.aetherium.bytecode.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The {@code invokedynamic} bootstrap that links lowered Aetherium API call sites.
 *
 * <p>EN: Every call site rewritten by the {@code DispatchLoweringTransformer} targets
 * {@link #bootstrapDispatch}. It resolves the {@link MethodHandle} for the given dense
 * {@code symbolId} from the {@link DispatchTable} and returns a {@link ConstantCallSite} — so the
 * JVM links the site <em>exactly once</em>, after which the JIT inlines through it as a direct
 * call. That one-time linkage is the mechanism behind the {@code O(1)} runtime guarantee
 * ({@code ARCHITECTURE.md} ).
 *
 * <p>RU: Каждая точка вызова, переписанная {@code DispatchLoweringTransformer}, целится в
 * {@link #bootstrapDispatch}. Он разрешает {@link MethodHandle} для заданного плотного
 * {@code symbolId} из {@link DispatchTable} и возвращает {@link ConstantCallSite} — поэтому JVM
 * линкует точку <em>ровно один раз</em>, после чего JIT встраивает её как прямой вызов.
 * Эта однократная линковка и есть механизм гарантии {@code O(1)}.
 */
public final class AetheriumBootstraps {

    private AetheriumBootstraps() {
    }

    /**
     * Link a dispatch call site.
     *
     * @param caller   the lookup of the call site's class (unused; required by the BSM contract)
     * @param name     the invoked name (unused; symbol identity comes from {@code symbolId})
     * @param type     the call site's {@link MethodType}
     * @param symbolId dense ID into the {@link DispatchTable}
     * @return a {@link ConstantCallSite} bound to the resolved handle
     */
    public static CallSite bootstrapDispatch(MethodHandles.Lookup caller, String name, MethodType type, int symbolId) {
        MethodHandle target = DispatchTable.handle(symbolId);
        if (target == null) {
            // A missing handle is a hard linkage error; surfaced as BootstrapMethodError so the
            // JVM's own diagnostics carry it. The load-phase fallback machinery should have
            // ensured the table is fully populated before any transformed class runs.
            throw new BootstrapMethodError("Aetherium: no dispatch handle bound for symbol id " + symbolId);
        }
        return new ConstantCallSite(target.asType(type));
    }
}
