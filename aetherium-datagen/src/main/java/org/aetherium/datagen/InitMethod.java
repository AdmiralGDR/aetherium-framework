/*
 * Aetherium Framework — a discovered @AetheriumInit method (compile-time descriptor).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

import java.util.List;
import java.util.Objects;

/**
 * A pure, loader-free descriptor of one {@code @AetheriumInit} method discovered by the processor.
 *
 * <p>EN: Carries everything the {@link InitOrdering} sort and the {@link InitSourceWriter} code generator
 * need — the ordering id, the fully-qualified owner class, the method name, and the
 * {@code runBefore}/{@code runAfter} edges — with no {@code javax.lang.model} or Minecraft types, so it
 * is trivially unit-testable. The generated bootstrap will emit {@code ownerFqn.methodName(context)}.
 * RU: Чистый, не зависящий от загрузчика дескриптор одного метода {@code @AetheriumInit}. Несёт всё, что
 * нужно сортировке {@link InitOrdering} и генератору {@link InitSourceWriter} — id, полное имя класса,
 * имя метода и рёбра {@code runBefore}/{@code runAfter} — без типов {@code javax.lang.model} или Minecraft,
 * поэтому легко тестируется. Генерируемый bootstrap выпустит {@code ownerFqn.methodName(context)}.
 *
 * @param id         ordering id (already defaulted to {@code SimpleClass.method} if the annotation left it blank)
 * @param ownerFqn   fully-qualified owner class name
 * @param methodName the static method name to invoke
 * @param runBefore  ids this init must precede
 * @param runAfter   ids this init must follow
 * @param side       the declared side name — {@code "BOTH"} (default), {@code "SERVER"}, or {@code "CLIENT"};
 *                   kept as a plain string so this descriptor stays free of the core enum ()
 */
public record InitMethod(String id, String ownerFqn, String methodName,
                         List<String> runBefore, List<String> runAfter, String side) {

    public InitMethod {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerFqn, "ownerFqn");
        Objects.requireNonNull(methodName, "methodName");
        runBefore = List.copyOf(Objects.requireNonNull(runBefore, "runBefore"));
        runAfter = List.copyOf(Objects.requireNonNull(runAfter, "runAfter"));
        side = (side == null || side.isBlank()) ? "BOTH" : side;
    }

    /** Backward-compatible constructor: a both-side init (added the {@code side} field). */
    public InitMethod(String id, String ownerFqn, String methodName,
                      List<String> runBefore, List<String> runAfter) {
        this(id, ownerFqn, methodName, runBefore, runAfter, "BOTH");
    }

    /** The direct static call the generated bootstrap emits (no reflection). */
    public String invocation(String contextVar) {
        return ownerFqn + "." + methodName + "(" + contextVar + ");";
    }

    /** Whether this init must be gated by side (anything but {@code BOTH} is routed through a side check). */
    public boolean sideGated() {
        return !"BOTH".equals(side);
    }
}
