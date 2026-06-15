/*
 * Aetherium Framework — Gradle plugin.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zero-config Gradle plugin for Aetherium mods.
 *
 * <p>EN: One DSL block — {@code aetherium { version = "1.0.0-SNAPSHOT" }} — configures the whole
 * build: applies {@code java-library}, pins the Java 21 toolchain with {@code --enable-preview}, wires
 * the Maven repositories, adds the Aetherium API dependencies ({@code core} + {@code edge}),
 * <strong>auto-generates host-loader metadata</strong> ({@code META-INF/neoforge.mods.toml} +
 * {@code fabric.mod.json}) so the output jar is recognized natively (the "not a mod" fix), and
 * registers an {@code aetheriumBundle} task that embeds the runtime JarJar-style. No Shadow
 * boilerplate, no metadata hand-authoring.
 *
 * <p>RU: Один DSL-блок настраивает всю сборку: {@code java-library}, тулчейн Java 21 с
 * {@code --enable-preview}, Maven-репозитории, зависимости API ({@code core} + {@code edge}),
 * <strong>автогенерацию метаданных загрузчика</strong> ({@code neoforge.mods.toml} +
 * {@code fabric.mod.json}) для нативного распознавания jar (исправление «not a mod») и задачу
 * {@code aetheriumBundle}, встраивающую рантайм в стиле JarJar.
 */
public class AetheriumGradlePlugin implements Plugin<Project> {

    private static final String GROUP = "org.aetherium";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply("java-library");

        AetheriumExtension ext = project.getExtensions().create("aetherium", AetheriumExtension.class);
        ext.getVersion().convention("1.0.0-SNAPSHOT");
        ext.getModId().convention(project.getName());
        ext.getDisplayName().convention(ext.getModId());
        ext.getBundle().convention(Boolean.TRUE);
        ext.getIncludeBytecode().convention(Boolean.FALSE);
        ext.getGenerateMetadata().convention(Boolean.TRUE);

        // Repositories needed to resolve the framework + its platform deps.
        project.getRepositories().mavenLocal();
        project.getRepositories().mavenCentral();
        project.getRepositories().maven(repo -> {
            repo.setName("NeoForged");
            repo.setUrl(project.uri("https://maven.neoforged.net/releases"));
        });

