/*
 * Aetherium Framework — developer CLI.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli;

import org.aetherium.bytecode.analyze.BytecodeAnalyzer;
import org.aetherium.cli.scaffold.ModScaffolder;
import org.aetherium.loader.PreFlightCheck;
import org.aetherium.testsuite.ChaosHarness;
import org.aetherium.testsuite.ChaosReportRenderer;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Aetherium developer CLI — the primary developer experience.
 *
 * <p><b>EN.</b> Commands: {@code init} (scaffold a ready-to-build, zero-boilerplate mod project),
 * {@code analyze} (statically verify a class/jar against target-loader constraints), {@code selftest}
 * (bytecode-engine end-to-end simulation), {@code preflight} (framework Pre-Flight Check), and
 * {@code chaos} (Chaos Engineering stress test). {@code --help} lists everything.
 *
 * <p><b>RU.</b> Команды: {@code init} (создать готовый к сборке мод-проект без шаблонного кода),
 * {@code analyze} (статически проверить класс/jar против ограничений целевого загрузчика),
 * {@code selftest} (end-to-end симуляция движка байт-кода), {@code preflight} (Pre-Flight Check
 * фреймворка) и {@code chaos} (стресс-тест Chaos Engineering). {@code --help} перечисляет всё.
 */
public final class AetheriumCli {

    private static final String TOOL_NAME = "aetherium";

    private AetheriumCli() {
    }

    public static void main(String[] args) {
        String command = args.length == 0 ? "--help" : args[0];
        int exit = switch (command) {
            case "--help", "-h", "help" -> printHelp();
            case "init" -> runInit(args);
            case "analyze" -> runAnalyze(args);
            case "selftest" -> runSelfTest();
            case "inject" -> runInjectorTest();
            case "simd" -> runSimd();
            case "cdscache" -> runCdsCache(args);
            case "profile" -> runProfile();
            case "security" -> runSecurity();
            case "preflight" -> runPreFlight();
            case "chaos" -> runChaos(args);
            case "entitysim" -> runEntitySim(args);
            default -> {
                System.err.printf("Unknown command '%s'.%n%n", command);
                printHelp();
                yield 2;
            }
        };
        System.exit(exit);
    }

    private static int printHelp() {
        System.out.printf("""
                %s — Aetherium Framework CLI
                Universal, high-performance Minecraft modding meta-layer.

                USAGE
                  aetherium <command> [args]

                COMMANDS
                  init <name>        Scaffold a new Aetherium-compatible mod project (zero boilerplate).
                  analyze <path>     Statically verify a .class / .jar / dir against loader constraints.
                  selftest           Run the bytecode-engine end-to-end simulation.
                  inject             Run the fluent bytecode-injector self-test (Mixin-killer + sandbox).
                  simd               Report the SIMD lane width and verify Vector API == scalar.
                  cdscache           Show the AppCDS zero-parse transformed-class cache status.
                  profile            Verify ephemeral JFR probes (zero overhead off, JFR fires on).
                  security           Verify the capability-based CIA-triad guards (default-deny).
                  preflight          Run the framework Pre-Flight Check (ASM + native + capability tier).
                  chaos [n]          Run the Chaos Engineering stress test (default %d simulated mods).
                  entitysim [n]      Run the data-oriented entity stress test (default 10000 entities).
                  --help, -h, help   Show this help.

                EXAMPLES
                  aetherium init my-mod
                  aetherium analyze build/libs/my-mod.jar
                  aetherium chaos 600

                Licensed under AGPL-3.0-or-later. Generated mod projects inherit this license.
                %n""", TOOL_NAME, ChaosHarness.DEFAULT_MOD_COUNT);
        return 0;
    }

    /** {@code init <name>} — scaffold a mod project under ./<name>. */
    private static int runInit(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            System.err.println("usage: aetherium init <name>");
            return 2;
        }
        String name = args[1];
        try {
            ModScaffolder scaffolder = new ModScaffolder(name);
            // Use the sanitized modId as the directory name so the output path is always valid.
            Path target = Path.of(scaffolder.modId());
            List<Path> created = scaffolder.scaffold(target);
            System.out.printf("Scaffolded Aetherium mod '%s' (modId=%s, mainClass=%s)%n",
                    name, scaffolder.modId(), scaffolder.mainClass());
            created.forEach(p -> System.out.println("  + " + p));
            System.out.printf("%nNext:%n  cd %s && ./gradlew build%n", name);
            System.out.println("  EN: A complete, AGPL-3.0 mod project using the Aetherium APIs — no boilerplate to write.");
            System.out.println("  RU: Полный проект мода под AGPL-3.0 с API Aetherium — шаблонный код писать не нужно.");
            return 0;
        } catch (Exception e) {
            System.err.printf("init failed: %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
            return 1;
        }
    }

