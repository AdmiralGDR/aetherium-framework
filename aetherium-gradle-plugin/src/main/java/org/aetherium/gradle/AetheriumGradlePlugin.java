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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
        ext.getUniversal().convention(Boolean.FALSE);
        ext.getEmbedLoader().convention(Boolean.TRUE);
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
        final String modId = sanitizeModId(ext.getModId().getOrElse(p.getName()));

        // Aetherium API dependencies — the modder writes against these.
        p.getDependencies().add("implementation", GROUP + ":aetherium-core:" + version);
        p.getDependencies().add("implementation", GROUP + ":aetherium-edge:" + version);
        if (Boolean.TRUE.equals(ext.getIncludeBytecode().getOrElse(Boolean.FALSE))) {
            p.getDependencies().add("implementation", GROUP + ":aetherium-bytecode:" + version);
        }

        // Zero-config declarative content: the modder gets @AetheriumBlock/@AetheriumItem plus the
        // build-time asset generator with no extra wiring. The annotation processor emits the resource
        // JSON + the runtime content index straight into the compiled output (and thus the jar).
        p.getDependencies().add("implementation", GROUP + ":aetherium-content:" + version);
        p.getDependencies().add("annotationProcessor", GROUP + ":aetherium-content:" + version);

        // Java 21 toolchain.
        JavaPluginExtension java = p.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(21));

        // The framework's public API uses preview FFM, so the mod must compile/run with it enabled.
        p.getTasks().withType(JavaCompile.class).configureEach(t -> {
            t.getOptions().getRelease().set(21);
            if (!t.getOptions().getCompilerArgs().contains("--enable-preview")) {
                t.getOptions().getCompilerArgs().add("--enable-preview");
            }
            // Default content namespace = the mod id, so @AetheriumBlock(name=…) needs no modId.
            String modIdArg = "-Aaetherium.modId=" + modId;
            if (t.getOptions().getCompilerArgs().stream().noneMatch(a -> a.startsWith("-Aaetherium.modId="))) {
                t.getOptions().getCompilerArgs().add(modIdArg);
            }
        });
        p.getTasks().withType(Test.class).configureEach(t -> t.jvmArgs("--enable-preview"));

        if (Boolean.TRUE.equals(ext.getGenerateMetadata().getOrElse(Boolean.TRUE))) {
            configureMetadata(p, ext);
        }

        if (Boolean.TRUE.equals(ext.getBundle().getOrElse(Boolean.TRUE))) {
            registerBundleTask(p);
        }
        if (Boolean.TRUE.equals(ext.getUniversal().getOrElse(Boolean.FALSE))) {
            boolean embedLoader = Boolean.TRUE.equals(ext.getEmbedLoader().getOrElse(Boolean.TRUE));
            if (embedLoader) {
                // Resolve the loader so the universal jar is fully self-contained; only aetherium-*
                // artifacts are actually embedded (Minecraft/NeoForge stay external).
                p.getDependencies().add("runtimeOnly", GROUP + ":aetherium-loader:" + version);
            }
            registerUniversalTask(p, sanitizeModId(ext.getModId().getOrElse(p.getName())), version);
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

    static String neoforgeModsToml(String modId, String displayName, String version) {
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

    static String fabricModJson(String modId, String displayName, String version) {
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
    static String sanitizeModId(String raw) {
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

    /**
     * Register the legacy flat-bundle task. Because this <em>flattens</em> the framework into the mod
     * namespace, it explicitly <strong>merges</strong> {@code META-INF/services} files (concatenated, never
     * overwritten) so {@code ServiceLoader} manifests survive — the QA "services destroyed on merge" fix.
     * The universal jar (Jar-in-Jar) is the recommended path; the bundle remains for the library-mod case.
     */
    private void registerBundleTask(Project p) {
        final File svcDir = new File(p.getLayout().getBuildDirectory().getAsFile().get(),
                "generated/aetherium/bundle-services");
        TaskProvider<?> mergeServices = p.getTasks().register("mergeAetheriumServiceFiles", t -> {
            t.setGroup("aetherium");
            t.setDescription("Concatenates META-INF/services entries from the mod + embedded Aetherium jars.");
            t.getOutputs().dir(svcDir);
            t.doLast(x -> writeMergedServiceFiles(p, svcDir));
        });

        p.getTasks().register("aetheriumBundle", Jar.class, jar -> {
            jar.setGroup("aetherium");
            jar.setDescription("Builds a self-contained mod jar with the Aetherium runtime embedded (flat).");
            jar.getArchiveClassifier().set("bundle");
            jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            jar.dependsOn(mergeServices);

            SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
            // Mod classes + embedded framework classes, EXCEPT service files (merged authoritatively below).
            jar.from(sourceSets.getByName("main").getOutput(), s -> s.exclude("META-INF/services/**"));
            jar.from(p.provider(() -> flattenedAetheriumTrees(p)), s -> s.exclude("META-INF/services/**"));
            jar.from(svcDir); // the concatenated service files
        });
    }

    /**
     * Register the Universal Jar task using the <strong>NeoForge Jar-in-Jar</strong> standard: the
     * framework is embedded as whole nested jars under {@code META-INF/jarjar/} and declared in
     * {@code metadata.json}, <em>not</em> shadowed into the mod's namespace. NeoForge extracts and loads
     * each {@code group:artifact} <strong>once globally</strong>, so two Aetherium mods no longer spin up
     * colliding engines / duplicate {@code O(1)} dispatch tables — the QA "fat-jar hell" fix.
     */
    private void registerUniversalTask(Project p, String modId, String version) {
        final File jijDir = new File(p.getLayout().getBuildDirectory().getAsFile().get(),
                "generated/aetherium/jarjar");
        TaskProvider<?> jijMeta = p.getTasks().register("generateAetheriumJijMetadata", t -> {
            t.setGroup("aetherium");
            t.setDescription("Writes META-INF/jarjar/metadata.json describing the embedded framework jars.");
            t.getOutputs().dir(jijDir);
            t.doLast(x -> writeJijMetadata(p, jijDir));
        });

        p.getTasks().register("aetheriumUniversalJar", Jar.class, jar -> {
            jar.setGroup("aetherium");
            jar.setDescription("Builds aetherium-universal.jar via NeoForge Jar-in-Jar (single shared engine).");
            jar.getArchiveClassifier().set("universal");
            jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            jar.dependsOn(jijMeta);
            jar.getManifest().getAttributes().putAll(universalManifest(modId));

            SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
            // ONLY the mod's own classes + metadata at the jar root — the framework is NOT shadowed in.
            jar.from(sourceSets.getByName("main").getOutput());
            // Each framework jar embedded WHOLE under META-INF/jarjar (loaded once, globally, by NeoForge).
            jar.from(p.provider(() -> aetheriumRuntimeJars(p)), s -> s.into("META-INF/jarjar"));
            jar.from(jijDir); // the JiJ metadata.json listing those nested jars
        });
    }

    /** Trees of {@code aetherium-*} artifacts for the flat bundle (unzipped into the jar root). */
    private static List<Object> flattenedAetheriumTrees(Project p) {
        List<Object> embedded = new ArrayList<>();
        for (File f : aetheriumRuntimeJars(p)) {
            embedded.add(f.isDirectory() ? f : p.zipTree(f));
        }
        return embedded;
    }

    /** The {@code aetherium-*} jar FILES from the runtime classpath (for Jar-in-Jar embedding). */
    private static List<File> aetheriumRuntimeJars(Project p) {
        Configuration runtime = p.getConfigurations().findByName("runtimeClasspath");
        List<File> jars = new ArrayList<>();
        if (runtime != null) {
            for (File f : runtime.getFiles()) {
                if (f.getName().startsWith("aetherium-")) {
                    jars.add(f);
                }
            }
        }
        return jars;
    }

    private static void writeJijMetadata(Project p, File jijDir) {
        try {
            File metaInf = new File(jijDir, "META-INF/jarjar");
            Files.createDirectories(metaInf.toPath());
            List<String> names = new ArrayList<>();
            for (File f : aetheriumRuntimeJars(p)) {
                if (f.getName().endsWith(".jar")) {
                    names.add(f.getName());
                }
            }
            Files.writeString(new File(metaInf, "metadata.json").toPath(),
                    jijMetadataJson(names), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Jar-in-Jar metadata", e);
        }
    }

    /** Build the NeoForge Jar-in-Jar {@code metadata.json} from the embedded jar file names (pure). */
    static String jijMetadataJson(List<String> jarFileNames) {
        StringBuilder sb = new StringBuilder("{\n  \"jars\": [\n");
        for (int i = 0; i < jarFileNames.size(); i++) {
            String name = jarFileNames.get(i);
            String[] av = parseArtifactVersion(name);
            sb.append("    {\n")
                    .append("      \"identifier\": { \"group\": \"").append(GROUP)
                    .append("\", \"artifact\": \"").append(av[0]).append("\" },\n")
                    .append("      \"version\": { \"range\": \"[").append(av[1]).append(",)\", ")
                    .append("\"artifactVersion\": \"").append(av[1]).append("\" },\n")
                    .append("      \"path\": \"META-INF/jarjar/").append(name).append("\",\n")
                    .append("      \"isObfuscated\": false\n")
                    .append("    }").append(i < jarFileNames.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    /** Parse {@code "aetherium-core-1.0.0-SNAPSHOT.jar"} → {@code ["aetherium-core","1.0.0-SNAPSHOT"]}. */
    static String[] parseArtifactVersion(String jarFileName) {
        String base = jarFileName.endsWith(".jar")
                ? jarFileName.substring(0, jarFileName.length() - 4) : jarFileName;
        for (int i = 0; i < base.length() - 1; i++) {
            if (base.charAt(i) == '-' && Character.isDigit(base.charAt(i + 1))) {
                return new String[]{base.substring(0, i), base.substring(i + 1)};
            }
        }
        return new String[]{base, "0.0.0"};
    }

    /** Collect + concatenate {@code META-INF/services} from the mod output and embedded aetherium jars. */
    private static void writeMergedServiceFiles(Project p, File svcDir) {
        try {
            List<Map<String, List<String>>> sources = new ArrayList<>();
            SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
            for (File dir : sourceSets.getByName("main").getOutput().getFiles()) {
                sources.add(readServicesFromDir(dir));
            }
            for (File f : aetheriumRuntimeJars(p)) {
                if (f.getName().endsWith(".jar")) {
                    sources.add(readServicesFromJar(f));
                }
            }
            Map<String, String> merged = ServiceFileMerger.mergeAll(sources);
            Files.createDirectories(svcDir.toPath());
            if (merged.isEmpty()) {
                return;
            }
            File out = new File(svcDir, "META-INF/services");
            Files.createDirectories(out.toPath());
            for (Map.Entry<String, String> e : merged.entrySet()) {
                Files.writeString(new File(out, e.getKey()).toPath(), e.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to merge service files", e);
        }
    }

    private static Map<String, List<String>> readServicesFromDir(File root) throws IOException {
        Map<String, List<String>> map = new LinkedHashMap<>();
        File services = new File(root, "META-INF/services");
        File[] files = services.listFiles(File::isFile);
        if (files != null) {
            for (File f : files) {
                map.put(f.getName(), Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static Map<String, List<String>> readServicesFromJar(File jarFile) throws IOException {
        Map<String, List<String>> map = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarFile)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String n = entry.getName();
                if (entry.isDirectory() || !n.startsWith("META-INF/services/") || n.equals("META-INF/services/")) {
                    continue;
                }
                String service = n.substring("META-INF/services/".length());
                if (service.contains("/")) {
                    continue;
                }
                List<String> lines = new ArrayList<>();
                try (InputStream in = jar.getInputStream(entry);
                     BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        lines.add(line);
                    }
                }
                map.put(service, lines);
            }
        }
        return map;
    }

    /** Manifest attributes stamped onto the universal jar so it is self-describing. */
    static Map<String, String> universalManifest(String modId) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("Aetherium-Universal", "true");
        attrs.put("Aetherium-Loaders", "neoforge,fabric");
        attrs.put("Aetherium-Packaging", "jar-in-jar");
        attrs.put("Aetherium-Mod-Id", modId);
        return attrs;
    }
}
