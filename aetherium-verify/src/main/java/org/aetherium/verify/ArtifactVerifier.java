/*
 * Aetherium Framework — sovereign build-time artifact verifier (, ).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.verify;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Verifies that the framework's shipped artifacts are actually runnable — using Aetherium's <em>own</em>
 * ASM, no external tool. This is the "five-line ASM/zip test" a downstream mod asked for in , done
 * properly: it would have caught, before release, every one of the four sub-failures that made the loader
 * un-runnable in a real game session.
 *
 * <p>EN: Sovereignty in practice — the framework proves its own output correct rather than trusting the
 * build. Checks, on the {@code aetherium-loader} MOD jar and the {@code aetherium-transformer} GAMELIBRARY
 * jar: (1) the loader is <strong>self-contained</strong> — every {@code org/aetherium/**} class its own
 * classes reference is present, either directly or in a {@code META-INF/jarjar} nested jar (a); (2) no
 * <strong>preview leak</strong> — the boot-layer transformer and the loader's {@code @Mod} entrypoint carry
 * class-file minor {@code 0x0000}, so they load on a vanilla JVM (c); (3) no <strong>split package</strong>
 * — neither jar ships a top-level copy of ASM/SLF4J (follow-up); (4) <strong>correct roles</strong> — the
 * loader is {@code FMLModType=MOD} with no ModLauncher services, the transformer is {@code GAMELIBRARY} with
 * both services (b). Pure (zip + ASM), so it runs headless in the build and is unit-testable.
 * RU: Суверенность на практике — фреймворк доказывает корректность своего вывода, а не доверяет сборке.
 * Проверяет на MOD-jar загрузчика и GAMELIBRARY-jar трансформера: (1) загрузчик самодостаточен — каждый
 * класс {@code org/aetherium/**}, на который он ссылается, присутствует прямо или в {@code META-INF/jarjar}
 * (a); (2) нет утечки preview — трансформер и точка входа {@code @Mod} имеют minor {@code 0x0000} и
 * грузятся на ванильной JVM (c); (3) нет split package — ни один jar не несёт копию ASM/SLF4J (); (4)
 * верные роли — загрузчик {@code MOD} без сервисов ModLauncher, трансформер {@code GAMELIBRARY} с обоими
 * сервисами (b). Чистый (zip + ASM), работает headless в сборке и покрывается юнит-тестами.
 */
public final class ArtifactVerifier {

    private ArtifactVerifier() {
    }

    private static final String ENTRYPOINT = "org/aetherium/loader/AetheriumNeoForgeEntrypoint.class";
    private static final String TS_SERVICE = "META-INF/services/cpw.mods.modlauncher.api.ITransformationService";
    private static final String LP_SERVICE =
            "META-INF/services/cpw.mods.modlauncher.serviceapi.ILaunchPluginService";

    /** A single failed invariant. */
    public record Violation(String code, String detail) {
    }

    /** The verification outcome. */
    public record Result(List<Violation> violations) {
        public boolean ok() {
            return violations.isEmpty();
        }
    }

    /** Verify the loader MOD jar and the transformer GAMELIBRARY jar together. Never throws for I/O. */
    public static Result verify(Path loaderJar, Path transformerJar) {
        List<Violation> v = new ArrayList<>();
        try {
            verifyTransformer(transformerJar, v);
            // The transformer ships as a sibling boot-layer jar; the mod layer sees it at runtime, so its
            // classes satisfy the loader's references even though they are not inside the loader jar.
            Set<String> transformerClasses = new LinkedHashSet<>();
            for (String e : topLevelEntryNames(transformerJar)) {
                if (e.endsWith(".class")) {
                    transformerClasses.add(e);
                }
            }
            verifyLoader(loaderJar, transformerClasses, v);
            checkModuleClash(loaderJar, transformerJar, v);
        } catch (IOException io) {
            v.add(new Violation("AE-VERIFY-IO", "could not read an artifact: " + io.getMessage()));
        }
        return new Result(List.copyOf(v));
    }

    /**
     * The check the old verifier lacked: the shipped jars must not export the same package from two
     * different modules, or NeoForge's module resolver throws {@code ResolutionException} before the window.
     *
     * <p>Enumerate every <em>module</em> the shipped set produces — each jar's loose (top-level) classes as one
     * module, <strong>plus one module per {@code META-INF/jarjar/*.jar} entry</strong> — and assert no package
     * appears in two of them. Nested jars are keyed by file name, so Jar-in-Jar's own deduplication (the same
     * nested jar embedded by two mods) is NOT counted as a clash. This reproduces the crash offline: the OLD
     * fat transformer + the loader's nested {@code aetherium-core.jar} both export {@code org/aetherium/core}.
     */
    private static void checkModuleClash(Path loaderJar, Path transformerJar, List<Violation> v)
            throws IOException {
        v.addAll(moduleClashes(loaderJar, transformerJar));
    }