    /** {@code analyze <path> [--classpath <cp>]} — static bytecode verification against the baseline. */
    private static int runAnalyze(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            System.err.println("usage: aetherium analyze <path-to-.class-or-.jar-or-dir> [--classpath <cp>]");
            return 2;
        }
        Path path = Path.of(args[1]);
        String classpath = null;
        for (int i = 2; i < args.length - 1; i++) {
            if ("--classpath".equals(args[i]) || "-cp".equals(args[i])) {
                classpath = args[i + 1];
            }
        }
        ClassLoader verifyLoader = buildVerifyLoader(classpath);
        System.out.printf("%s analyze — %s (target: Java 21 / major %d%s)%n%n",
                TOOL_NAME, path, BytecodeAnalyzer.DEFAULT_TARGET_MAJOR,
                verifyLoader != null ? "; classpath-aware" : "");
        try {
            BytecodeAnalyzer.AnalysisReport report =
                    BytecodeAnalyzer.analyze(path, BytecodeAnalyzer.DEFAULT_TARGET_MAJOR, verifyLoader);
            for (BytecodeAnalyzer.ClassResult c : report.classes()) {
                String status = (c.versionOk() && c.verifyOk()) ? "OK " : "BAD";
                System.out.printf("  [%s] %s (major %d)%s%s%n",
                        status, c.className(), c.majorVersion(),
                        c.versionOk() ? "" : " — exceeds target",
                        c.verifyOk() ? "" : " — verify: " + c.verifyError());
            }
            System.out.printf("%n  EN: %d class(es), %d OK, %d problem(s).%n",
                    report.classes().size(), report.okCount(), report.problemCount());
            System.out.printf("  RU: классов: %d, OK: %d, проблем: %d.%n",
                    report.classes().size(), report.okCount(), report.problemCount());
            System.out.printf("%nRESULT: %s%n", report.clean() ? "CLEAN ✓" : "PROBLEMS FOUND ✗");
            return report.clean() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("analyze failed: %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
            return 1;
        }
    }

    private static int runChaos(String[] args) {
        int modCount = ChaosHarness.DEFAULT_MOD_COUNT;
        if (args.length > 1) {
            try {
                modCount = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                System.err.printf("chaos: '%s' is not a number; using default %d.%n", args[1], modCount);
            }
        }
        System.out.printf("%s chaos — Chaos Engineering stress test (%d mods)%n%n", TOOL_NAME, modCount);
        var report = ChaosHarness.run(modCount);
        System.out.println(ChaosReportRenderer.render(report));
        return report.passed() ? 0 : 1;
    }

    /** Build a verification classloader over the given path-separated classpath (or null). */
    private static ClassLoader buildVerifyLoader(String classpath) {
        if (classpath == null || classpath.isBlank()) {
            return null;
        }
        List<URL> urls = new ArrayList<>();
        for (String entry : classpath.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                urls.add(new File(entry).toURI().toURL());
            } catch (MalformedURLException e) {
                System.err.printf("analyze: skipping bad classpath entry '%s'%n", entry);
            }
        }
        // Parent = platform loader so we resolve the supplied classpath + the JDK, but NOT the CLI's
        // own classes (which would mask genuine missing-dependency problems in the analyzed artifact).
        return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    private static int runEntitySim(String[] args) {
        int entities = org.aetherium.testsuite.EntityChaosHarness.DEFAULT_ENTITIES;
        if (args.length > 1) {
            try {
                entities = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                System.err.printf("entitysim: '%s' is not a number; using default %d.%n", args[1], entities);
            }
        }
        System.out.printf("%s entitysim — data-oriented entity stress test (%d entities)%n%n", TOOL_NAME, entities);
        var report = org.aetherium.testsuite.EntityChaosHarness.run(
                entities,
                org.aetherium.testsuite.EntityChaosHarness.DEFAULT_TICKS,
                org.aetherium.testsuite.EntityChaosHarness.DEFAULT_TASKS);
        System.out.println(org.aetherium.testsuite.EntityChaosRenderer.render(report));
        return report.passed() ? 0 : 1;
    }

