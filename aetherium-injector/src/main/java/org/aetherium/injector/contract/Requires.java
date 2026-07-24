/*
 * Aetherium Framework — a hook precondition on its inputs (Consistency contract).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a {@link Constraint} a hook method requires of an integral parameter — a precondition contract.
 *
 * <p>EN: The companion of {@link Ensures}. It documents (and lets tooling enforce) the assumption a hook
 * makes about one of its arguments, e.g. {@code @Requires(param = 0, value = NON_NEGATIVE)} for a hook
 * that indexes an array with {@code arg0}. The CLI reports declared preconditions alongside the return
 * postcondition so a reviewer sees the full contract of a hook at a glance. Retained at runtime.
 *
 * <p>RU: Компаньон {@link Ensures}. Документирует (и позволяет инструментам проверять) предположение хука
 * об одном из аргументов, напр. {@code @Requires(param = 0, value = NON_NEGATIVE)} для хука, индексирующего
 * массив по {@code arg0}. CLI показывает объявленные предусловия рядом с постусловием возврата. Хранится
 * во время выполнения.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Requires {

    /** The zero-based index of the parameter the constraint applies to. */
    int param() default 0;

    /** The constraint the parameter must satisfy on entry. */
    Constraint value();
}
