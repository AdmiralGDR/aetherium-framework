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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        // Default FALSE: aetherium-loader depends on Minecraft/NeoForge (via ModDevGradle) and is NOT a
        // publishable Maven artifact, so `runtimeOnly aetherium-loader` cannot resolve — embedLoader=true
        // silently failed for every consumer (feedback ). Ship the loader as a separate drop-in mod, or
        // opt in explicitly if you have published a loader coordinate yourself.
        ext.getEmbedLoader().convention(Boolean.FALSE);
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

        // Generated content assets (from the annotation processor) can collide with the same paths arriving
        // as plain resources, so the default `jar` dies on "entry ... is a duplicate" on a consumer's first
        // build. Set EXCLUDE on `jar` too (aetheriumBundle/aetheriumUniversalJar already do). — 
        p.getTasks().named("jar", Jar.class).configure(jar -> jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE));

        // EXCLUDE turned a build crash into SILENT lang loss — when the AP writes
        // assets/<id>/lang/en_us.json into the classes output and the author ships one in resources, EXCLUDE
        // keeps whichever the jar visits first and drops the other with no warning. Merge them by key union
        // instead (a lang file is a flat {"key":"value"} map), so both contributors survive; warn on a real
        // key conflict. Every packaging task (jar/bundle/universal) depends on this.
        registerLangMerge(p);

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
            // tell the content processor where the mod's resources live so it can reliably detect a
            // missing block texture (the processor's CLASS_OUTPUT never contains src/main/resources — they only
            // merge into the jar), and warn naming the exact path instead of shipping a checkerboard.
            if (t.getOptions().getCompilerArgs().stream().noneMatch(a -> a.startsWith("-Aaetherium.resourcesDir="))) {
                List<String> resDirs = new ArrayList<>();
                for (File d : java.getSourceSets().getByName("main").getResources().getSrcDirs()) {
                    resDirs.add(d.getAbsolutePath());
                }
                t.getOptions().getCompilerArgs().add(
                        "-Aaetherium.resourcesDir=" + String.join(File.pathSeparator, resDirs));
            }
        });
        // the framework runtime needs the Vector API module + native access at RUNTIME, not
        // just --enable-preview. Set the full flag set on every Test and JavaExec the consumer runs, so mod
        // code that touches the framework doesn't fail to load. (Also documented in gradle-plugin.md.)
        final List<String> runtimeJvmArgs = List.of(
                "--enable-preview", "--add-modules=jdk.incubator.vector", "--enable-native-access=ALL-UNNAMED");
        p.getTasks().withType(Test.class).configureEach(t -> t.jvmArgs(runtimeJvmArgs));
        p.getTasks().withType(org.gradle.api.tasks.JavaExec.class).configureEach(t -> t.jvmArgs(runtimeJvmArgs));

        if (Boolean.TRUE.equals(ext.getGenerateMetadata().getOrElse(Boolean.TRUE))) {
            configureMetadata(p, ext);
        }

        // When the shield is on it produces a task-owned shielded mirror that EVERY packaging task must
        // consume (/); shieldedDir is null when the shield is off. Protected mods also get
        // aetherium-verify automatically () — in-game verification for exactly the mods that
        // opted into protection.
        final boolean shieldOn = Boolean.TRUE.equals(ext.getShield().getOrElse(Boolean.FALSE));
        if (shieldOn) {
            p.getDependencies().add("implementation", GROUP + ":aetherium-verify:" + version);
        }
        final File shieldedDir = shieldOn ? registerShieldTask(p, ext, version) : null;

        if (Boolean.TRUE.equals(ext.getBundle().getOrElse(Boolean.TRUE))) {
            registerBundleTask(p, shieldedDir);
        }
        if (Boolean.TRUE.equals(ext.getUniversal().getOrElse(Boolean.FALSE))) {
            boolean embedLoader = Boolean.TRUE.equals(ext.getEmbedLoader().getOrElse(Boolean.TRUE));
            if (embedLoader) {
                // Resolve the loader so the universal jar is fully self-contained; only aetherium-*
                // artifacts are actually embedded (Minecraft/NeoForge stay external).
                p.getDependencies().add("runtimeOnly", GROUP + ":aetherium-loader:" + version);
            }
            registerUniversalTask(p, sanitizeModId(ext.getModId().getOrElse(p.getName())), version, shieldedDir);
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
        // a pure Aetherium mod is inert without the loader. When the loader is NOT embedded
        // (the default, and only working, mode), declare it as a required dependency in BOTH metadata
        // files so a user who installs only the mod jar gets a clear "missing dependency" error instead
        // of a silently do-nothing mod. The plugin already knows the framework version — no new input.
        final boolean embedLoader = Boolean.TRUE.equals(ext.getEmbedLoader().getOrElse(Boolean.FALSE));
        final boolean requireLoader = !embedLoader;
        final String frameworkVersion = ext.getVersion().getOrElse("");

        TaskProvider<?> gen = p.getTasks().register("generateAetheriumMetadata", task -> {
            task.setGroup("aetherium");
            task.setDescription("Generates host-loader metadata (neoforge.mods.toml + fabric.mod.json).");
            task.getInputs().property("modId", modId);
            task.getInputs().property("displayName", displayName);
            task.getInputs().property("modVersion", modVersion);
            task.getInputs().property("requireLoader", requireLoader);
            task.getInputs().property("frameworkVersion", frameworkVersion);
            task.getOutputs().dir(genDir);
            task.doLast(t -> writeMetadata(genDir, modId, displayName, modVersion, requireLoader, frameworkVersion));
        });

        SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main").getResources().srcDir(genDir);
        p.getTasks().named("processResources").configure(t -> t.dependsOn(gen));
    }

    private static void writeMetadata(File genDir, String modId, String displayName, String version,
            boolean requireLoader, String frameworkVersion) {
        try {
            File metaInf = new File(genDir, "META-INF");
            Files.createDirectories(metaInf.toPath());
            Files.writeString(new File(metaInf, "neoforge.mods.toml").toPath(),
                    neoforgeModsToml(modId, displayName, version, requireLoader, frameworkVersion),
                    StandardCharsets.UTF_8);
            Files.writeString(new File(genDir, "fabric.mod.json").toPath(),
                    fabricModJson(modId, displayName, version, requireLoader, frameworkVersion),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Aetherium loader metadata", e);
        }
    }

    /** NeoForge lower-bound version range for the loader dependency ({@code [fv,)}; permissive if unknown). */
    private static String neoforgeLoaderRange(String frameworkVersion) {
        return (frameworkVersion == null || frameworkVersion.isBlank())
                ? "[0.0.0,)" : "[" + frameworkVersion + ",)";
    }

    /** Fabric semver predicate for the loader dependency ({@code >=fv}; permissive if unknown). */
    private static String fabricLoaderRange(String frameworkVersion) {
        return (frameworkVersion == null || frameworkVersion.isBlank())
                ? "*" : ">=" + frameworkVersion;
    }

    static String neoforgeModsToml(String modId, String displayName, String version,
            boolean requireLoader, String frameworkVersion) {
        // when the loader is not embedded, require it (modId "aetherium"). ordering="AFTER"
        // means THIS mod loads after aetherium, i.e. the loader/PAL initialises first — which is what a
        // pure Aetherium mod needs before its @AetheriumInit runs.
        String loaderDep = requireLoader
                ? "[[dependencies." + modId + "]]\n"
                        + "modId = \"aetherium\"\n"
                        + "type = \"required\"\n"
                        + "versionRange = \"" + neoforgeLoaderRange(frameworkVersion) + "\"\n"
                        + "ordering = \"AFTER\"\n"
                        + "side = \"BOTH\"\n\n"
                : "";
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
                + loaderDep
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

    static String fabricModJson(String modId, String displayName, String version,
            boolean requireLoader, String frameworkVersion) {
        // mirror the loader requirement on Fabric (the loader's Fabric id is "aetherium").
        String loaderDep = requireLoader
                ? "    \"aetherium\": \"" + fabricLoaderRange(frameworkVersion) + "\",\n"
                : "";
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"id\": \"" + modId + "\",\n"
                + "  \"version\": \"" + version + "\",\n"
                + "  \"name\": \"" + displayName + "\",\n"
                + "  \"license\": \"AGPL-3.0-or-later\",\n"
                + "  \"environment\": \"*\",\n"
                + "  \"depends\": {\n"
                + loaderDep
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
     * Register {@code mergeAetheriumLang}: consolidate every {@code assets/<id>/lang/*.json} that exists in
     * both the classes output (AP-generated) and the resources output (author-written) into a single merged
     * file (key union; author value wins a conflict, with a warning) written to the resources output, and
     * delete the classes-output copy — so packaging sees exactly one, correct lang file. Every jar-like task
     * depends on it. Runs in the Gradle daemon, so the merge is plain, preview-free Java.
     */
    private void registerLangMerge(Project p) {
        TaskProvider<?> merge = p.getTasks().register("mergeAetheriumLang", task -> {
            task.setGroup("aetherium");
            task.setDescription("Merge AP-generated + hand-written lang JSON by key union (no silent drop).");
            task.dependsOn("classes", "processResources");
            task.doLast(t -> mergeLangInPlace(p));
        });
        for (String jarLike : List.of("jar")) {
            p.getTasks().named(jarLike).configure(t -> t.dependsOn(merge));
        }
        // aetheriumBundle / aetheriumUniversalJar also depend on it — wired where they are registered
        // (they may not exist yet). We add the dependency lazily by name below in those methods.
    }

    /** Merge/consolidate all Aetherium lang files into the resources output; delete class-output copies. */
    private static void mergeLangInPlace(Project p) {
        var out = p.getExtensions().getByType(SourceSetContainer.class).getByName("main").getOutput();
        File resDir = out.getResourcesDir();
        if (resDir == null) {
            return;
        }
        List<File> roots = new ArrayList<>();
        for (File c : out.getClassesDirs().getFiles()) {
            roots.add(c);
        }
        roots.add(resDir);

        // relPath -> ordered contributors (classes first = AP, resources last = author wins).
        Map<String, List<File>> byRel = new LinkedHashMap<>();
        for (File root : roots) {
            collectLangFiles(root, root, byRel);
        }
        Set<File> classDirFiles = new LinkedHashSet<>(out.getClassesDirs().getFiles());
        Map<String, Map<String, String>> mergedByRel = new LinkedHashMap<>();
        Map<String, Set<String>> generatedKeysByRel = new LinkedHashMap<>();
        for (Map.Entry<String, List<File>> e : byRel.entrySet()) {
            String rel = e.getKey();
            Map<String, String> merged = new LinkedHashMap<>();
            Set<String> generatedKeys = new LinkedHashSet<>();
            for (File f : e.getValue()) {
                Map<String, String> one = parseFlatJson(readFile(f));
                if (one == null) {
                    continue; // not a flat lang object — leave it alone (do not merge/drop)
                }
                // Keys from a classes-output file are annotation-processor generated (e.g. itemGroup.<id>).
                if (classDirFiles.stream().anyMatch(c -> f.getPath().startsWith(c.getPath()))) {
                    generatedKeys.addAll(one.keySet());
                }
                for (Map.Entry<String, String> kv : one.entrySet()) {
                    String prev = merged.put(kv.getKey(), kv.getValue());
                    if (prev != null && !prev.equals(kv.getValue())) {
                        p.getLogger().warn("aetherium: lang key '{}' in {} overrides a different value from an "
                                + "earlier contributor (merging, author wins).", kv.getKey(), rel);
                    }
                }
            }
            mergedByRel.put(rel, merged);
            generatedKeysByRel.put(rel, generatedKeys);
            // Write the merged result into the resources output, and drop any classes-output copies.
            File target = new File(resDir, rel);
            writeFile(target, writeFlatJson(merged));
            for (File c : out.getClassesDirs().getFiles()) {
                File dup = new File(c, rel);
                if (dup.exists() && !dup.equals(target)) {
                    //noinspection ResultOfMethodCallIgnored
                    dup.delete();
                }
            }
        }
        warnLangAsymmetry(p, mergedByRel, generatedKeysByRel);
    }

    /**
     * warn when a generated lang key (e.g. {@code itemGroup.<id>}, emitted into {@code en_us} only)
     * is present in {@code en_us.json} but missing from another shipped language in the same {@code lang/} dir —
     * otherwise a non-English player silently sees the English creative-tab title. Reuses the merged maps and
     * the set of AP-generated keys already computed, so it costs nothing extra and names the exact gap.
     */
    private static void warnLangAsymmetry(Project p, Map<String, Map<String, String>> mergedByRel,
            Map<String, Set<String>> generatedKeysByRel) {
        for (String warning : langAsymmetryWarnings(mergedByRel, generatedKeysByRel)) {
            p.getLogger().warn("aetherium: {}", warning);
        }
    }

    /**
     * Pure computation of the asymmetry warnings (as message strings), so it is unit-testable without a
     * Gradle {@code Project}. For each {@code lang/} directory, any AP-generated key present in {@code en_us}
     * but missing from another shipped language is one warning naming the key and the file.
     */
    static List<String> langAsymmetryWarnings(Map<String, Map<String, String>> mergedByRel,
            Map<String, Set<String>> generatedKeysByRel) {
        List<String> warnings = new ArrayList<>();
        Map<String, List<String>> byDir = new LinkedHashMap<>();
        for (String rel : mergedByRel.keySet()) {
            int slash = rel.lastIndexOf('/');
            String dir = slash < 0 ? "" : rel.substring(0, slash);
            byDir.computeIfAbsent(dir, d -> new ArrayList<>()).add(rel);
        }
        for (Map.Entry<String, List<String>> dir : byDir.entrySet()) {
            String enRel = dir.getKey() + "/en_us.json";
            Set<String> generated = generatedKeysByRel.getOrDefault(enRel, Set.of());
            if (!mergedByRel.containsKey(enRel) || generated.isEmpty()) {
                continue; // no generated en_us keys here → nothing to be asymmetric about
            }
            for (String rel : dir.getValue()) {
                if (rel.equals(enRel)) {
                    continue;
                }
                Map<String, String> other = mergedByRel.get(rel);
                for (String key : generated) {
                    if (!other.containsKey(key)) {
                        String langFile = rel.substring(rel.lastIndexOf('/') + 1);
                        warnings.add("generated lang key '" + key + "' is in en_us.json but missing from "
                                + langFile + " — non-English players will see the English text. Add it to "
                                + rel + ".");
                    }
                }
            }
        }
        return warnings;
    }

    private static void collectLangFiles(File root, File dir, Map<String, List<File>> byRel) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory()) {
                collectLangFiles(root, k, byRel);
            } else if (k.getName().endsWith(".json") && k.getPath().replace('\\', '/').contains("/lang/")
                    && k.getPath().replace('\\', '/').contains("assets/")) {
                String rel = root.toPath().relativize(k.toPath()).toString().replace('\\', '/');
                byRel.computeIfAbsent(rel, r -> new ArrayList<>()).add(k);
            }
        }
    }

    /** Parse a flat {@code {"key":"value",...}} JSON object; returns null if the shape is not flat. */
    static Map<String, String> parseFlatJson(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null) {
            return map;
        }
        int i = 0;
        int n = text.length();
        while (i < n && text.charAt(i) != '{') {
            i++;
        }
        if (i >= n) {
            return map.isEmpty() ? map : null;
        }
        i++; // past '{'
        while (i < n) {
            i = skipWs(text, i);
            if (i < n && text.charAt(i) == '}') {
                return map;
            }
            if (i >= n || text.charAt(i) != '"') {
                return null; // not a flat object
            }
            int[] keyEnd = new int[1];
            String key = readJsonString(text, i, keyEnd);
            if (key == null) {
                return null;
            }
            i = skipWs(text, keyEnd[0]);
            if (i >= n || text.charAt(i) != ':') {
                return null;
            }
            i = skipWs(text, i + 1);
            if (i >= n || text.charAt(i) != '"') {
                return null; // value must be a string (flat lang map)
            }
            int[] valEnd = new int[1];
            String val = readJsonString(text, i, valEnd);
            if (val == null) {
                return null;
            }
            map.put(key, val);
            i = skipWs(text, valEnd[0]);
            if (i < n && text.charAt(i) == ',') {
                i++;
            } else if (i < n && text.charAt(i) == '}') {
                return map;
            }
        }
        return map;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Read a JSON string starting at the opening quote {@code s[start]}; end index (past close) in {@code end[0]}. */
    private static String readJsonString(String s, int start, int[] end) {
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                end[0] = i;
                return sb.toString();
            }
            if (c == '\\' && i < s.length()) {
                char esc = s.charAt(i++);
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 4 <= s.length()) {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                    }
                    default -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        return null; // unterminated
    }

    /** Serialize a flat lang map to pretty, sorted-key JSON. */
    static String writeFlatJson(Map<String, String> map) {
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort(String::compareTo);
        StringBuilder sb = new StringBuilder("{\n");
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            sb.append("  ").append(jsonStr(k)).append(": ").append(jsonStr(map.get(k)))
                    .append(i < keys.size() - 1 ? ",\n" : "\n");
        }
        return sb.append("}\n").toString();
    }

    private static String jsonStr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String readFile(File f) {
        try {
            return Files.readString(f.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeFile(File f, String content) {
        try {
            Files.createDirectories(f.getParentFile().toPath());
            Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Register the {@code aetheriumShield} task: a <strong>forked</strong> JavaExec that obfuscates the mod's
     * compiled classes into a <strong>separate, task-owned output directory</strong>
     * ({@code build/aetherium/shielded}) — it never mutates {@code build/classes}. This makes the task
     * incremental + cacheable (its output no longer overlaps its input) and, crucially, keeps
     * {@code ./gradlew build} repeatable: the compile output stays under its original class names, so a second
     * build (and {@code compileTestJava}) never breaks (). Every packaging task then consumes the
     * shielded directory (), and fails loudly if {@code shield = true} produced no integrity
     * manifest — a security feature must never fail open. Forks because the framework runtime is preview.
     *
     * @return the shielded output directory that packaging tasks must consume
     */
    private File registerShieldTask(Project p, AetheriumExtension ext, String version) {
        Configuration tool = p.getConfigurations().detachedConfiguration(
                p.getDependencies().create(GROUP + ":aetherium-shield:" + version));

        final String author = ext.getShieldAuthor().getOrElse("");
        // is fixed (ShieldDirectory rewrites content.index/behaviors.index through the rename map),
        // so class renaming — the strongest anti-analysis pass — is now SAFE BY DEFAULT when the shield is on.
        final boolean rename = Boolean.TRUE.equals(ext.getShieldRename().getOrElse(Boolean.TRUE));

        SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
        final File shieldedDir = new File(p.getLayout().getBuildDirectory().getAsFile().get(),
                "aetherium/shielded");

        p.getTasks().register("aetheriumShield", org.gradle.api.tasks.JavaExec.class, task -> {
            task.setGroup("aetherium");
            task.setDescription("Obfuscate the mod's classes into build/aetherium/shielded (never mutates build/classes).");
            task.dependsOn("classes");
            task.mustRunAfter("classes");
            task.getMainClass().set("org.aetherium.shield.ShieldDirectory");
            task.setClasspath(tool);
            task.jvmArgs("--enable-preview", "--add-modules=jdk.incubator.vector");
            // Proper up-to-date tracking: input = compiled classes, output = the shielded mirror.
            task.getInputs().files(sourceSets.getByName("main").getOutput().getClassesDirs());
            task.getOutputs().dir(shieldedDir);
            task.doFirst(t -> {
                File classesDir = sourceSets.getByName("main").getOutput().getClassesDirs().getFiles()
                        .stream().findFirst().orElse(null);
                List<String> args = new ArrayList<>();
                args.add(classesDir == null ? "" : classesDir.getAbsolutePath());
                args.add(author);
                if (rename) {
                    args.add("--rename");
                }
                args.add("--out");
                args.add(shieldedDir.getAbsolutePath());
                // Pass the mod's runtime classpath (Minecraft + framework) so control-flow obfuscation can
                // recompute frames — far fewer classes revert un-protected (). Best-effort.
                try {
                    Configuration runtime = p.getConfigurations().findByName("runtimeClasspath");
                    if (runtime != null) {
                        StringBuilder cp = new StringBuilder();
                        for (File f : runtime.getFiles()) {
                            if (cp.length() > 0) {
                                cp.append(File.pathSeparator);
                            }
                            cp.append(f.getAbsolutePath());
                        }
                        if (cp.length() > 0) {
                            args.add("--classpath");
                            args.add(cp.toString());
                        }
                    }
                } catch (RuntimeException ignored) {
                    // unresolved classpath just means more classes may revert — never fail the build
                }
                ((org.gradle.api.tasks.JavaExec) t).setArgs(args);
            });
        });

        // The default jar consumes the shielded mirror (); bundle/universal do too (wired in their
        // register methods, since those tasks may not exist yet).
        p.getTasks().named("jar", Jar.class).configure(jar -> applyShieldedContent(jar, p, shieldedDir, false));
        return shieldedDir;
    }

    /**
     * Point a packaging {@link Jar} task at the shielded mirror instead of the raw compile output: drop every
     * file under a compiled-classes dir, add {@code shieldedDir} (a full, protected mirror), and refuse to
     * package if the shield produced no integrity manifest (fail-closed — ).
     */
    private static void applyShieldedContent(Jar jar, Project p, File shieldedDir, boolean excludeServices) {
        jar.dependsOn("aetheriumShield");
        SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
        final java.util.Set<File> classesDirs = sourceSets.getByName("main").getOutput().getClassesDirs().getFiles();
        // Global exclude keyed on ABSOLUTE path, so it drops the original compiled classes (+ AP-generated
        // resources under the classes dir) but never the shieldedDir mirror or the resources output.
        jar.exclude(element -> {
            String abs = element.getFile().getAbsolutePath();
            for (File cd : classesDirs) {
                if (abs.startsWith(cd.getAbsolutePath())) {
                    return true;
                }
            }
            return false;
        });
        jar.from(shieldedDir, spec -> {
            if (excludeServices) {
                spec.exclude("META-INF/services/**");
            }
        });
        jar.doFirst(t -> {
            File manifest = new File(shieldedDir, "META-INF/aetherium/shield-integrity.txt");
            if (!manifest.exists()) {
                throw new org.gradle.api.GradleException("aetherium: shield = true but no shield-integrity.txt "
                        + "was produced in " + shieldedDir + " — refusing to package an unprotected artifact. "
                        + "(A security feature must not fail open.)");
            }
        });
    }

    /**
     * Register the legacy flat-bundle task. Because this <em>flattens</em> the framework into the mod
     * namespace, it explicitly <strong>merges</strong> {@code META-INF/services} files (concatenated, never
     * overwritten) so {@code ServiceLoader} manifests survive — the QA "services destroyed on merge" fix.
     * The universal jar (Jar-in-Jar) is the recommended path; the bundle remains for the library-mod case.
     */
    private void registerBundleTask(Project p, File shieldedDir) {
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
            jar.dependsOn(mergeServices, "mergeAetheriumLang");

            SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
            // Mod classes + embedded framework classes, EXCEPT service files (merged authoritatively below).
            jar.from(sourceSets.getByName("main").getOutput(), s -> s.exclude("META-INF/services/**"));
            jar.from(p.provider(() -> flattenedAetheriumTrees(p)), s -> s.exclude("META-INF/services/**"));
            jar.from(svcDir); // the concatenated service files
            // when shielded, package the protected mirror instead of the raw classes (services
            // excluded — the bundle merges them separately above).
            if (shieldedDir != null) {
                applyShieldedContent(jar, p, shieldedDir, true);
            }
        });
    }

    /**
     * Register the Universal Jar task using the <strong>NeoForge Jar-in-Jar</strong> standard: the
     * framework is embedded as whole nested jars under {@code META-INF/jarjar/} and declared in
     * {@code metadata.json}, <em>not</em> shadowed into the mod's namespace. NeoForge extracts and loads
     * each {@code group:artifact} <strong>once globally</strong>, so two Aetherium mods no longer spin up
     * colliding engines / duplicate {@code O(1)} dispatch tables — the QA "fat-jar hell" fix.
     */
    private void registerUniversalTask(Project p, String modId, String version, File shieldedDir) {
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
            jar.dependsOn(jijMeta, "mergeAetheriumLang");
            jar.getManifest().getAttributes().putAll(universalManifest(modId));

            SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
            // ONLY the mod's own classes + metadata at the jar root — the framework is NOT shadowed in.
            jar.from(sourceSets.getByName("main").getOutput());
            // Each framework jar embedded WHOLE under META-INF/jarjar (loaded once, globally, by NeoForge).
            jar.from(p.provider(() -> aetheriumRuntimeJars(p)), s -> s.into("META-INF/jarjar"));
            jar.from(jijDir); // the JiJ metadata.json listing those nested jars
            // the RECOMMENDED artifact must actually be protected — package the shielded mirror,
            // not the raw classes, and fail loudly if the shield produced no manifest.
            if (shieldedDir != null) {
                applyShieldedContent(jar, p, shieldedDir, false);
            }
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
