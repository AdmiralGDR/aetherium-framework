/*
 * Aetherium Framework — declarative item annotation.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Minecraft item in <em>one annotation</em> — zero boilerplate, zero JSON.
 *
 * <p>EN: Annotate a class with {@code @AetheriumItem}; the processor generates the
 * {@code item/generated} model and the lang entry, and the loader registers an {@code Item} to the
 * vanilla item registry. No {@code DeferredRegister}, no JSON. Example:
 *
 * <pre>{@code
 * @AetheriumItem(name = "steel_ingot", maxStackSize = 64)
 * public final class SteelIngot {}
 * }</pre>
 *
 * <p>RU: Пометьте класс {@code @AetheriumItem}; процессор генерирует модель {@code item/generated} и
 * запись lang, а загрузчик регистрирует {@code Item} в ванильном реестре предметов. Без
 * {@code DeferredRegister}, без JSON.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AetheriumItem {

    /** Registry path, e.g. {@code "steel_ingot"}. */
    String name();

    /** Owning mod id (registry namespace). Blank → resolved from the {@code aetherium.modId} processor option, else {@code "aetherium"}. */
    String modId() default "";

    /** Maximum stack size (1–99). */
    int maxStackSize() default 64;

    /** Human-readable label for {@code lang/en_us.json}. Blank → auto-derived from {@link #name()}. */
    String displayName() default "";

    /**
     * EN: Optional behavior class (e.g. an item-use handler the loader binds). Default {@link Object}
     * means "plain item, no behavior". Recorded in the behavior index for the loader to wire.
     * RU: Необязательный класс поведения (напр. обработчик использования предмета). По умолчанию
     * {@link Object} — «обычный предмет». Записывается в индекс поведений для подключения загрузчиком.
     */
    Class<?> behavior() default Object.class;
}
