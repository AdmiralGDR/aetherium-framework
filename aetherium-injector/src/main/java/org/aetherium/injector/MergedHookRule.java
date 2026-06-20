/*
 * Aetherium Framework — a DAG-ordered, semantically merged hook group.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import java.util.List;
import java.util.Objects;

/**
 * A group of hooks attached at one {@link InjectionAnchor}, already DAG-sorted, lowered as a single
 * shared-context block with one cancellation epilogue — the unit the ASM Semantic Merger emits.
 *
 * <p>EN: Where {@link InjectionRule} replays free-form cursor ops, a {@code MergedHookRule} is the
 * higher-level, conflict-free construct: at {@code anchor}, build <strong>one</strong>
 * {@link HookContext}, invoke {@code hookIds} in DAG order against it, then evaluate cancellation
 * <strong>once</strong>. This is what makes two hooks that both call {@code ctx.cancel()} compose
 * instead of fight — see {@link BytecodeCursor#insertMergedContextHookBefore(int[], boolean)}.
 *
 * <p>RU: Если {@link InjectionRule} воспроизводит произвольные операции курсора, то
 * {@code MergedHookRule} — высокоуровневая бесконфликтная конструкция: в точке {@code anchor} строится
 * <strong>один</strong> {@link HookContext}, {@code hookIds} вызываются в порядке DAG против него,
 * затем отмена оценивается <strong>однократно</strong>. Именно это заставляет два хука, оба
 * вызывающие {@code ctx.cancel()}, кооперироваться, а не конфликтовать.
 *
 * @param classInternalName JVM internal name of the target class
 * @param methodName        target method name
 * @param methodDesc        target method descriptor
 * @param anchor            where the merged block attaches ({@link InjectionAnchor})
 * @param hookIds           dense context-hook IDs, already in DAG-resolved execution order
 * @param captureArguments  whether to box the method arguments into the shared {@link HookContext}
 */
public record MergedHookRule(String classInternalName, String methodName, String methodDesc,
                             InjectionAnchor anchor, List<Integer> hookIds, boolean captureArguments) {

    public MergedHookRule {
        Objects.requireNonNull(classInternalName, "classInternalName");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(methodDesc, "methodDesc");
        Objects.requireNonNull(anchor, "anchor");
        hookIds = List.copyOf(Objects.requireNonNull(hookIds, "hookIds"));
        if (hookIds.isEmpty()) {
            throw new IllegalArgumentException("a merged hook rule needs at least one hook");
        }
    }

    /** Dense hook IDs as a primitive array (the form {@link BytecodeCursor} lowers). */
    public int[] hookIdArray() {
        int[] out = new int[hookIds.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = hookIds.get(i);
        }
        return out;
    }
}
