/*
 * Aetherium Framework — headless boot smoke harness (WS-BOOT).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.verify;

import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Boots the framework's <em>shipped</em> jars in isolation — no Minecraft, no display, no network — and
 * proves the boot layer actually loads and runs from the artifacts as distributed.
 *
 * <p>EN: showed that {@code runClient} hid every defect because it uses the Gradle classpath, not
 * the shipped jars. This harness closes that gap the sovereign way: it opens the shipped
 * {@code aetherium-transformer} jar in a child {@link URLClassLoader} (so its classes load <em>from the jar</em>,
 * exactly as ModLauncher's boot layer does — ASM/SLF4J resolve from the parent), then drives it the way FML
 * would: discover the {@link ITransformationService} and {@link ILaunchPluginService} via {@code ServiceLoader},
 * assert they are Aetherium's, initialise the service, and run a <strong>real bytecode transform</strong>
 * through the launch plugin. It also checks the {@code aetherium-loader} MOD jar declares its PAL/UI services
 * pointing at classes actually present in the jar. Full in-game FML mod-listing needs the client run (a display);
 * everything provable headless is proven here.
 * RU: показал, что {@code runClient} скрывал дефекты , т.к. использует classpath Gradle, а не
 * поставляемые jar. Харнесс закрывает это суверенно: открывает поставляемый jar {@code aetherium-transformer} в
 * дочернем {@link URLClassLoader} (классы грузятся ИЗ jar, как в boot-слое ModLauncher; ASM/SLF4J — от родителя),
 * затем ведёт его как FML: находит {@link ITransformationService}/{@link ILaunchPluginService} через
 * {@code ServiceLoader}, проверяет, что это сервисы Aetherium, инициализирует и выполняет РЕАЛЬНУЮ трансформацию
 * байткода. Также проверяет, что MOD-jar загрузчика объявляет сервисы PAL/UI на классы, реально лежащие в jar.
 */
public final class BootHarness {

    private BootHarness() {
    }

    /** A single boot check and whether it passed. */
    public record Step(String name, boolean ok, String detail) {
    }

