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

import java.nio.file.Path;
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
            case "preflight" -> runPreFlight();
            case "chaos" -> runChaos(args);
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
                  preflight          Run the framework Pre-Flight Check (ASM + native + capability tier).
                  chaos [n]          Run the Chaos Engineering stress test (default %d simulated mods).
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

    /** {@code analyze <path>} — static bytecode verification against the target loader baseline. */
    private static int runAnalyze(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            System.err.println("usage: aetherium analyze <path-to-.class-or-.jar-or-dir>");
            return 2;
        }
        Path path = Path.of(args[1]);
        System.out.printf("%s analyze — %s (target: Java 21 / major %d)%n%n",
                TOOL_NAME, path, BytecodeAnalyzer.DEFAULT_TARGET_MAJOR);
        try {
            BytecodeAnalyzer.AnalysisReport report = BytecodeAnalyzer.analyze(path);
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
