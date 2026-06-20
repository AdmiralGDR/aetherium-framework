/*
 * Aetherium Framework — hook DAG cycle error.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

/**
 * Thrown when {@link HookDag} cannot produce a topological order because the
 * {@code runBefore}/{@code runAfter} constraints form a cycle.
 *
 * <p>EN: A contained, structured failure — the fluent {@code commit()} surfaces it at declaration
 * time; the loader wraps each {@link InjectionProvider} so one mod's impossible ordering never aborts
 * the launch. It is a {@link RuntimeException} so it also trips the transformer's revert path.
 *
 * <p>RU: Локализованный структурированный сбой — текучий {@code commit()} выдаёт его на этапе
 * объявления; загрузчик оборачивает каждый {@link InjectionProvider}, чтобы невозможный порядок одного
 * мода не срывал запуск.
 */
public final class HookCycleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HookCycleException(String message) {
        super(message);
    }
}
