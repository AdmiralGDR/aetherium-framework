/*
 * Aetherium Framework — Gradle plugin DSL extension.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gradle;

import org.gradle.api.provider.Property;

/**
 * The {@code aetherium { ... }} DSL block — the entire configuration surface for a mod developer.
 *
 * <p>EN: Zero-config by design: {@code version} (the Aetherium framework version to resolve) is the
 * only thing usually set; everything else has sensible conventions. {@code modId} and
 * {@code displayName} feed the <strong>auto-generated host-loader metadata</strong>
 * ({@code META-INF/neoforge.mods.toml} + {@code fabric.mod.json}) so the produced jar is recognized
 * natively by NeoForge/Fabric — fixing the "not a mod" packaging gap. The mod's own version is taken
 * from the standard Gradle {@code project.version}. {@code bundle} toggles JarJar-style embedding of
 * the Aetherium runtime; {@code includeBytecode} adds the bytecode engine.
 *
 * <p>RU: Ноль конфигурации по дизайну: обычно задаётся только {@code version} (версия фреймворка
 * Aetherium); у остального разумные значения по умолчанию. {@code modId} и {@code displayName}
 * наполняют <strong>автогенерируемые метаданные загрузчика</strong>
 * ({@code META-INF/neoforge.mods.toml} + {@code fabric.mod.json}), чтобы итоговый jar распознавался
 * NeoForge/Fabric нативно — это и есть исправление бага «not a mod». Версия самого мода берётся из
 * стандартного {@code project.version}.
 */
public abstract class AetheriumExtension {

    /** Aetherium framework version to resolve from Maven, e.g. {@code "1.0.0-SNAPSHOT"}. */
    public abstract Property<String> getVersion();

    /** Mod id for the generated loader metadata. Defaults to the Gradle project name. */
    public abstract Property<String> getModId();

    /** Human-readable mod name for the generated metadata. Defaults to {@code modId}. */
    public abstract Property<String> getDisplayName();

    /** Embed the Aetherium runtime into the mod jar (default true). */
    public abstract Property<Boolean> getBundle();

    /** Also add the bytecode engine dependency (default false). */
    public abstract Property<Boolean> getIncludeBytecode();

    /** Auto-generate host-loader metadata (neoforge.mods.toml + fabric.mod.json). Default true. */
    public abstract Property<Boolean> getGenerateMetadata();
}
