/*
 * Aetherium Framework — a compiled injection rule.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A fully-described, replayable injection: a target method and the ordered cursor operations to run.
 *
 * <p>EN: Produced by the fluent {@link MethodInjection} builder. The {@code ops} are recorded as
 * {@link Consumer}s over a live {@link BytecodeCursor}; the {@link InjectorTransformer} creates a real
 * cursor over the matched method at transform time and replays them in order. This recording design
 * lets injections be <em>declared</em> at init time but <em>applied</em> later, when the target class
 * is actually loaded — and it reuses the exact same {@link BytecodeCursor} for both the fluent surface
 * and execution, so there is no parallel operation model to drift.
 *
 * <p>RU: Создаётся текучим построителем {@link MethodInjection}. {@code ops} записаны как
 * {@link Consumer} над живым {@link BytecodeCursor}; {@link InjectorTransformer} создаёт реальный
 * курсор над найденным методом во время трансформации и воспроизводит их по порядку. Такая запись
 * позволяет <em>объявлять</em> инъекции на этапе инициализации, а <em>применять</em> позже, при
 * фактической загрузке целевого класса.
 *
 * @param classInternalName JVM internal name of the target class (e.g. {@code net/minecraft/world/entity/Entity})
 * @param methodName        target method name
 * @param methodDesc        target method descriptor
 * @param ops               ordered cursor operations to replay
 */
public record InjectionRule(String classInternalName, String methodName, String methodDesc,
                            List<Consumer<BytecodeCursor>> ops) {

    public InjectionRule {
        Objects.requireNonNull(classInternalName, "classInternalName");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(methodDesc, "methodDesc");
        ops = List.copyOf(Objects.requireNonNull(ops, "ops"));
    }

    /** Does this rule target the given method node? */
    public boolean matchesMethod(String name, String desc) {
        return methodName.equals(name) && methodDesc.equals(desc);
    }
}
