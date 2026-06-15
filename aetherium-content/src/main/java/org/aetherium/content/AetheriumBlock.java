/*
 * Aetherium Framework — declarative block annotation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Minecraft block in <em>one annotation</em> — zero boilerplate, zero JSON.
 *
 * <p>EN: Annotate any class with {@code @AetheriumBlock} and the framework does 100% of the work that
 * normally takes a {@code DeferredRegister}, a hand-written {@code BlockItem}, and four-plus JSON
 * files: at build time the annotation processor generates the block model ({@code cube_all}), item
 * model, blockstate, loot table, and lang entry; at load time the loader registers the block <em>and
 * its {@code BlockItem}</em> to the vanilla registries. The modder writes no registry code and no
 * JSON. Example:
 *
 * <pre>{@code
 * @AetheriumBlock(name = "steel_block", hardness = 5.0f, resistance = 6.0f, requiresTool = true)
 * public final class AetheriumSteelBlock {}
 * }</pre>
 *
 * <p>RU: Пометьте любой класс {@code @AetheriumBlock}, и фреймворк делает 100% работы, которая обычно
 * требует {@code DeferredRegister}, рукописного {@code BlockItem} и четырёх с лишним JSON-файлов: на
 * этапе сборки процессор генерирует модель блока ({@code cube_all}), модель предмета, blockstate,
 * loot-таблицу и запись lang; на этапе загрузки загрузчик регистрирует блок <em>и его
 * {@code BlockItem}</em> в ванильных реестрах. Модельер не пишет ни кода реестра, ни JSON.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AetheriumBlock {

    /** Registry path, e.g. {@code "steel_block"} (lowercase {@code [a-z0-9_]}). */
    String name();

    /** Owning mod id (registry namespace). Blank → resolved from the {@code aetherium.modId} processor option, else {@code "aetherium"}. */
    String modId() default "";

    /** Destroy time / mining hardness. */
    float hardness() default 1.0f;

    /** Blast resistance. Negative → mirror {@link #hardness()}. */
    float resistance() default -1.0f;

    /** Whether the correct tool is required for the block to drop anything. */
    boolean requiresTool() default false;

    /** Whether the block drops itself when broken (generates a self-drop loot table). */
    boolean dropSelf() default true;

    /** Human-readable label for {@code lang/en_us.json}. Blank → auto-derived from {@link #name()}. */
    String displayName() default "";
}
