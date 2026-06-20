/*
 * Aetherium Framework — injection hook invokedynamic bootstrap.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The {@code invokedynamic} bootstrap that links every injected hook call site.
 *
 * <p>EN: A {@link BytecodeCursor} emits an {@code invokedynamic} (descriptor {@code ()V}) whose
 * bootstrap is {@link #bootstrapHook} and whose single static argument is the dense hook ID. On first
 * execution the JVM calls this once: it resolves the {@link AetheriumHook} from the {@link HookTable},
 * binds {@link AetheriumHook#invoke()} to that instance, and returns a {@link ConstantCallSite}. The
 * site is then permanently linked and JIT-inlinable — the {@code O(1)} dispatch path. A missing hook
 * is a hard linkage error surfaced as {@link BootstrapMethodError} (the injector installs the table
 * before any transformed class runs).
 *
 * <p>RU: {@link BytecodeCursor} порождает {@code invokedynamic} (дескриптор {@code ()V}) с bootstrap
 * {@link #bootstrapHook} и единственным статическим аргументом — плотным ID хука. При первом
 * выполнении JVM вызывает это один раз: разрешает {@link AetheriumHook} из {@link HookTable},
 * привязывает {@link AetheriumHook#invoke()} к экземпляру и возвращает {@link ConstantCallSite}.
 * Далее точка связана навсегда и встраиваема JIT — путь диспетчеризации {@code O(1)}. Отсутствующий
 * хук — жёсткая ошибка линковки {@link BootstrapMethodError}.
 */
public final class HookBootstrap {

    private static final MethodHandle INVOKE;
    private static final MethodHandle INVOKE_CONTEXT;

    static {
        try {
            INVOKE = MethodHandles.lookup().findVirtual(
                    AetheriumHook.class, "invoke", MethodType.methodType(void.class));
            INVOKE_CONTEXT = MethodHandles.lookup().findVirtual(
                    ContextualHook.class, "invoke", MethodType.methodType(void.class, HookContext.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private HookBootstrap() {
    }

    /**
     * Link an injected hook call site.
     *
     * @param caller the call site's lookup (unused; required by the BSM contract)
     * @param name   the invoked name (unused; identity comes from {@code hookId})
     * @param type   the call site's {@link MethodType} (always {@code ()V})
     * @param hookId dense ID into the {@link HookTable}
     * @return a {@link ConstantCallSite} bound to the resolved hook
     */
    public static CallSite bootstrapHook(MethodHandles.Lookup caller, String name, MethodType type, int hookId) {
        AetheriumHook hook = HookTable.hook(hookId);
        if (hook == null) {
            throw new BootstrapMethodError("Aetherium: no hook bound for hook id " + hookId);
        }
        return new ConstantCallSite(INVOKE.bindTo(hook).asType(type));
    }

    /**
     * Link an injected context-hook call site (descriptor {@code (LHookContext;)V}).
     *
     * @param caller the call site's lookup (unused; required by the BSM contract)
     * @param name   the invoked name (unused; identity comes from {@code hookId})
     * @param type   the call site's {@link MethodType} (always {@code (HookContext)void})
     * @param hookId dense ID into the {@link HookTable}'s context-hook array
     * @return a {@link ConstantCallSite} bound to the resolved {@link ContextualHook}
     */
    public static CallSite bootstrapContextHook(MethodHandles.Lookup caller, String name, MethodType type, int hookId) {
        ContextualHook hook = HookTable.contextHook(hookId);
        if (hook == null) {
            throw new BootstrapMethodError("Aetherium: no context hook bound for hook id " + hookId);
        }
        return new ConstantCallSite(INVOKE_CONTEXT.bindTo(hook).asType(type));
    }
}
