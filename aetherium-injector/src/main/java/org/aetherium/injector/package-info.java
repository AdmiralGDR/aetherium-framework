/*
 * Aetherium Framework — injector package overview.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * The programmatic, fluent bytecode-injection API — Aetherium's strongly-typed Mixin replacement.
 *
 * <p>EN: {@link org.aetherium.injector.AetheriumInjector} is the fluent registry;
 * {@link org.aetherium.injector.BytecodeCursor} is the navigable, typed cursor over a method's
 * instructions (no string-based {@code @At} matching). Hooks ({@link org.aetherium.injector.AetheriumHook})
 * are lowered to {@code invokedynamic} via {@link org.aetherium.injector.HookBootstrap} /
 * {@link org.aetherium.injector.HookTable} — the {@code O(1)} dispatch path. Every injection runs
 * inside the {@code aetherium-bytecode} verification sandbox, so a bad edit reverts to the original
 * bytes with a structured {@code Diagnostic} and never crashes the JVM. Mods contribute rules through
 * {@link org.aetherium.injector.InjectionProvider}; {@link org.aetherium.injector.InjectorSelfTest}
 * proves the whole path headlessly.
 *
 * <p>RU: {@link org.aetherium.injector.AetheriumInjector} — текучий реестр;
 * {@link org.aetherium.injector.BytecodeCursor} — навигируемый типобезопасный курсор по инструкциям
 * метода (без строкового {@code @At}). Хуки понижаются до {@code invokedynamic} через
 * {@link org.aetherium.injector.HookBootstrap} / {@link org.aetherium.injector.HookTable} — путь
 * {@code O(1)}. Каждая инъекция выполняется внутри верификационной песочницы {@code aetherium-bytecode},
 * поэтому плохая правка откатывается к исходным байтам со структурированным {@code Diagnostic} и
 * никогда не роняет JVM.
 */
package org.aetherium.injector;
