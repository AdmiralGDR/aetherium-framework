/*
 * Aetherium Framework — injection provider SPI.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

/**
 * The discovery point through which a mod contributes its injections to the loader.
 *
 * <p>EN: A mod registers an implementation via {@code META-INF/services/org.aetherium.injector.InjectionProvider};
 * at load time the loader discovers every provider through {@code ServiceLoader}, hands each one a
 * shared {@link AetheriumInjector} to populate with fluent rules, then installs the combined hook
 * table and wires the resulting transformer into the class-loading pipeline. This keeps the injection
 * API loader-agnostic: a mod declares <em>what</em> to inject without importing a single NeoForge or
 * ModLauncher type.
 *
 * <p>RU: Точка обнаружения, через которую мод поставляет свои инъекции загрузчику. Мод регистрирует
 * реализацию через {@code META-INF/services/org.aetherium.injector.InjectionProvider}; на этапе
 * загрузки загрузчик находит каждого провайдера через {@code ServiceLoader}, передаёт ему общий
 * {@link AetheriumInjector} для наполнения текучими правилами, затем устанавливает объединённую
 * таблицу хуков и подключает полученный трансформер. Это сохраняет API независимым от загрузчика: мод
 * объявляет, <em>что</em> внедрять, не импортируя ни одного типа NeoForge или ModLauncher.
 */
@FunctionalInterface
public interface InjectionProvider {

    /** Add this provider's rules (and hooks) to the shared injector. Must not throw for control flow. */
    void configure(AetheriumInjector injector);
}
