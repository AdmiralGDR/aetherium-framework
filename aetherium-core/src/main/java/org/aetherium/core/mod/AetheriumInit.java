/*
 * Aetherium Framework — zero-config initialization marker.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.mod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static void m(AetheriumContext)} method as a mod initialization entry — no
 * {@link AetheriumMod} class, no {@code META-INF/services} file, no boilerplate.
 *
 * <p>EN: This is the zero-config entrypoint. A modder annotates one (or several) static methods; at
 * <strong>compile time</strong> the Aetherium annotation processor discovers every {@code @AetheriumInit}
 * method, orders them into a deterministic initialization DAG from their {@link #runBefore()}/
 * {@link #runAfter()} relationships (never magic priority numbers — same model as the hook DAG), and
 * generates a single {@link AetheriumMod} that invokes them <em>by direct static call</em>, in order,
 * and registers it for {@code ServiceLoader}. The result: <strong>no runtime reflection and no classpath
 * scanning</strong> — discovery happens entirely in {@code javac}, and the running game just calls the
 * generated method. The method must be {@code public static} and take a single {@link AetheriumContext}.
 *
 * <pre>{@code
 * public final class MyMod {
 *     @AetheriumInit(runAfter = "registry")     // ordered relative to other @AetheriumInit ids
 *     public static void setup(AetheriumContext ctx) {
 *         ctx.log("MyMod up on tier " + ctx.computeTier());
 *     }
 * }
 * }</pre>
 *
 * <p>RU: Это entrypoint без конфигурации. Мод-разработчик помечает один (или несколько) статических
 * методов; во <strong>время компиляции</strong> процессор Aetherium находит каждый {@code @AetheriumInit}
 * метод, выстраивает их в детерминированный DAG инициализации по отношениям {@link #runBefore()}/
 * {@link #runAfter()} (без магических чисел-приоритетов — как у DAG хуков) и генерирует один
 * {@link AetheriumMod}, вызывающий их <em>прямым статическим вызовом</em> по порядку и регистрирующий его
 * для {@code ServiceLoader}. Итог: <strong>без рантайм-рефлексии и без сканирования classpath</strong> —
 * обнаружение целиком в {@code javac}. Метод обязан быть {@code public static} и принимать один
 * {@link AetheriumContext}.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface AetheriumInit {

    /** Stable id used for ordering; defaults to {@code SimpleClassName.methodName} when blank. */
    String id() default "";

    /** This init must run before the named init ids (edges {@code this → other}); unknown ids are soft. */
    String[] runBefore() default {};

    /** This init must run after the named init ids (edges {@code other → this}); unknown ids are soft. */
    String[] runAfter() default {};
}
