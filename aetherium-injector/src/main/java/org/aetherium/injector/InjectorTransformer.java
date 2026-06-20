/*
 * Aetherium Framework — injection transformer (ClassTransformer bridge).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.TransformResult;
import org.aetherium.core.Diagnostic;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Applies registered {@link InjectionRule}s to matching classes — the bridge between the fluent
 * injector and the {@code aetherium-bytecode} engine.
 *
 * <p>EN: This is a plain {@link ClassTransformer}, so it runs <em>inside</em> the engine's
 * verification sandbox: the engine recomputes frames, runs {@code CheckClassAdapter}/dataflow
 * verification, and — on any {@code VerifyError}, malformed result, or thrown exception — reverts the
 * class to its <strong>original</strong> bytes and logs a structured {@link Diagnostic}. The
 * transformer adds its own first line of containment: a failed {@link BytecodeCursor} navigation
 * ({@link CursorException}) or a missing target method is turned into a {@link TransformResult.Failed}
 * with a structured diagnostic, which makes the engine revert. The JVM is never allowed to crash.
 *
 * <p>RU: Это обычный {@link ClassTransformer}, поэтому он работает <em>внутри</em> верификационной
 * песочницы движка: движок пересчитывает фреймы, выполняет проверку {@code CheckClassAdapter}/потоков
 * данных и при любом {@code VerifyError}, неверном результате или исключении откатывает класс к
 * <strong>исходным</strong> байтам и логирует структурированный {@link Diagnostic}. Трансформер
 * добавляет свою первую линию локализации: сбой навигации {@link BytecodeCursor}
 * ({@link CursorException}) или отсутствие целевого метода превращаются в
 * {@link TransformResult.Failed} со структурированной диагностикой, что заставляет движок откатиться.
 */
public final class InjectorTransformer implements ClassTransformer {

    private final List<InjectionRule> rules;
    private final List<MergedHookRule> mergedRules;
    private final int order;

    public InjectorTransformer(List<InjectionRule> rules, int order) {
        this(rules, List.of(), order);
    }

    public InjectorTransformer(List<InjectionRule> rules, List<MergedHookRule> mergedRules, int order) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        this.mergedRules = List.copyOf(Objects.requireNonNull(mergedRules, "mergedRules"));
        this.order = order;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public boolean handles(ClassContext context) {
        String internal = context.internalName();
        for (InjectionRule rule : rules) {
            if (rule.classInternalName().equals(internal)) {
                return true;
            }
        }
        for (MergedHookRule rule : mergedRules) {
            if (rule.classInternalName().equals(internal)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TransformResult apply(ClassContext context) {
        String internal = context.internalName();
        int applied = 0;
        for (InjectionRule rule : rules) {
            if (!rule.classInternalName().equals(internal)) {
                continue;
            }
            MethodNode target = findMethod(context, rule.methodName(), rule.methodDesc());
            if (target == null) {
                return new TransformResult.Failed(Diagnostic.error(
                        "AE-INJECT-404",
                        "Injection target " + internal + "#" + rule.methodName() + rule.methodDesc()
                                + " not found; reverting class."));
            }
            try {
                BytecodeCursor cursor = new BytecodeCursor(target);
                for (Consumer<BytecodeCursor> op : rule.ops()) {
                    op.accept(cursor);
                }
                applied++;
            } catch (CursorException cursorFailure) {
                // Expected, contained failure: hand the engine a structured diagnostic so it reverts
                // this class to the original vanilla bytes (no partial edit escapes).
                return new TransformResult.Failed(Diagnostic.error(
                        "AE-INJECT-CURSOR",
                        "Injection into " + internal + "#" + rule.methodName() + rule.methodDesc()
                                + " failed: " + cursorFailure.getMessage() + "; reverting class."));
            }
        }

        // The Semantic Merger: each merged rule lowers a whole DAG-ordered hook group as one
        // shared-context block with a single cancellation epilogue.
        for (MergedHookRule rule : mergedRules) {
            if (!rule.classInternalName().equals(internal)) {
                continue;
            }
            MethodNode target = findMethod(context, rule.methodName(), rule.methodDesc());
            if (target == null) {
                return new TransformResult.Failed(Diagnostic.error(
                        "AE-INJECT-404",
                        "Merged injection target " + internal + "#" + rule.methodName() + rule.methodDesc()
                                + " not found; reverting class."));
            }
            try {
                BytecodeCursor cursor = new BytecodeCursor(target);
                rule.anchor().navigate(cursor);
                cursor.insertMergedContextHookBefore(rule.hookIdArray(), rule.captureArguments());
                applied++;
            } catch (CursorException cursorFailure) {
                return new TransformResult.Failed(Diagnostic.error(
                        "AE-INJECT-CURSOR",
                        "Merged injection into " + internal + "#" + rule.methodName() + rule.methodDesc()
                                + " failed: " + cursorFailure.getMessage() + "; reverting class."));
            }
        }
        return applied > 0
                ? new TransformResult.Applied(context.node())
                : new TransformResult.Skipped("no injection rule matched a method in " + internal);
    }

    @Override
    public String id() {
        return "AetheriumInjector(" + rules.size() + " rule(s), " + mergedRules.size() + " merged group(s))";
    }

    private static MethodNode findMethod(ClassContext context, String name, String desc) {
        for (MethodNode method : context.node().methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }
}