    private static int runSelfTest() {
        System.out.printf("%s selftest — bytecode engine end-to-end simulation%n%n", TOOL_NAME);
        try {
            org.aetherium.bytecode.selftest.EngineSelfTest.Result result =
                    org.aetherium.bytecode.selftest.EngineSelfTest.run();
            result.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  dispatch lowering : %s (Demo.run() = %d)%n",
                    result.dispatchLoweringOk() ? "OK" : "FAIL", result.observedValue());
            System.out.printf("  fallback safety   : %s%n", result.fallbackOk() ? "OK" : "FAIL");
            if (!result.diagnostics().isEmpty()) {
                System.out.println("\n  diagnostics emitted (expected from the fallback case):");
                result.diagnostics().forEach(d ->
                        System.out.printf("    [%s] %s: %s%n", d.severity(), d.code(), d.message()));
            }
            System.out.printf("%nRESULT: %s%n", result.passed() ? "PASS ✓" : "FAIL ✗");
            return result.passed() ? 0 : 1;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            System.err.printf("selftest crashed: %s: %s%n",
                    failure.getClass().getSimpleName(), failure.getMessage());
            return 1;
        }
    }

    /** {@code inject} — fluent bytecode-injector self-test (programmatic injection + safety sandbox). */
    private static int runInjectorTest() {
        System.out.printf("%s inject — fluent bytecode injector self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.injector.InjectorSelfTest.Result result =
                    org.aetherium.injector.InjectorSelfTest.run();
            result.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  programmatic injection : %s (compute()=%d via %d hook call(s))%n",
                    result.injectionOk() ? "OK" : "FAIL", result.observedValue(), result.hookCalls());
            System.out.printf("  revert on bad bytecode : %s%n", result.revertOnInvalidBytecode() ? "OK" : "FAIL");
            System.out.printf("  revert on cursor miss  : %s%n", result.revertOnCursorMiss() ? "OK" : "FAIL");
            System.out.printf("  method cancellation    : %s (compute()=%d, vanilla 21 bypassed)%n",
                    result.cancellationOk() ? "OK" : "FAIL", result.cancelledValue());
            System.out.printf("  arg read + value cancel: %s (hook saw arg0=%d, doubleIt(10)=15)%n",
                    result.argReadOk() ? "OK" : "FAIL", result.observedArg());
            System.out.printf("  DAG hook ordering      : %s (resolved %s from reversed declaration)%n",
                    result.dagOrderOk() ? "OK" : "FAIL", result.dagOrder());
            System.out.printf("  semantic double-cancel : %s (merged(123)=%d; both hooks ran, mod_b combined mod_a's 7+2)%n",
                    result.mergedDoubleCancelOk() ? "OK" : "FAIL", result.mergedCancelValue());
            System.out.printf("  DAG cycle detection    : %s%n", result.cycleDetected() ? "OK" : "FAIL");
            if (!result.diagnostics().isEmpty()) {
                System.out.println("\n  contained diagnostics (expected from the revert cases):");
                result.diagnostics().forEach(d ->
                        System.out.printf("    [%s] %s: %s%n", d.severity(), d.code(), d.message()));
            }
            System.out.printf("%nRESULT: %s%n", result.passed() ? "PASS ✓" : "FAIL ✗");
            return result.passed() ? 0 : 1;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            System.err.printf("inject self-test crashed: %s: %s%n",
                    failure.getClass().getSimpleName(), failure.getMessage());
            return 1;
        }
    }

    /** {@code simd} — report the SIMD lane width and prove the Vector API path matches scalar. */
    private static int runSimd() {
        System.out.printf("%s simd — SIMD / Vector API self-test%n%n", TOOL_NAME);
        org.aetherium.core.simd.SimdSelfTest.Result r = org.aetherium.core.simd.SimdSelfTest.run();
        r.notes().forEach(note -> System.out.println("  · " + note));
        System.out.println();
        System.out.printf("  Vector API available   : %s%n", r.vectorApiAvailable() ? "yes" : "no (scalar fallback)");
        System.out.printf("  SIMD lane width        : %d-bit (%d floats/op)%n", r.laneBits(), r.laneCount());
        System.out.printf("  heap float[] == scalar : %s%n", r.heapOk() ? "OK" : "FAIL");
        System.out.printf("  off-heap lane == scalar: %s%n", r.laneOk() ? "OK" : "FAIL");
        System.out.printf("  scalar-tail correct    : %s%n", r.tailOk() ? "OK" : "FAIL");
        System.out.printf("  max abs error vs scalar: %s%n", r.maxAbsError());
        System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
        return r.passed() ? 0 : 1;
    }

    /** {@code cdscache [test]} — show the AppCDS cache status, or run the round-trip self-test. */
    private static int runCdsCache(String[] args) {
        System.out.printf("%s cdscache — AppCDS zero-parse transformed-class cache%n%n", TOOL_NAME);
        if (args.length > 1 && "test".equals(args[1])) {
            try {
                org.aetherium.loader.AppCdsSelfTest.Result r = org.aetherium.loader.AppCdsSelfTest.run();
                r.notes().forEach(note -> System.out.println("  · " + note));
                System.out.printf("%n  cold lookup miss       : %s%n", r.coldMiss() ? "OK" : "FAIL");
                System.out.printf("  warm hit after reopen  : %s (zero ASM parse via mmap)%n", r.warmHit() ? "OK" : "FAIL");
                System.out.printf("  stale-bytes invalidated: %s%n", r.staleInvalidated() ? "OK" : "FAIL");
                System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
                return r.passed() ? 0 : 1;
            } catch (Exception e) {
                System.err.printf("cdscache self-test crashed: %s%n", e);
                return 1;
            }
        }
        org.aetherium.loader.AppCdsManager mgr =
                org.aetherium.loader.AppCdsManager.open(org.aetherium.loader.AppCdsManager.defaultDir());
        mgr.stats().lines().forEach(line -> System.out.println("  " + line));
        return 0;
    }

    /** {@code profile} — verify ephemeral JFR probes: absent when off, woven + recording when on. */
    private static int runProfile() {
        System.out.printf("%s profile — ephemeral JFR probe self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.injector.probe.ProbeSelfTest.Result r = org.aetherium.injector.probe.ProbeSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  zero overhead when off : %s (no probe bytecode present)%n", r.zeroOverheadWhenOff() ? "OK" : "FAIL");
            System.out.printf("  woven when on          : %s%n", r.wovenWhenOn() ? "OK" : "FAIL");
            System.out.printf("  JFR event fired        : %s (%d events captured)%n", r.jfrEventFired() ? "OK" : "FAIL", r.eventsCaptured());
            System.out.printf("  live hot-swap available: %s%n", r.instrumentationAvailable() ? "yes (instant retransform)" : "no (load-time weaving)");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("profile self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code security} — verify the capability-based CIA-triad guards. */
    private static int runSecurity() {
        System.out.printf("%s security — capability-based CIA-triad self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.security.SecuritySelfTest.Result r = org.aetherium.security.SecuritySelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  default-deny           : %s%n", r.defaultDenyOk() ? "OK" : "FAIL");
            System.out.printf("  granted capability ok  : %s%n", r.grantedAllowed() ? "OK" : "FAIL");
            System.out.printf("  FFM in-bounds access   : %s%n", r.ffmInBoundsOk() ? "OK" : "FAIL");
            System.out.printf("  FFM out-of-bounds block: %s (Integrity)%n", r.ffmBoundsEnforced() ? "OK" : "FAIL");
            System.out.printf("  internal reflection deny: %s (Confidentiality)%n", r.internalReflectionDenied() ? "OK" : "FAIL");
            System.out.printf("  own-class reflection ok: %s%n", r.ownReflectionAllowed() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("security self-test crashed: %s%n", e);
            return 1;
        }
    }

    private static int runPreFlight() {
        System.out.printf("%s preflight — framework Pre-Flight Check%n%n", TOOL_NAME);
        try {
            PreFlightCheck.Report report = PreFlightCheck.run();
            report.lines().forEach(line -> System.out.println("  " + line));
            System.out.println();
            System.out.printf("  ASM engine     : %s%n", report.asmOk() ? "OK" : "FAIL");
            System.out.printf("  Native bridge  : %s%n", report.nativeHealthy() ? "OK" : "DEGRADED");
            System.out.printf("  Vulkan access  : available=%s devices=%d queueFamilies=%d%n",
                    report.vulkanAvailable(), report.vulkanDeviceCount(), report.vulkanQueueFamilies());
            System.out.printf("  Compute tier   : %s%n", report.tier());
            if (!report.diagnostics().isEmpty()) {
                System.out.println("\n  structured diagnostics:");
                report.diagnostics().forEach(d ->
                        System.out.printf("    [%s] %s%n", d.severity(), d.code()));
            }
            System.out.printf("%nLAUNCH: %s%n", report.launchAllowed() ? "ALLOWED ✓" : "BLOCKED ✗");
            return report.launchAllowed() ? 0 : 1;
        } catch (RuntimeException failure) {
            System.err.printf("preflight crashed unexpectedly: %s: %s%n",
                    failure.getClass().getSimpleName(), failure.getMessage());
            return 1;
        }
    }
}
