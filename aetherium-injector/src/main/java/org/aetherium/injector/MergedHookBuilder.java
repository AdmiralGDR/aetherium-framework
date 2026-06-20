/*
 * Aetherium Framework — fluent builder for a DAG-ordered merged hook group.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The zero-boilerplate fluent surface for declaring a group of cooperating hooks at one anchor.
 *
 * <p>EN: Obtained from {@link MethodInjection#at(InjectionAnchor)}. You add named hooks and declare
 * <em>relationships</em> instead of magic priority numbers:
 *
 * <pre>{@code
 * injector.inClass("net/minecraft/world/entity/player/Player")
 *     .method("hurt", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
 *     .at(InjectionAnchor.HEAD)
 *         .captureArguments()
 *         .hook("shield_mod:block", ShieldMod::onHurt).runBefore("armor_mod:absorb")
 *         .hook("armor_mod:absorb", ArmorMod::onHurt)
 *     .commit();
 * }</pre>
 *
 * On {@link #commit()} the group is topologically sorted by {@link HookDag} (stable, cycle-checked),
 * each hook is registered to obtain a dense ID, and a single {@link MergedHookRule} is recorded. At
 * transform time the Semantic Merger lowers the whole group into one shared-{@link HookContext} block
 * with exactly one cancellation epilogue.
 *
 * <p>RU: Получается из {@link MethodInjection#at(InjectionAnchor)}. Вы добавляете именованные хуки и
 * объявляете <em>отношения</em> вместо магических чисел-приоритетов. На {@link #commit()} группа
 * топологически сортируется {@link HookDag} (стабильно, с проверкой циклов), каждый хук регистрируется
 * ради плотного ID, и записывается один {@link MergedHookRule}. На этапе трансформации семантический
 * слиятель понижает всю группу в один блок с общим {@link HookContext} и ровно одним эпилогом отмены.
 */
public final class MergedHookBuilder {

    private final AetheriumInjector injector;
    private final String classInternalName;
    private final String methodName;
    private final String methodDesc;
    private final InjectionAnchor anchor;
    private final List<HookNode> nodes = new ArrayList<>();
    private boolean captureArguments;
    private HookNode current;

    MergedHookBuilder(AetheriumInjector injector, String classInternalName, String methodName,
                      String methodDesc, InjectionAnchor anchor) {
        this.injector = injector;
        this.classInternalName = classInternalName;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.anchor = anchor;
    }

    /** Box the target method's arguments into the shared {@link HookContext} (opt-in; see perf notes). */
    public MergedHookBuilder captureArguments() {
        this.captureArguments = true;
        return this;
    }

    /** Add a named, context-aware hook to the group. Subsequent {@code runBefore}/{@code runAfter} bind to it. */
    public MergedHookBuilder hook(String id, ContextualHook hook) {
        current = new HookNode(id, hook);
        nodes.add(current);
        return this;
    }

    /** Constrain the most recently added hook to run before the given hook id(s). */
    public MergedHookBuilder runBefore(String... ids) {
        requireCurrent("runBefore");
        current.addRunBefore(ids);
        return this;
    }

    /** Constrain the most recently added hook to run after the given hook id(s). */
    public MergedHookBuilder runAfter(String... ids) {
        requireCurrent("runAfter");
        current.addRunAfter(ids);
        return this;
    }

    /**
     * DAG-sort the group, register each hook, record the merged rule, and return to the injector.
     *
     * @throws HookCycleException if the ordering constraints are cyclic
     */
    public AetheriumInjector commit() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("merged hook group at " + anchor + " has no hooks");
        }
        List<HookNode> ordered = HookDag.sort(nodes);
        List<Integer> hookIds = new ArrayList<>(ordered.size());
        for (HookNode node : ordered) {
            hookIds.add(injector.registerContextHook(node.hook()));
        }
        injector.addMergedRule(new MergedHookRule(
                classInternalName, methodName, methodDesc, anchor, hookIds, captureArguments));
        return injector;
    }

    /** The DAG-resolved execution order of hook ids (for diagnostics/self-test, before commit). */
    public List<String> resolvedOrder() {
        return HookDag.sort(nodes).stream().map(HookNode::id).toList();
    }

    private void requireCurrent(String op) {
        if (current == null) {
            throw new IllegalStateException(op + "(...) must follow a hook(...) declaration");
        }
        Objects.requireNonNull(current);
    }
}
