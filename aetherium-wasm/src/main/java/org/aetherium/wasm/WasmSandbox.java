/*
 * Aetherium Framework — sandboxed GraalWASM execution context (reflective, optional).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.wasm;

import java.lang.reflect.Method;

/**
 * A locked-down GraalWASM execution context for untrusted {@code .wasm} mods — memory & compute only.
 *
 * <p>EN: Wraps a GraalVM polyglot {@code Context} reached entirely by reflection, so the framework
 * never hard-depends on the GraalVM polyglot/wasm jars: where they are present the sandbox runs real
 * WebAssembly; where they are absent {@link #available()} is {@code false} and the module degrades to
 * policy-only mode (the {@link WasmSecurityPolicy} and the {@link StructArenaWasmBridge} still work).
 * The {@code Context} is always built with {@code IOAccess.NONE} and {@code HostAccess.NONE} and with
 * thread/native creation disabled — enforcing {@link WasmSecurityPolicy#strict()}: a mod can compute in
 * its linear memory but can never touch the filesystem, the network, or the JVM.
 *
 * <p>RU: Оборачивает polyglot-{@code Context} GraalVM, достигаемый исключительно рефлексией, поэтому
 * фреймворк не зависит жёстко от jar GraalVM polyglot/wasm: где они есть — песочница исполняет реальный
 * WebAssembly; где их нет — {@link #available()} равно {@code false}, и модуль деградирует в режим
 * только-политики ({@link WasmSecurityPolicy} и {@link StructArenaWasmBridge} продолжают работать).
 * {@code Context} всегда строится с {@code IOAccess.NONE} и {@code HostAccess.NONE} и с отключённым
 * созданием потоков/нативного доступа — обеспечивая {@link WasmSecurityPolicy#strict()}.
 */
public final class WasmSandbox implements AutoCloseable {

    private static final String CONTEXT = "org.graalvm.polyglot.Context";
    private static final String ENGINE = "org.graalvm.polyglot.Engine";

    private final WasmSecurityPolicy policy;
    private final Object context; // org.graalvm.polyglot.Context, or null when GraalWASM is absent

    private WasmSandbox(WasmSecurityPolicy policy, Object context) {
        this.policy = policy;
        this.context = context;
    }

    /**
     * EN: Open a strict sandbox. If GraalWASM is on the classpath a real {@code Context} is created
     * with IO/host access denied; otherwise a policy-only sandbox is returned.
     * RU: Открыть строгую песочницу. При наличии GraalWASM создаётся реальный {@code Context} с
     * запретом IO/host-доступа; иначе возвращается песочница только-политики.
     */
    public static WasmSandbox open() {
        WasmSecurityPolicy policy = WasmSecurityPolicy.strict();
        policy.assertStrict();
        Object ctx = tryCreateContext();
        return new WasmSandbox(policy, ctx);
    }

    /** True if a real GraalWASM {@code Context} backs this sandbox (the "wasm" language is installed). */
    public boolean available() {
        return context != null;
    }

    public WasmSecurityPolicy policy() {
        return policy;
    }

    /**
     * EN: Evaluate a {@code .wasm} binary inside the sandbox, returning the module instance (a polyglot
     * value) — or throwing {@link IllegalStateException} if GraalWASM is unavailable.
     * RU: Выполнить бинарь {@code .wasm} в песочнице, вернув экземпляр модуля (polyglot-значение) —
     * или бросить {@link IllegalStateException}, если GraalWASM недоступен.
     */
    public Object evalModule(byte[] wasmBytes, String name) {
        if (context == null) {
            throw new IllegalStateException("GraalWASM not available; cannot evaluate '" + name + "'");
        }
        try {
            Class<?> sourceClass = Class.forName("org.graalvm.polyglot.Source");
            Class<?> byteSeqClass = Class.forName("org.graalvm.polyglot.io.ByteSequence");
            Object byteSeq = byteSeqClass.getMethod("create", byte[].class).invoke(null, (Object) wasmBytes);
            Object builder = sourceClass
                    .getMethod("newBuilder", String.class, byteSeqClass, String.class)
                    .invoke(null, "wasm", byteSeq, name);
            Object source = builder.getClass().getMethod("build").invoke(builder);
            Method eval = context.getClass().getMethod("eval", sourceClass);
            return eval.invoke(context, source);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("WASM evaluation failed for '" + name + "': " + e.getMessage(), e);
        }
    }

    /** True if the GraalVM polyglot runtime with the "wasm" language is installed on this JVM. */
    public static boolean graalWasmInstalled() {
        return tryCreateContext() != null;
    }

    /** Reflectively build a strict {@code Context}, or null if GraalWASM / the wasm language is absent. */
    private static Object tryCreateContext() {
        try {
            // The "wasm" language must actually be installed, not just the polyglot API on the classpath.
            Class<?> engineClass = Class.forName(ENGINE);
            Object engine = engineClass.getMethod("create").invoke(null);
            Object languages = engineClass.getMethod("getLanguages").invoke(engine);
            boolean hasWasm = (boolean) languages.getClass().getMethod("containsKey", Object.class)
                    .invoke(languages, "wasm");
            if (!hasWasm) {
                return null;
            }

            Class<?> contextClass = Class.forName(CONTEXT);
            Object builder = contextClass.getMethod("newBuilder", String[].class)
                    .invoke(null, (Object) new String[]{"wasm"});

            // Deny filesystem/host/thread/native access — the security contract.
            applyIoNone(builder);
            applyHostNone(builder);
            invokeBoolSetter(builder, "allowCreateThread", false);
            invokeBoolSetter(builder, "allowNativeAccess", false);

            return builder.getClass().getMethod("build").invoke(builder);
        } catch (Throwable absentOrLockedDown) {
            return null;
        }
    }

    private static void applyIoNone(Object builder) throws ReflectiveOperationException {
        Class<?> ioAccess = Class.forName("org.graalvm.polyglot.io.IOAccess");
        Object none = ioAccess.getField("NONE").get(null);
        builder.getClass().getMethod("allowIO", ioAccess).invoke(builder, none);
    }

    private static void applyHostNone(Object builder) throws ReflectiveOperationException {
        Class<?> hostAccess = Class.forName("org.graalvm.polyglot.HostAccess");
        Object none = hostAccess.getField("NONE").get(null);
        builder.getClass().getMethod("allowHostAccess", hostAccess).invoke(builder, none);
    }

    private static void invokeBoolSetter(Object builder, String method, boolean value)
            throws ReflectiveOperationException {
        builder.getClass().getMethod(method, boolean.class).invoke(builder, value);
    }

    @Override
    public void close() {
        if (context == null) {
            return;
        }
        try {
            context.getClass().getMethod("close").invoke(context);
        } catch (ReflectiveOperationException ignored) {
            // best-effort close
        }
    }
}
