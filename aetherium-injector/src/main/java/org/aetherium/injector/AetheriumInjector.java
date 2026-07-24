/*
 * Aetherium Framework — fluent injection registry (the "Mixin killer").
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import org.aetherium.bytecode.BytecodeEngine;
import org.aetherium.bytecode.DiagnosticSink;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The programmatic, fluent registry for deep bytecode injection — Aetherium's strongly-typed
 * replacement for Mixin.
 *
 * <p>EN: Declare injections with a chained, strongly-typed sentence — no annotations, no string
 * matching:
 *
 * <pre>{@code
 * AetheriumInjector injector = AetheriumInjector.create()
 *     .inClass("net/minecraft/world/entity/Entity")
 *         .method("tick", "()V")
 *             .findReturn()
 *             .insertHookBefore(MyMod::asyncTick)   // lowered to O(1) invokedynamic
 *         .commit();
 * injector.installHooks();                          // bind the hook dispatch table once
 * }</pre>
 *
 * <p>Each {@code insertHook*} registers the {@link AetheriumHook} here, assigning it a dense ID; the
 * cursor lowers the call to an {@code invokedynamic} bound to the {@link HookTable} (never a brittle
 * static call). {@link #toTransformer(int)} yields a {@code ClassTransformer} that runs inside the
 * bytecode engine's verification sandbox, and {@link #transform(byte[], ClassLoader, DiagnosticSink)}
 * is a self-contained convenience that builds that sandboxed engine for you — so a bad injection
 * always reverts to the original bytes and never crashes the JVM.
 *
 * <p>RU: Программный текучий реестр для глубокой инъекции байт-кода — строго типизированная замена
 * Mixin. Инъекции объявляются цепочкой без аннотаций и строкового сопоставления. Каждый
 * {@code insertHook*} регистрирует {@link AetheriumHook} здесь, назначая плотный ID; курсор понижает
 * вызов до {@code invokedynamic}, привязанного к {@link HookTable} (а не хрупкого статического
 * вызова). {@link #toTransformer(int)} даёт {@code ClassTransformer}, работающий внутри
 * верификационной песочницы движка, а {@link #transform(byte[], ClassLoader, DiagnosticSink)} —
 * самодостаточное удобство, строящее эту песочницу за вас.
 */
public final class AetheriumInjector {

    private final List<InjectionRule> rules = new ArrayList<>();
    private final List<MergedHookRule> mergedRules = new ArrayList<>();
    private final List<AetheriumHook> hooks = new ArrayList<>();
    private final List<ContextualHook> contextHooks = new ArrayList<>();

    private AetheriumInjector() {
    }

    public static AetheriumInjector create() {
        return new AetheriumInjector();
    }

    /** Begin an injection targeting the class with the given JVM internal name. */
    public ClassInjection inClass(String internalName) {
        return new ClassInjection(this, internalName);
    }

    /** Begin an injection targeting the given type. */
    public ClassInjection inClass(Type type) {
        return new ClassInjection(this, Objects.requireNonNull(type, "type").getInternalName());
    }

    /**
     * Register a void hook and return its <em>process-wide unique</em> dense ID (called by the fluent
     * builder). The ID comes from the global {@link HookTable} immediately, so it is already live and never
     * collides with another injector's IDs — this is the multi-mod coexistence guarantee. The local list is
     * kept only for {@link #hookCount()}.
     */
    int registerHook(AetheriumHook hook) {
        Objects.requireNonNull(hook, "hook");
        hooks.add(hook);
        return HookTable.registerVoid(hook);
    }

    /**
     * Register a context-aware hook and return its process-wide unique dense ID (called by the fluent
     * builder). Registered globally at declaration time; see {@link #registerHook(AetheriumHook)}.
     */
    int registerContextHook(ContextualHook hook) {
        Objects.requireNonNull(hook, "hook");
        contextHooks.add(hook);
        return HookTable.registerContext(hook);
    }

    /** Add a finalized rule (called by {@link MethodInjection#commit()}). */
    void addRule(InjectionRule rule) {
        rules.add(Objects.requireNonNull(rule, "rule"));
    }

    /** Add a finalized merged DAG hook group (called by {@link MergedHookBuilder#commit()}). */
    void addMergedRule(MergedHookRule rule) {
        mergedRules.add(Objects.requireNonNull(rule, "rule"));
    }

    /** Immutable snapshot of the registered (free-form cursor) rules. */
    public List<InjectionRule> rules() {
        return List.copyOf(rules);
    }

    /** Immutable snapshot of the registered merged DAG hook groups. */
    public List<MergedHookRule> mergedRules() {
        return List.copyOf(mergedRules);
    }

    /** Whether any rule (free-form or merged) targets the given class (the loader's transform gate). */
    public boolean hasRuleFor(String classInternalName) {
        for (InjectionRule rule : rules) {
            if (rule.classInternalName().equals(classInternalName)) {
                return true;
            }
        }
        for (MergedHookRule rule : mergedRules) {
            if (rule.classInternalName().equals(classInternalName)) {
                return true;
            }
        }
        return false;
    }

    /** Number of registered void hooks. */
    public int hookCount() {
        return hooks.size();
    }

    /** Number of registered context-aware hooks. */
    public int contextHookCount() {
        return contextHooks.size();
    }

    /**
     * Idempotent. Hooks are registered into the global {@link HookTable} at declaration time
     * ({@link #registerHook}/{@link #registerContextHook}) with process-wide unique IDs, so they are already
     * live before this is called. This method no longer <em>replaces</em> the table — doing so would clobber
     * every other mod's hooks (the multi-mod coexistence bug) — it merely reports the current global hook
     * count. Retained so existing call sites keep compiling; calling it is optional.
     *
     * @return the total number of hooks currently live in the global table (void + context)
     */
    public int installHooks() {
        return HookTable.size() + HookTable.contextSize();
    }

    /** Build a {@code ClassTransformer} that applies these rules inside the engine's sandbox. */
    public InjectorTransformer toTransformer(int order) {
        return new InjectorTransformer(rules(), mergedRules(), order);
    }

    /**
     * Self-contained convenience: transform {@code original} through a one-shot bytecode engine that
     * already provides the verification sandbox. Never throws — returns transformed bytes on success
     * or the original bytes (with a logged {@link org.aetherium.core.Diagnostic}) on any failure.
     */
    public byte[] transform(byte[] original, ClassLoader verifyLoader, DiagnosticSink sink) {
        BytecodeEngine engine = BytecodeEngine.builder()
                .transformer(toTransformer(100))
                .classLoader(verifyLoader)
                .build();
        return engine.transformClass(original, sink);
    }
}
