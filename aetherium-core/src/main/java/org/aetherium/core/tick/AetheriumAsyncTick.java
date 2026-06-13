/*
 * Aetherium Framework — async tick annotation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.tick;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a no-argument method as heavy per-tick logic to be offloaded from the main Minecraft thread.
 *
 * <p>EN: The whole point of the DX: a modder writes
 * <pre>{@code  @AetheriumAsyncTick void updatePhysics() { ... } }</pre>
 * and the framework runs it on a Java 21 virtual thread every tick, joined by a Sync Barrier before
 * the 50 ms tick ends (see {@link AetheriumTickEngine}). No threads, no executors, no locks in user
 * code. The annotated method must only touch data it owns (e.g. its slice of a
 * {@link org.aetherium.core.compute.StructArena}); cross-thread write-back belongs in a commit step.
 *
 * <p>RU: Суть DX: моддер пишет {@code @AetheriumAsyncTick void updatePhysics() { ... }}, а фреймворк
 * запускает это на виртуальном потоке Java 21 каждый тик, объединяя Sync-барьером до конца 50-мс
 * тика (см. {@link AetheriumTickEngine}). Ни потоков, ни executor-ов, ни блокировок в коде
 * пользователя. Аннотированный метод должен трогать только свои данные (напр. свой срез
 * {@link org.aetherium.core.compute.StructArena}); запись между потоками — в шаге commit.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AetheriumAsyncTick {

    /** Optional human-readable label for diagnostics. */
    String value() default "";
}