        project.afterEvaluate(p -> configure(p, ext));
    }

    private void configure(Project p, AetheriumExtension ext) {
        String version = ext.getVersion().get();

        // Aetherium API dependencies — the modder writes against these.
        p.getDependencies().add("implementation", GROUP + ":aetherium-core:" + version);
        p.getDependencies().add("implementation", GROUP + ":aetherium-edge:" + version);
        if (Boolean.TRUE.equals(ext.getIncludeBytecode().getOrElse(Boolean.FALSE))) {
            p.getDependencies().add("implementation", GROUP + ":aetherium-bytecode:" + version);
        }

        // Java 21 toolchain.
        JavaPluginExtension java = p.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(21));

        // The framework's public API uses preview FFM, so the mod must compile/run with it enabled.
        p.getTasks().withType(JavaCompile.class).configureEach(t -> {
            t.getOptions().getRelease().set(21);
            if (!t.getOptions().getCompilerArgs().contains("--enable-preview")) {
                t.getOptions().getCompilerArgs().add("--enable-preview");
            }
        });
        p.getTasks().withType(Test.class).configureEach(t -> t.jvmArgs("--enable-preview"));

        if (Boolean.TRUE.equals(ext.getGenerateMetadata().getOrElse(Boolean.TRUE))) {
            configureMetadata(p, ext);
        }

        if (Boolean.TRUE.equals(ext.getBundle().getOrElse(Boolean.TRUE))) {
            registerBundleTask(p);
        }
    }

    /**
     * Auto-generate {@code META-INF/neoforge.mods.toml} + {@code fabric.mod.json} into a generated
     * resources dir wired into the main source set, so BOTH the normal {@code jar} and
     * {@code aetheriumBundle} contain valid host-loader metadata.
     */
    private void configureMetadata(Project p, AetheriumExtension ext) {
        final File genDir = new File(p.getLayout().getBuildDirectory().getAsFile().get(),
                "generated/aetherium/resources");

        final String modId = sanitizeModId(ext.getModId().getOrElse(p.getName()));
        final String displayName = ext.getDisplayName().getOrElse(modId);
        String v = String.valueOf(p.getVersion());
        final String modVersion = (v == null || v.isBlank() || "unspecified".equals(v)) ? "0.0.0" : v;

        TaskProvider<?> gen = p.getTasks().register("generateAetheriumMetadata", task -> {
            task.setGroup("aetherium");
            task.setDescription("Generates host-loader metadata (neoforge.mods.toml + fabric.mod.json).");
            task.getInputs().property("modId", modId);
            task.getInputs().property("displayName", displayName);
            task.getInputs().property("modVersion", modVersion);
            task.getOutputs().dir(genDir);
            task.doLast(t -> writeMetadata(genDir, modId, displayName, modVersion));
        });

        SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main").getResources().srcDir(genDir);
        p.getTasks().named("processResources").configure(t -> t.dependsOn(gen));
    }

    private static void writeMetadata(File genDir, String modId, String displayName, String version) {
        try {
            File metaInf = new File(genDir, "META-INF");
            Files.createDirectories(metaInf.toPath());
            Files.writeString(new File(metaInf, "neoforge.mods.toml").toPath(),
                    neoforgeModsToml(modId, displayName, version), StandardCharsets.UTF_8);
            Files.writeString(new File(genDir, "fabric.mod.json").toPath(),
                    fabricModJson(modId, displayName, version), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Aetherium loader metadata", e);
        }
    }

    private static String neoforgeModsToml(String modId, String displayName, String version) {
        return ""
                + "# Auto-generated by the Aetherium Gradle plugin. Do not edit.\n"
                + "modLoader = \"javafml\"\n"
                + "loaderVersion = \"[4,)\"\n"
                + "license = \"AGPL-3.0-or-later\"\n\n"
                + "[[mods]]\n"
                + "modId = \"" + modId + "\"\n"
                + "version = \"" + version + "\"\n"
                + "displayName = \"" + displayName + "\"\n"
                + "description = \"An Aetherium-powered mod.\"\n\n"
                + "[[dependencies." + modId + "]]\n"
                + "modId = \"neoforge\"\n"
                + "type = \"required\"\n"
                + "versionRange = \"[21.1.0,)\"\n"
                + "ordering = \"NONE\"\n"
                + "side = \"BOTH\"\n\n"
                + "[[dependencies." + modId + "]]\n"
                + "modId = \"minecraft\"\n"
                + "type = \"required\"\n"
                + "versionRange = \"[1.21.1,1.22)\"\n"
                + "ordering = \"NONE\"\n"
                + "side = \"BOTH\"\n";
    }

    private static String fabricModJson(String modId, String displayName, String version) {
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"id\": \"" + modId + "\",\n"
                + "  \"version\": \"" + version + "\",\n"
                + "  \"name\": \"" + displayName + "\",\n"
                + "  \"license\": \"AGPL-3.0-or-later\",\n"
                + "  \"environment\": \"*\",\n"
                + "  \"depends\": {\n"
                + "    \"fabricloader\": \">=0.15.0\",\n"
                + "    \"minecraft\": \"~1.21.1\"\n"
                + "  }\n"
                + "}\n";
    }

    /**
     * Coerce the project name into a mod id valid on <em>both</em> loaders. NeoForge requires
     * {@code [a-z][a-z0-9_]{1,63}} (hyphens are NOT allowed); Fabric also accepts that shape. So all
     * separators collapse to {@code '_'} rather than {@code '-'} — otherwise NeoForge rejects the mod.
     */
    private static String sanitizeModId(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else if (c == '-' || c == ' ' || c == '.') {
                sb.append('_');
            }
        }
        String cleaned = sb.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (cleaned.isEmpty() || !Character.isLetter(cleaned.charAt(0))) {
            cleaned = "mod" + (cleaned.isEmpty() ? "" : "_" + cleaned);
        }
        return cleaned;
    }

    /** Register a JarJar-style task that bundles the Aetherium runtime into the mod jar. */
    private void registerBundleTask(Project p) {
        p.getTasks().register("aetheriumBundle", Jar.class, jar -> {
            jar.setGroup("aetherium");
            jar.setDescription("Builds a self-contained mod jar with the Aetherium runtime embedded.");
            jar.getArchiveClassifier().set("bundle");
            jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);

            SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
            // main output includes processed resources (incl. the generated metadata) → present in bundle.
            jar.from(sourceSets.getByName("main").getOutput());

            // Embed only the Aetherium artifacts from the runtime classpath (not Minecraft/NeoForge).
            jar.from(p.provider(() -> {
                Configuration runtime = p.getConfigurations().findByName("runtimeClasspath");
                List<Object> embedded = new ArrayList<>();
                if (runtime != null) {
                    for (File f : runtime.getFiles()) {
                        if (f.getName().startsWith("aetherium-")) {
                            embedded.add(f.isDirectory() ? f : p.zipTree(f));
                        }
                    }
                }
                return embedded;
            }));
        });
    }
}