    /** The boot smoke outcome. */
    public record Result(List<Step> steps) {
        public boolean ok() {
            for (Step s : steps) {
                if (!s.ok()) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Boot the transformer jar in isolation and drive its ModLauncher services; then check the loader jar's
     * mod-layer service wiring. Never throws — every failure becomes a failed {@link Step}.
     */
    public static Result boot(Path transformerJar, Path loaderJar) {
        List<Step> steps = new ArrayList<>();
        try {
            URL[] urls = {transformerJar.toUri().toURL()};
            // Parent = this class's loader: it has ASM + ModLauncher but NOT org.aetherium.transformer.*, so
            // the transformer's boot classes are loaded FROM THE JAR (proving the shipped artifact works).
            try (URLClassLoader jarCl = new URLClassLoader(urls, BootHarness.class.getClassLoader())) {
                bootTransformer(jarCl, steps);
            }
        } catch (Throwable t) {
            steps.add(new Step("transformer-classloader", false, t.toString()));
        }
        checkLoaderServices(loaderJar, steps);
        // bootSmoke loads the transformer ALONE, so it cannot see the module-graph clash that
        // actually crashed the game. Run the cross-artifact check on both shipped jars here too.
        try {
            List<ArtifactVerifier.Violation> clashes = ArtifactVerifier.moduleClashes(loaderJar, transformerJar);
            steps.add(new Step("no-cross-artifact-package-clash", clashes.isEmpty(),
                    clashes.isEmpty() ? "no package is exported by two modules across the shipped set"
                            : clashes.size() + " clash(es): " + clashes));
        } catch (java.io.IOException io) {
            steps.add(new Step("cross-artifact-clash-check", false, io.toString()));
        }
        return new Result(List.copyOf(steps));
    }

    private static void bootTransformer(ClassLoader jarCl, List<Step> steps) {
        // 1. ModLauncher discovers the transformation service exactly via ServiceLoader.
        ITransformationService service = null;
        int tsCount = 0;
        for (ITransformationService s : ServiceLoader.load(ITransformationService.class, jarCl)) {
            tsCount++;
            if ("aetherium".equals(s.name())) {
                service = s;
            }
        }
        steps.add(new Step("transformation-service-discovered", service != null,
                "found " + tsCount + " service(s); aetherium present = " + (service != null)));
        // Prove it loaded FROM THE JAR (child loader), not from the app classpath.
        if (service != null) {
            boolean fromJar = service.getClass().getClassLoader() == jarCl;
            steps.add(new Step("service-loaded-from-shipped-jar", fromJar,
                    "loader=" + service.getClass().getClassLoader()));
            try {
                service.initialize(null); // our initialize ignores the environment; also prints the preview advisory
                steps.add(new Step("service-initialize", true, "initialize() ran without error"));
            } catch (Throwable t) {
                steps.add(new Step("service-initialize", false, t.toString()));
            }
        }

        // 2. The launch plugin is discovered and actually transforms a real class from the shipped jar.
        ILaunchPluginService plugin = null;
        for (ILaunchPluginService p : ServiceLoader.load(ILaunchPluginService.class, jarCl)) {
            if ("aetherium".equals(p.name())) {
                plugin = p;
            }
        }
        steps.add(new Step("launch-plugin-discovered", plugin != null,
                plugin == null ? "no aetherium ILaunchPluginService" : plugin.getClass().getName()));
        if (plugin == null) {
            return;
        }

        // A trivial class in the transform allow-list namespace (org/aetherium/testmod/…) so the plugin's
        // namespace filter admits it and the ASM engine actually runs on it.
        byte[] probeBytes = trivialClass("org/aetherium/testmod/BootProbe");
        Type probeType = Type.getObjectType("org/aetherium/testmod/BootProbe");
        try {
            EnumSet<ILaunchPluginService.Phase> phases = plugin.handlesClass(probeType, false);
            boolean admitted = phases != null && !phases.isEmpty();
            steps.add(new Step("namespace-filter-admits-mod-class", admitted, "phases=" + phases));

            ClassNode node = new ClassNode();
            new ClassReader(probeBytes).accept(node, 0);
            // processClass runs the real transform engine (lower→verify→revert-on-fail) from the shipped jar.
            ILaunchPluginService.Phase phase = admitted ? phases.iterator().next() : ILaunchPluginService.Phase.AFTER;
            plugin.processClass(phase, node, probeType);
            steps.add(new Step("transform-engine-runs-from-jar", true,
                    "processClass executed the engine on a real ClassNode without error"));
        } catch (Throwable t) {
            steps.add(new Step("transform-engine-runs-from-jar", false, t.toString()));
        }
    }

    /** Assert the loader MOD jar declares its PAL/UI services pointing at classes present in the jar. */
    private static void checkLoaderServices(Path loaderJar, List<Step> steps) {
        try {
            Set<String> classEntries = new LinkedHashSet<>();
            List<String[]> services = new ArrayList<>(); // [serviceFile, implClass]
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(loaderJar))) {
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    String name = e.getName();
                    if (name.endsWith(".class")) {
                        classEntries.add(name);
                    } else if (name.startsWith("META-INF/services/org.aetherium.")) {
                        String impl = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                                .lines().map(String::trim)
                                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                                .findFirst().orElse("");
                        services.add(new String[]{name.substring("META-INF/services/".length()), impl});
                    }
                }
            }
            steps.add(new Step("loader-declares-pal-ui-services", services.size() >= 2,
                    "service files: " + services.size()));
            for (String[] svc : services) {
                String entry = svc[1].replace('.', '/') + ".class";
                boolean present = classEntries.contains(entry);
                steps.add(new Step("service-impl-present:" + svc[0], present,
                        svc[1] + (present ? " present in jar" : " MISSING from jar")));
            }
        } catch (Throwable t) {
            steps.add(new Step("loader-services", false, t.toString()));
        }
    }

    private static byte[] trivialClass(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** {@code BootHarness <transformer.jar> <loader.jar>} — non-zero exit on any failed step. */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: BootHarness <transformer.jar> <loader.jar>");
            System.exit(2);
            return;
        }
        Result r = boot(Path.of(args[0]), Path.of(args[1]));
        System.out.println("Aetherium headless boot smoke — loading the SHIPPED jars in isolation:\n");
        for (Step s : r.steps()) {
            System.out.printf("  %s %s — %s%n", s.ok() ? "✓" : "✗", s.name(), s.detail());
        }
        System.out.println();
        if (r.ok()) {
            System.out.println("RESULT: PASS — the boot layer loads and transforms from the shipped artifact.");
        } else {
            System.err.println("RESULT: FAIL — a boot step did not pass (see above).");
            System.exit(1);
        }
    }
}