    /** The cross-artifact clash logic, isolated so it is unit-testable without the other checks. */
    static List<Violation> moduleClashes(Path loaderJar, Path transformerJar) throws IOException {
        List<Violation> v = new ArrayList<>();
        // module key -> set of packages it exports
        Map<String, Set<String>> modulePackages = new LinkedHashMap<>();
        collectModules(transformerJar, "aetherium-transformer.jar (loose)", modulePackages);
        collectModules(loaderJar, "aetherium-loader.jar (loose)", modulePackages);

        // package -> set of module keys that export it
        Map<String, Set<String>> packageOwners = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> mod : modulePackages.entrySet()) {
            for (String pkg : mod.getValue()) {
                packageOwners.computeIfAbsent(pkg, k -> new LinkedHashSet<>()).add(mod.getKey());
            }
        }
        for (Map.Entry<String, Set<String>> owned : packageOwners.entrySet()) {
            if (owned.getValue().size() > 1) {
                v.add(new Violation("AE-MODULE-CLASH", "package " + owned.getKey()
                        + " is exported by two modules " + owned.getValue()
                        + " — NeoForge's module resolver will throw ResolutionException at boot"));
            }
        }
        return v;
    }

    /** Add {@code jar}'s loose classes (under {@code looseKey}) + one module per nested jar (keyed by name). */
    private static void collectModules(Path jar, String looseKey, Map<String, Set<String>> out)
            throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.startsWith("META-INF/jarjar/") && name.endsWith(".jar")) {
                    String nestedKey = name.substring("META-INF/jarjar/".length()); // dedup by file name
                    byte[] nested = readAll(zis);
                    Set<String> pkgs = out.computeIfAbsent(nestedKey, k -> new LinkedHashSet<>());
                    try (ZipInputStream inner = new ZipInputStream(new java.io.ByteArrayInputStream(nested))) {
                        ZipEntry ie;
                        while ((ie = inner.getNextEntry()) != null) {
                            addPackage(ie.getName(), pkgs);
                        }
                    }
                } else if (name.endsWith(".class")) {
                    addPackage(name, out.computeIfAbsent(looseKey, k -> new LinkedHashSet<>()));
                }
            }
        }
    }

    private static void addPackage(String classEntry, Set<String> pkgs) {
        if (classEntry.endsWith(".class") && classEntry.lastIndexOf('/') > 0) {
            pkgs.add(classEntry.substring(0, classEntry.lastIndexOf('/'))); // the package (internal form)
        }
    }

    // --- transformer (boot-layer GAMELIBRARY) --------------------------------------------------

    private static void verifyTransformer(Path jar, List<Violation> v) throws IOException {
        Set<String> entries = topLevelEntryNames(jar);
        // (4) role: GAMELIBRARY + both ModLauncher services present.
        String modType = manifestAttr(jar, "FMLModType");
        if (!"GAMELIBRARY".equals(modType)) {
            v.add(new Violation("AE-ROLE-TRANSFORMER",
                    "transformer FMLModType must be GAMELIBRARY (was " + modType + ")"));
        }
        if (!entries.contains(TS_SERVICE) || !entries.contains(LP_SERVICE)) {
            v.add(new Violation("AE-ROLE-SERVICES",
                    "transformer must declare both ModLauncher service files"));
        }
        // (2) preview leak: EVERY class in the boot layer must be minor 0x0000.
        for (byte[] cls : classBytes(jar)) {
            if (isPreview(cls)) {
                v.add(new Violation("AE-PREVIEW-LEAK",
                        "boot-layer class is preview-compiled (0xFFFF): " + internalName(cls)));
            }
        }
        // (3) split package: no bundled ASM/SLF4J at the top level.
        for (String bad : platformLeaks(entries)) {
            v.add(new Violation("AE-SPLIT-PACKAGE", "transformer bundles a platform library: " + bad));
        }
    }

    // --- loader (MOD) ---------------------------------------------------------------------------

    private static void verifyLoader(Path jar, Set<String> siblingProvided, List<Violation> v)
            throws IOException {
        Set<String> topEntries = topLevelEntryNames(jar);
        // (4) role: MOD + NO ModLauncher services (those belong to the transformer only).
        String modType = manifestAttr(jar, "FMLModType");
        if (!"MOD".equals(modType)) {
            v.add(new Violation("AE-ROLE-LOADER", "loader FMLModType must be MOD (was " + modType + ")"));
        }
        if (topEntries.contains(TS_SERVICE) || topEntries.contains(LP_SERVICE)) {
            v.add(new Violation("AE-ROLE-DOUBLE",
                    "loader must NOT ship ModLauncher services (they moved to the transformer)"));
        }
        // (3) split package at the top level.
        for (String bad : platformLeaks(topEntries)) {
            v.add(new Violation("AE-SPLIT-PACKAGE", "loader bundles a platform library: " + bad));
        }
        // (2) the @Mod entrypoint must load on a vanilla JVM.
        byte[] entry = readEntry(jar, ENTRYPOINT);
        if (entry == null) {
            v.add(new Violation("AE-ENTRYPOINT-MISSING", ENTRYPOINT + " not in the loader jar"));
            return;
        }
        if (isPreview(entry)) {
            v.add(new Violation("AE-PREVIEW-ENTRYPOINT",
                    "the @Mod entrypoint is preview-compiled (0xFFFF); it cannot load without --enable-preview"));
        }
        // (1) self-contained: every org/aetherium/** class the loader's own classes reference must be
        //     resolvable in the loader jar OR in a META-INF/jarjar nested jar.
        Set<String> available = new LinkedHashSet<>();
        for (String e : topEntries) {
            if (e.endsWith(".class")) {
                available.add(e);
            }
        }
        available.addAll(nestedJarClassEntries(jar));
        available.addAll(siblingProvided); // the boot-layer transformer jar (visible to the mod layer)
        Set<String> referenced = new LinkedHashSet<>();
        for (byte[] cls : classBytes(jar)) { // loader's OWN classes only (nested jars are separate zips)
            collectAetheriumRefs(cls, referenced);
        }
        for (String ref : referenced) {
            if (!available.contains(ref)) {
                v.add(new Violation("AE-NOT-SELF-CONTAINED",
                        "loader references " + ref + " but ships it neither directly nor via jar-in-jar"));
            }
        }
    }

    // --- ASM: collect every org/aetherium/** type a class references ----------------------------

    private static void collectAetheriumRefs(byte[] classBytes, Set<String> out) {
        Remapper recorder = new Remapper() {
            @Override
            public String map(String internalName) {
                if (internalName != null && internalName.startsWith("org/aetherium/")) {
                    out.add(internalName + ".class");
                }
                return internalName;
            }
        };
        // ClassRemapper visits every type reference (super, interfaces, fields, method owners/descriptors,
        // instructions, indy) and routes each through the recorder — a comprehensive reference walk.
        new ClassReader(classBytes).accept(new ClassRemapper(new ClassWriter(0), recorder), 0);
    }

    // --- zip helpers ----------------------------------------------------------------------------

    private static boolean isPreview(byte[] classFile) {
        // Class file: magic(0..3) minor(4..5) major(6..7). Preview => minor == 0xFFFF.
        return classFile.length >= 6
                && (classFile[4] & 0xFF) == 0xFF && (classFile[5] & 0xFF) == 0xFF;
    }

    private static String internalName(byte[] classFile) {
        try {
            return new ClassReader(classFile).getClassName();
        } catch (RuntimeException malformed) {
            return "<unreadable>";
        }
    }

    private static List<String> platformLeaks(Set<String> entries) {
        List<String> leaks = new ArrayList<>();
        for (String e : entries) {
            if (e.endsWith(".class") && (e.startsWith("org/objectweb/asm/") || e.startsWith("org/slf4j/"))) {
                leaks.add(e);
            }
        }
        return leaks;
    }

    private static Set<String> topLevelEntryNames(Path jar) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                names.add(e.getName());
            }
        }
        return names;
    }

    /** Bytes of every top-level {@code .class} entry (NOT descending into nested jars). */
    private static List<byte[]> classBytes(Path jar) throws IOException {
        List<byte[]> out = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().endsWith(".class")) {
                    out.add(readAll(zis));
                }
            }
        }
        return out;
    }

    private static byte[] readEntry(Path jar, String name) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().equals(name)) {
                    return readAll(zis);
                }
            }
        }
        return null;
    }

    /** Class entry names inside every {@code META-INF/jarjar/*.jar} nested in the loader jar. */
    private static Set<String> nestedJarClassEntries(Path jar) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (ZipInputStream outer = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = outer.getNextEntry()) != null) {
                if (e.getName().startsWith("META-INF/jarjar/") && e.getName().endsWith(".jar")) {
                    byte[] nested = readAll(outer);
                    try (ZipInputStream inner = new ZipInputStream(new java.io.ByteArrayInputStream(nested))) {
                        ZipEntry ie;
                        while ((ie = inner.getNextEntry()) != null) {
                            if (ie.getName().endsWith(".class")) {
                                names.add(ie.getName());
                            }
                        }
                    }
                }
            }
        }
        return names;
    }

    private static String manifestAttr(Path jar, String key) throws IOException {
        byte[] mf = readEntry(jar, "META-INF/MANIFEST.MF");
        if (mf == null) {
            return null;
        }
        Manifest manifest = new Manifest(new java.io.ByteArrayInputStream(mf));
        Attributes attrs = manifest.getMainAttributes();
        return attrs.getValue(key);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    // --- CLI / Gradle entry point ---------------------------------------------------------------

    /** {@code ArtifactVerifier <loader.jar> <transformer.jar>} — exits non-zero on any violation. */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: ArtifactVerifier <loader.jar> <transformer.jar>");
            System.exit(2);
            return;
        }
        Result r = verify(Path.of(args[0]), Path.of(args[1]));
        if (r.ok()) {
            System.out.println("verifyJar: OK — loader self-contained, boot path preview-free, "
                    + "no split package, roles correct (MOD + GAMELIBRARY).");
            return;
        }
        System.err.println("verifyJar: " + r.violations().size() + " violation(s):");
        for (Violation viol : r.violations()) {
            System.err.println("  ✗ [" + viol.code() + "] " + viol.detail());
        }
        System.exit(1);
    }
}
