/*
 * Aetherium Framework — best-effort self-attach to acquire Instrumentation on demand.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.probe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Loads {@link AetheriumProbeAgent} into the <em>current</em> JVM via the Attach API to obtain a live
 * {@link Instrumentation} — entirely reflectively, so the framework never hard-depends on
 * {@code jdk.attach} and degrades cleanly when self-attach is forbidden.
 *
 * <p>EN: An ephemeral profile must be able to start with no agent pre-configured. This synthesizes a
 * minimal agent jar (manifest {@code Agent-Class} + {@code Can-Retransform-Classes: true}, plus the
 * already-compiled {@link AetheriumProbeAgent} class bytes) and attaches it to this process. The Attach
 * API is reached by reflection ({@code com.sun.tools.attach.VirtualMachine}) so a missing/locked-down
 * {@code jdk.attach}, or a JVM started without {@code -Djdk.attach.allowAttachSelf=true}, simply yields
 * {@code null} instead of a hard failure. {@link DynamicProbeController} then falls back to load-time
 * weaving.
 *
 * <p>RU: Эфемерный профиль должен запускаться без заранее настроенного агента. Здесь синтезируется
 * минимальный jar агента (манифест {@code Agent-Class} + {@code Can-Retransform-Classes: true} и
 * скомпилированные байты {@link AetheriumProbeAgent}) и подключается к текущему процессу. Attach API
 * вызывается рефлексивно, поэтому отсутствие/блокировка {@code jdk.attach} или запуск без
 * {@code -Djdk.attach.allowAttachSelf=true} дают {@code null}, а не жёсткий сбой.
 */
final class SelfAttach {

    private SelfAttach() {
    }

    static Instrumentation tryAcquire() {
        try {
            Path agentJar = buildAgentJar();
            String pid = String.valueOf(ProcessHandle.current().pid());

            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);
            try {
                vmClass.getMethod("loadAgent", String.class).invoke(vm, agentJar.toString());
            } finally {
                vmClass.getMethod("detach").invoke(vm);
            }
            return AetheriumProbeAgent.INSTRUMENTATION;
        } catch (Throwable lockedDown) {
            // No jdk.attach, allowAttachSelf=false, or a security policy: degrade to load-time weaving.
            return null;
        }
    }

    private static Path buildAgentJar() throws IOException {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        main.putValue("Agent-Class", AetheriumProbeAgent.class.getName());
        main.putValue("Can-Retransform-Classes", "true");
        main.putValue("Can-Redefine-Classes", "true");

        Path jar = Files.createTempFile("aetherium-probe-agent", ".jar");
        jar.toFile().deleteOnExit();

        String entryName = AetheriumProbeAgent.class.getName().replace('.', '/') + ".class";
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest);
             InputStream agentClass = AetheriumProbeAgent.class.getClassLoader().getResourceAsStream(entryName)) {
            if (agentClass == null) {
                throw new IOException("agent class bytes not found: " + entryName);
            }
            out.putNextEntry(new ZipEntry(entryName));
            agentClass.transferTo(out);
            out.closeEntry();
        }
        return jar;
    }
}
