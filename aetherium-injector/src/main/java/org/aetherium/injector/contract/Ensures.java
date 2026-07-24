/*
 * Aetherium Framework — a hook postcondition on its return value (Consistency contract).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the {@link Constraint} a hook method's <strong>return value</strong> must always satisfy.
 *
 * <p>EN: A postcondition contract the CLI's {@code ContractAnalyzer} verifies statically via abstract
 * (sign) interpretation of the compiled method — no execution needed. For example a hook that computes a
 * block's light level should never hand the engine a negative number:
 *
 * <pre>{@code
 * @Ensures(Constraint.NON_NEGATIVE)
 * public static int lightLevel(HookContext ctx) { ... }
 * }</pre>
 *
 * <p>If the analyzer can prove a return path yields a violating sign, it warns before the game runs;
 * unprovable paths are reported as unverified rather than silently accepted. Retained at runtime so the
 * analyzer (and future runtime assertions) can read it straight from the bytecode.
 *
 * <p>RU: Постусловие-контракт, который {@code ContractAnalyzer} проверяет статически через абстрактную
 * (знаковую) интерпретацию скомпилированного метода — без запуска. Если анализатор может доказать, что
 * путь возврата даёт нарушающий знак, он предупреждает ещё до запуска игры; недоказуемые пути помечаются
 * как непроверенные, а не молча принимаются.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Ensures {

    /** The constraint the method's return value must satisfy. */
    Constraint value();
}
