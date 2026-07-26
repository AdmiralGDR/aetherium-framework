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
            case "shield" -> runShield();
            case "guard" -> runGuard();
            case "verify" -> runVerify();
            case "protect" -> runProtect(args);
            case "coexist" -> runCoexist();
            case "acid" -> runAcid();
            case "ttd" -> runTtd();
            case "simd" -> runSimd();
            case "cdscache" -> runCdsCache(args);
            case "profile" -> runProfile();
            case "security" -> runSecurity();
            case "domains" -> runDomains();
            case "contracts" -> runContracts();
            case "spirv" -> runSpirv();
            case "hotswap" -> runHotSwap();
            case "wasm" -> runWasm();
            case "delta" -> runDelta();
            case "fuzz" -> runFuzz(args);
            case "lsp" -> runLsp(args);
            case "ui" -> runUi();
            case "gfx" -> runGfx();
            case "tree" -> runTree();
            case "config" -> runConfig();
            case "behavior" -> runBehavior();
            case "gameplay" -> runGameplay();
            case "doctor" -> runDoctor();
            case "preflight" -> runPreFlight();
            case "computegpu" -> runComputeGpu();
            case "chaos" -> runChaos(args);
            case "entitysim" -> runEntitySim(args);
            case "ffmaudit" -> runFfmAudit(args);
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
                  analyze <path>     Statically verify a .class / .jar / dir (+ @Requires/@Ensures contracts).
                  contracts          Verify hook contract analysis (@Ensures return-sign symbolic checking).
                  selftest           Run the bytecode-engine end-to-end simulation.
                  inject             Run the fluent bytecode-injector self-test (Mixin-killer + sandbox).
                  shield             Prove the sovereign anti-reverse / anti-AI protection (obfuscate + integrity + watermark).
                  guard              Report the Zig native anti-tamper guard (checksum + debugger probe, degrades to pure-Java).
                  verify             Prove in-game mod verification (integrity verdicts + inspector screen).
                  protect <dir>      Shield every .class in a directory in place ([--author "Name"] [--rename]).
                  coexist            Prove two mods' injectors coexist (global hook-id space, no clobber).
                  config             Run the ConfigStore self-test (JSON-over-TreeNode, atomic write, hot-reload).
                  acid               Prove transactional (ACID) hooks: a mod's failing hook rolls back all its hooks.
                  ttd                Run the Time-Travel Debugger self-test (bounded journal + rewind + fault capture).
                  simd               Report the SIMD lane width and verify Vector API == scalar.
                  cdscache           Show the AppCDS zero-parse transformed-class cache status.
                  profile            Verify ephemeral JFR probes (zero overhead off, JFR fires on).
                  security           Verify the capability-based CIA-triad guards (default-deny).
                  domains            Verify FFM memory-domain isolation (cross-mod access denied without a grant).
                  spirv              Compile a Java kernel to SPIR-V and prove the magic word (0x07230203).
                  hotswap            Verify the live class hot-swap engine + live DAG reconciliation.
                  wasm               Verify the polyglot WASM sandbox (deny FS/network) + StructArena bridge.
                  delta              Verify delta-sync networking (dirty bitmap, changed rows only).
                  fuzz [n]           Fuzz the SPIR-V + WASM attack surface (default %d cases/target).
                  lsp [--serve]      Run the LSP backend self-test, or serve LSP over stdio (--serve).
                  ui                 Verify the declarative UI framework (layout + paint + click).
                  gfx                Verify advanced GFX (matrix/pose/skeleton/vertex pipeline).
                  tree               Verify hierarchical TreeCodec sync (NBT/JSON-like, round-trip).
                  behavior           Verify content behaviors (@AetheriumMachineLogic ticking).
                  gameplay           Verify the gameplay PAL (player/inventory/interaction events).
                  doctor             Check this host's readiness for Aetherium's extreme features.
                  preflight          Run the framework Pre-Flight Check (ASM + native + capability tier).
                  computegpu         Dispatch a SPIR-V kernel on a real Vulkan GPU and check GPU == CPU.
                  chaos [n]          Run the Chaos Engineering stress test (default %d simulated mods).
                  entitysim [n]      Run the data-oriented entity stress test (default 10000 entities).
                  ffmaudit [n]       FFM zero-leak audit: churn n entities (default 10000000) through
                                     StructArena; prove release via ledger + NMT + JFR.
                  --help, -h, help   Show this help.

                EXAMPLES
                  aetherium init my-mod
                  aetherium analyze build/libs/my-mod.jar
                  aetherium chaos 600

                Licensed under AGPL-3.0-or-later. Generated mod projects inherit this license.
                %n""", TOOL_NAME, org.aetherium.fuzzer.FuzzerSelfTest.DEFAULT_ITERATIONS,
                ChaosHarness.DEFAULT_MOD_COUNT);
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
            // Consistency pass: scan @Requires/@Ensures hook contracts and statically verify return signs.
            long contractViolations = runContractScan(path);

            boolean clean = report.clean() && contractViolations == 0;
            System.out.printf("%nRESULT: %s%n", clean ? "CLEAN ✓" : "PROBLEMS FOUND ✗");
            return clean ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("analyze failed: %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
            return 1;
        }
    }

    /** Scan every class under {@code path} for hook contracts and print any static verdicts. */
    private static long runContractScan(Path path) {
        long violations = 0;
        int contractedClasses = 0;
        try {
            List<byte[]> classes = readClassBytes(path);
            List<String> lines = new ArrayList<>();
            for (byte[] bytes : classes) {
                org.aetherium.cli.contract.ContractAnalyzer.Report r =
                        org.aetherium.cli.contract.ContractAnalyzer.analyze(bytes);
                if (!r.hasContracts()) {
                    continue;
                }
                contractedClasses++;
                violations += r.violations();
                for (var c : r.contracts()) {
                    String tag = switch (c.verdict()) {
                        case SATISFIED -> "OK  ";
                        case VIOLATED -> "WARN";
                        case UNVERIFIED -> "?   ";
                    };
                    lines.add(String.format("    [%s] %s#%s @Ensures(%s): %s",
                            tag, r.className(), c.methodName(), c.ensures(), c.message()));
                }
            }
            if (contractedClasses > 0) {
                System.out.printf("%n  hook contracts (@Requires/@Ensures): %d class(es), %d warning(s)%n",
                        contractedClasses, violations);
                lines.forEach(System.out::println);
            }
        } catch (Exception e) {
            System.err.printf("  contract scan skipped: %s%n", e.getMessage());
        }
        return violations;
    }

    /** {@code contracts} — verify hook contract analysis (@Ensures return-sign symbolic checking). */
    private static int runContracts() {
        System.out.printf("%s contracts — static hook contract verification self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.cli.contract.ContractSelfTest.Result r =
                    org.aetherium.cli.contract.ContractSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println("\n  per-hook verdicts:");
            r.verdictLines().forEach(line -> System.out.println("      " + line));
            System.out.println();
            System.out.printf("  satisfied contract ok  : %s%n", r.goodSatisfied() ? "OK" : "FAIL");
            System.out.printf("  proven negative warned : %s (return -5 under NON_NEGATIVE)%n",
                    r.negativeViolated() ? "OK" : "FAIL");
            System.out.printf("  zero-vs-POSITIVE warned: %s%n", r.zeroUnderPositiveViolated() ? "OK" : "FAIL");
            System.out.printf("  variable = unverified  : %s (no false alarm)%n", r.variableUnverified() ? "OK" : "FAIL");
            System.out.printf("  @Requires parsed       : %s%n", r.requiresParsed() ? "OK" : "FAIL");
            System.out.printf("  total warnings         : %d%n", r.violations());
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("contracts self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** Read every {@code .class} byte[] under a path (a single class, a directory tree, or a jar). */
    private static List<byte[]> readClassBytes(Path path) throws java.io.IOException {
        List<byte[]> out = new ArrayList<>();
        File f = path.toFile();
        if (f.isDirectory()) {
            try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(path)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (p.toString().endsWith(".class")) {
                        out.add(java.nio.file.Files.readAllBytes(p));
                    }
                }
            }
        } else if (f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(f)) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry e = entries.nextElement();
                    if (!e.isDirectory() && e.getName().endsWith(".class")) {
                        out.add(zip.getInputStream(e).readAllBytes());
                    }
                }
            }
        } else if (f.getName().endsWith(".class")) {
            out.add(java.nio.file.Files.readAllBytes(path));
        }
        return out;
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
        var nmtBefore = org.aetherium.testsuite.NmtMonitor.snapshot();
        var report = ChaosHarness.run(modCount);
        System.out.println(ChaosReportRenderer.render(report));
        printNmtDelta(nmtBefore);
        return report.passed() ? 0 : 1;
    }

    /** Print the native-memory movement across a stress run (capital-debugging telemetry). */
    private static void printNmtDelta(org.aetherium.testsuite.NmtMonitor.Snapshot before) {
        var after = org.aetherium.testsuite.NmtMonitor.snapshot();
        if (!before.available() || !after.available()) {
            System.out.println("\n  NMT: not enabled (-XX:NativeMemoryTracking=summary) — native telemetry skipped");
            return;
        }
        long totalDelta = after.totalCommittedKb() - before.totalCommittedKb();
        long otherDelta = after.otherCommittedKb() - before.otherCommittedKb();
        System.out.printf("%n  NMT native memory     : total committed %,d KB (%+d KB), FFM/'Other' %,d KB (%+d KB)%n",
                after.totalCommittedKb(), totalDelta, after.otherCommittedKb(), otherDelta);
    }

    /** {@code ffmaudit [n]} — the FFM capital-debugging zero-leak proof (ledger + NMT + JFR). */
    private static int runFfmAudit(String[] args) {
        long entities = org.aetherium.testsuite.FfmLeakHarness.DEFAULT_ENTITIES;
        if (args.length > 1) {
            try {
                entities = Math.max(1, Long.parseLong(args[1]));
            } catch (NumberFormatException ignored) {
                System.err.printf("ffmaudit: '%s' is not a number; using default %d.%n", args[1], entities);
            }
        }
        System.out.printf("%s ffmaudit — FFM zero-leak audit (%,d entity lifecycles)%n%n", TOOL_NAME, entities);
        try {
            org.aetherium.testsuite.FfmLeakHarness.Report r =
                    org.aetherium.testsuite.FfmLeakHarness.run(entities);
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  entities churned       : %,d (%d arenas x %d, %,d B each) in %,d ms%n",
                    r.entities(), r.arenaCount(), r.entitiesPerArena(), r.arenaBytes(), r.elapsedMillis());
            System.out.printf("  task failures          : %d%n", r.failures());
            System.out.printf("  ledger balanced        : %s (outstanding arenas %d, outstanding bytes %d)%n",
                    r.ledgerBalanced() ? "OK" : "FAIL",
                    r.ledgerDelta().outstandingArenas(), r.ledgerDelta().outstandingBytes());
            System.out.printf("  ledger exact           : %s (allocated %,d B == freed %,d B == %,d B expected)%n",
                    r.ledgerExact() ? "OK" : "FAIL",
                    r.ledgerDelta().bytesAllocated(), r.ledgerDelta().bytesFreed(), r.totalChurnedBytes());
            if (r.nmtAvailable()) {
                System.out.printf("  NMT 'Other' (FFM)      : %s (%,d -> %,d KB, delta %+d KB, tolerance ±%d KB)%n",
                        r.nmtClean() ? "OK" : "FAIL",
                        r.nmtOtherBeforeKb(), r.nmtOtherAfterKb(), r.nmtOtherDeltaKb(),
                        org.aetherium.testsuite.FfmLeakHarness.NMT_TOLERANCE_KB);
                System.out.printf("  NMT total committed    : %+d KB across the whole churn%n",
                        r.nmtTotalCommittedDeltaKb());
            } else {
                System.out.println("  NMT                    : not enabled — ledger-only proof (still exact)");
            }
            System.out.printf("  JFR NMT timeline       : %d event(s)%s%n", r.jfrEvents(),
                    r.jfrEvents() > 0 ? ", peak committed " + String.format("%,d", r.jfrPeakCommittedKb()) + " KB" : "");
            System.out.printf("%nRESULT: %s%n", r.passed()
                    ? "PASS ✓ (native memory released exactly on close; zero bytes escaped)"
                    : "FAIL ✗ (a leak or failure was detected)");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("ffmaudit crashed: %s%n", e);
            return 1;
        }
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

    /**
     * {@code protect <classesDir> [--author X] [--rename]} — shield every {@code .class} under a directory
     * IN PLACE. Renaming is OFF by default (it would move files and break by-name/service resolution); the
     * always-safe passes (debug-strip, string encryption, control-flow obfuscation, watermark, integrity)
     * run in place. Classes named in {@code META-INF/services} are auto-kept. Writes the integrity manifest
     * to {@code META-INF/aetherium/shield-integrity.txt}.
     */
    private static int runProtect(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: aetherium protect <classesDir> [--author \"Name\"] [--rename] [--out <dir>]");
            return 2;
        }
        java.nio.file.Path dir = java.nio.file.Path.of(args[1]);
        if (!java.nio.file.Files.isDirectory(dir)) {
            System.err.printf("protect: '%s' is not a directory%n", dir);
            return 2;
        }
        String author = "";
        boolean rename = false;
        java.nio.file.Path out = null;
        for (int i = 2; i < args.length; i++) {
            if ("--author".equals(args[i]) && i + 1 < args.length) {
                author = args[++i];
            } else if ("--rename".equals(args[i])) {
                rename = true;
            } else if ("--out".equals(args[i]) && i + 1 < args.length) {
                out = java.nio.file.Path.of(args[++i]);
            }
        }
        System.out.printf("%s protect — shielding %s (rename=%s%s)%n%n", TOOL_NAME, dir, rename,
                out == null ? "" : ", out=" + out);
        try {
            if (out != null) {
                // Two-directory form: the source stays untouched () — the correct, repeatable design.
                org.aetherium.shield.ShieldDirectory.protect(dir, out, author, rename, java.util.List.of());
            } else {
                org.aetherium.shield.ShieldDirectory.protect(dir, author, rename);
            }
            System.out.println("\nRESULT: PROTECTED ✓");
            return 0;
        } catch (Exception e) {
            System.err.printf("protect failed: %s%n", e);
            return 1;
        }
    }

    /** {@code verify} — prove the in-game mod verification stack (integrity verdicts + inspector render). */
    private static int runVerify() {
        System.out.printf("%s verify — in-game mod verification & analysis self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.verify.ModVerifySelfTest.Result r = org.aetherium.verify.ModVerifySelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  SIGNED_INTACT verdict  : %s%n", r.intactVerdict() ? "OK" : "FAIL");
            System.out.printf("  TAMPERED detected      : %s%n", r.tamperedVerdict() ? "OK" : "FAIL");
            System.out.printf("  UNSIGNED recognised    : %s%n", r.unsignedVerdict() ? "OK" : "FAIL");
            System.out.printf("  inspector screen renders: %s%n", r.screenRenders() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("verify self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code guard} — report the Zig native anti-tamper guard status (native or graceful pure-Java fallback). */
    private static int runGuard() {
        System.out.printf("%s guard — sovereign native anti-tamper guard (Zig, zero-dependency)%n%n", TOOL_NAME);
        org.aetherium.shield.NativeGuard g = org.aetherium.shield.NativeGuard.get();
        byte[] probe = "aetherium".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long native_ = g.checksum(probe);
        long java_ = org.aetherium.shield.NativeGuard.fnv1aJava(probe);
        int tracer = g.tracerPid();
        System.out.printf("  backend               : %s%n", g.isNative() ? "NATIVE (libaetherium_guard.so)" : "pure-Java fallback");
        System.out.printf("  ABI version           : %d%n", g.abiVersion());
        System.out.printf("  checksum agreement    : %s (native=%016x, java=%016x)%n",
                native_ == java_ ? "OK" : "MISMATCH", native_, java_);
        System.out.printf("  debugger/agent probe  : tracerPid=%d (%s)%n", tracer,
                tracer > 0 ? "INSTRUMENTED" : tracer == 0 ? "clean" : "unavailable");
        boolean ok = native_ == java_;
        System.out.printf("%nRESULT: %s%n", ok ? "PASS ✓" : "FAIL ✗");
        return ok ? 0 : 1;
    }

    /** {@code shield} — prove the sovereign anti-RE / anti-AI protection (obfuscate → still runs → tamper). */
    private static int runShield() {
        System.out.printf("%s shield — sovereign anti-reverse-engineering / anti-AI protection self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.shield.ShieldSelfTest.Result r = org.aetherium.shield.ShieldSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  string literals hidden : %s (grep/AI sees only ciphertext)%n", r.stringHidden() ? "OK" : "FAIL");
            System.out.printf("  debug metadata stripped: %s%n", r.debugStripped() ? "OK" : "FAIL");
            System.out.printf("  renamed → opaque, runs : %s (%s, compute(20)=%d)%n",
                    (r.renamedButRuns() && r.computeResult() == 41) ? "OK" : "FAIL", r.opaqueName(), r.computeResult());
            System.out.printf("  string decodes at run  : %s%n", r.secretDecodedAtRuntime() ? "OK" : "FAIL");
            System.out.printf("  tamper detected        : %s (integrity manifest)%n", r.tamperDetected() ? "OK" : "FAIL");
            System.out.printf("  author watermark       : %s (leaked jar is traceable)%n", r.watermarkTraceable() ? "OK" : "FAIL");
            System.out.printf("  broken input reverts   : %s (never crashes the build)%n", r.brokenInputReverts() ? "OK" : "FAIL");
            System.out.printf("  decoder left bytecode  : %s (native decrypt — AI sees no XOR loop)%n", r.decoderOutOfBytecode() ? "OK" : "FAIL");
            System.out.printf("  magic constants hidden : %s (AI/decompiler loses its literal anchors)%n", r.constantsObfuscated() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.err.printf("shield self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code config} — prove the ConfigStore lifecycle (defaults, round-trip, validate, hot-reload). */
    private static int runConfig() {
        System.out.printf("%s config — ConfigStore self-test (JSON-over-TreeNode, atomic, hot-reload)%n%n", TOOL_NAME);
        try {
            org.aetherium.config.ConfigSelfTest.Result r = org.aetherium.config.ConfigSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  defaults written       : %s%n", r.wroteDefaults() ? "OK" : "FAIL");
            System.out.printf("  JSON round-trip        : %s%n", r.roundTrip() ? "OK" : "FAIL");
            System.out.printf("  validator clamps value : %s%n", r.validatorClamped() ? "OK" : "FAIL");
            System.out.printf("  hot-reload (WatchSvc)  : %s%n", r.hotReloaded() ? "OK" : "FAIL");
            System.out.printf("  malformed edit contained: %s%n", r.containedBadEdit() ? "OK" : "FAIL");
            System.out.printf("  reload() returns result : %s%n", r.reloadResultOk() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (RuntimeException e) {
            System.err.printf("config self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code coexist} — prove two independent mods' injectors coexist (global hook-id space). */
    private static int runCoexist() {
        System.out.printf("%s coexist — multi-mod injector coexistence self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.injector.CoexistenceSelfTest.Result r =
                    org.aetherium.injector.CoexistenceSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  mod A hook fires       : %s (A.compute()=%d)%n",
                    r.modAFired() ? "OK" : "FAIL", r.modAValue());
            System.out.printf("  mod B hook fires       : %s (B.compute()=%d)%n",
                    r.modBFired() ? "OK" : "FAIL", r.modBValue());
            System.out.printf("  no cross-talk / clobber: %s%n", r.noCrossTalk() ? "OK" : "FAIL");
            System.out.printf("  values preserved       : %s%n", r.valuesPreserved() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            System.err.printf("coexist self-test crashed: %s: %s%n",
                    failure.getClass().getSimpleName(), failure.getMessage());
            return 1;
        }
    }

    /** {@code acid} — prove transactional hook Atomicity: one failing hook rolls back the whole mod. */
    private static int runAcid() {
        System.out.printf("%s acid — transactional (ACID) hook Atomicity self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.injector.txn.TransactionalInjectorSelfTest.Result r =
                    org.aetherium.injector.txn.TransactionalInjectorSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println("\n  transaction log (mod 'gravity_plus'):");
            r.transactionLog().forEach(line -> System.out.println("      " + line));
            System.out.println();
            System.out.printf("  3rd hook fails         : rolled back = %s (verified %d of 3 before abort at %s)%n",
                    r.gravityRolledBack() ? "OK" : "FAIL", r.appliedBeforeAbort(), r.failedClass());
            System.out.printf("  hooks 1 & 2 rolled back: %s (nothing published; run vanilla)%n",
                    (r.gravityPublishedNothing() && r.rolledBackHooksInert()) ? "OK" : "FAIL");
            System.out.printf("  graceful (no crash)    : %s (healthy neighbour mod still committed)%n",
                    (r.speedCommitted() && r.healthyModRuns()) ? "OK" : "FAIL");
            if (!r.diagnostics().isEmpty()) {
                System.out.println("\n  contained diagnostics (the failing hook, not a crash):");
                r.diagnostics().forEach(d ->
                        System.out.printf("    [%s] %s: %s%n", d.severity(), d.code(), d.message()));
            }
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("acid self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code ttd} — Time-Travel Debugger: bounded delta journal, byte-exact rewind, fault capture. */
    private static int runTtd() {
        System.out.printf("%s ttd — Time-Travel Debugger self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.hotswap.ttd.TimeTravelSelfTest.Result r =
                    org.aetherium.hotswap.ttd.TimeTravelSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  bounded journal        : %s (%,d B held / %,d B ceiling, %d frames after %,d ticks)%n",
                    r.footprintBounded() ? "OK" : "FAIL", r.journalBytes(), r.journalMaxBytes(),
                    r.retainedFrames(), r.ticksRun());
            System.out.printf("  byte-exact rewind      : %s%n", r.rewindAccurate() ? "OK" : "FAIL");
            System.out.printf("  clamp past window      : %s%n", r.clampWorks() ? "OK" : "FAIL");
            System.out.printf("  crash scene captured   : %s (tick %d, entity x=%.1f)%n",
                    r.faultCaptured() ? "OK" : "FAIL", r.faultTick(), r.faultCorruptValue());
            System.out.printf("  history intact on fault: %s (faulted tick uncommitted)%n",
                    r.historyIntactAfterFault() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("ttd self-test crashed: %s%n", e);
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

    /** {@code domains} — verify FFM memory-domain isolation (cross-mod access denied without a grant). */
    private static int runDomains() {
        System.out.printf("%s domains — FFM memory-domain isolation self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.security.MemoryDomainSelfTest.Result r =
                    org.aetherium.security.MemoryDomainSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  owner reads own domain : %s%n", r.ownerReadOk() ? "OK" : "FAIL");
            System.out.printf("  cross-mod denied       : %s (Isolation by default)%n",
                    r.crossModDeniedByDefault() ? "OK" : "FAIL");
            System.out.printf("  explicit grant opens   : %s (grantee read owner's value)%n",
                    r.grantedAccessOk() ? "OK" : "FAIL");
            System.out.printf("  revoke re-seals        : %s%n", r.revokeReSeals() ? "OK" : "FAIL");
            System.out.printf("  no-capability denied   : %s (default-deny allocate)%n",
                    r.uncapableCannotAllocate() ? "OK" : "FAIL");
            System.out.printf("  non-owner cannot grant : %s%n", r.nonOwnerCannotGrant() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("domains self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code spirv} — compile a pure-Java kernel to SPIR-V and prove the binary header. */
    private static int runSpirv() {
        System.out.printf("%s spirv — Java→SPIR-V compiler self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.compute.SpirvSelfTest.Result r = org.aetherium.compute.SpirvSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  SPIR-V magic word      : 0x%08X %s%n", r.magicWord(),
                    r.magicOk() ? "(== 0x07230203 ✓)" : "(MISMATCH ✗)");
            System.out.printf("  binary header          : %s%n", r.headerHex());
            System.out.printf("  module size            : %d words (%d bytes)%n", r.wordCount(), r.wordCount() * 4);
            System.out.printf("  structural verify      : %s%n", r.structuralOk() ? "OK" : "FAIL");
            System.out.printf("  native bridge dispatch : %s%n", r.dispatched() ? "OK (accepted)" : "FAIL");
            System.out.printf("  Math.sin → GLSL.std.450: %s%n", r.mathSinMapped() ? "OK (OpExtInst Sin)" : "FAIL");
            System.out.printf("  non-kernel rejected    : %s%n", r.rejectedNonKernel() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("spirv self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code hotswap} — verify the live class hot-swap engine and live DAG reconciliation. */
    private static int runHotSwap() {
        System.out.printf("%s hotswap — live class hot-swap self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.hotswap.HotSwapSelfTest.Result r = org.aetherium.hotswap.HotSwapSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  live Instrumentation   : %s%n",
                    r.instrumentationAvailable() ? "available (instant redefine)" : "absent (degrade to next-launch)");
            System.out.printf("  v1 loaded (value==1)   : %s%n", r.valueBeforeOk() ? "OK" : "FAIL");
            System.out.printf("  redefined live to v2    : %s%n",
                    r.instrumentationAvailable() ? (r.redefineApplied() ? "OK" : "FAIL") : "skipped (no agent)");
            System.out.printf("  value after swap==2    : %s%n",
                    r.instrumentationAvailable() ? (r.valueAfterOk() ? "OK (no restart)" : "FAIL") : "skipped");
            System.out.printf("  DAG reconciled live    : %s (%s → %s)%n",
                    r.dagReconciled() ? "OK" : "FAIL", r.orderBefore(), r.orderAfter());
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("hotswap self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code wasm} — verify the polyglot WASM sandbox security contract and StructArena bridge. */
    private static int runWasm() {
        System.out.printf("%s wasm — polyglot WASM sandbox self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.wasm.WasmSelfTest.Result r = org.aetherium.wasm.WasmSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  GraalWASM installed    : %s%n",
                    r.graalWasmAvailable() ? "yes (real sandbox)" : "no (policy-only mode)");
            System.out.printf("  FS/network denied      : %s (Security)%n", r.ioDenied() ? "OK" : "FAIL");
            System.out.printf("  permissive rejected    : %s%n", r.permissiveRejected() ? "OK" : "FAIL");
            System.out.printf("  .wasm magic validated  : %s%n", r.moduleValidated() ? "OK" : "FAIL");
            System.out.printf("  non-wasm rejected      : %s%n", r.nonWasmRejected() ? "OK" : "FAIL");
            System.out.printf("  StructArena bridge     : %s%n", r.bridgeRoundTrip() ? "OK" : "FAIL");
            System.out.printf("  sandboxed physics      : %s (x += vx)%n", r.physicsCorrect() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("wasm self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code delta} — verify delta-sync transmits only changed rows and reconstructs the client exactly. */
    private static int runDelta() {
        System.out.printf("%s delta — delta-sync networking self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.network.DeltaSyncSelfTest.Result r = org.aetherium.network.DeltaSyncSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  entities               : %d%n", r.entities());
            System.out.printf("  full sync bytes        : %d%n", r.fullBytes());
            System.out.printf("  delta dirty rows       : %d%n", r.deltaDirtyRows());
            System.out.printf("  delta sync bytes       : %d (%d%% saved)%n", r.deltaBytes(), r.savingsPercent());
            System.out.printf("  client matches server  : %s%n",
                    (r.firstSyncMatched() && r.deltaSyncMatched()) ? "OK (byte-exact)" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("delta self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code fuzz [n]} — run the aggressive SPIR-V + WASM fuzzing campaign ({@code n} cases/target). */
    private static int runFuzz(String[] args) {
        int iterations = org.aetherium.fuzzer.FuzzerSelfTest.DEFAULT_ITERATIONS;
        if (args.length > 1) {
            try {
                iterations = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                System.err.printf("fuzz: '%s' is not a number; using default %d.%n", args[1], iterations);
            }
        }
        System.out.printf("%s fuzz — aggressive SPIR-V + WASM fuzzing campaign (%d cases/target)%n%n",
                TOOL_NAME, iterations);
        var nmtBefore = org.aetherium.testsuite.NmtMonitor.snapshot();
        try {
            org.aetherium.fuzzer.FuzzerSelfTest.Result r =
                    org.aetherium.fuzzer.FuzzerSelfTest.run(iterations, System.nanoTime());
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  total cases            : %,d%n", r.totalCases());
            System.out.printf("  clean rejections       : %,d (reject paths reached)%n", r.totalRejected());
            System.out.printf("  unexpected crashes     : %d%n", r.findings().size());
            if (!r.findings().isEmpty()) {
                System.out.println("\n  CRASHES (reproducible by seed):");
                r.findings().forEach(f -> System.out.println("    ✗ " + f));
            }
            printNmtDelta(nmtBefore);
            System.out.printf("%nRESULT: %s%n", r.passed()
                    ? "PASS ✓ (JVM/host never crashed)" : "FAIL ✗ (a malformed input crashed the target)");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("fuzz crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code lsp [--serve]} — LSP backend self-test, or the real stdio JSON-RPC server with --serve. */
    private static int runLsp(String[] args) {
        boolean serve = args.length > 1 && "--serve".equals(args[1]);
        if (serve) {
            // Real Language Server mode: speak JSON-RPC over stdio until the editor sends `exit`.
            try {
                new org.aetherium.cli.lsp.AetheriumLspServer(new org.aetherium.cli.lsp.LspBackend())
                        .serve(System.in, System.out);
                return 0;
            } catch (Exception e) {
                System.err.printf("lsp server failed: %s%n", e);
                return 1;
            }
        }
        System.out.printf("%s lsp — Language Server backend self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.cli.lsp.LspSelfTest.Result r = org.aetherium.cli.lsp.LspSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  known injection points : %d (autocomplete catalogue)%n", r.knownTargets());
            System.out.printf("  completion             : %s (%d items for 'tick')%n",
                    r.completionOk() ? "OK" : "FAIL", r.completionCount());
            System.out.printf("  ordering cycle caught  : %s (before compile)%n", r.cycleDetected() ? "OK" : "FAIL");
            System.out.printf("  clean set passes       : %s%n", r.cleanOk() ? "OK" : "FAIL");
            System.out.printf("  competing-cancel warn  : %s%n", r.cancelWarned() ? "OK" : "FAIL");
            System.out.printf("  JSON-RPC round-trip    : %s (Content-Length framing)%n", r.rpcOk() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            System.out.println("\n  Tip: `aetherium lsp --serve` runs the real LSP over stdio for your IDE.");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("lsp self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code ui} — verify the declarative UI framework (layout + paint + click, offline). */
    /** {@code computegpu} — dispatch a SPIR-V kernel on a real Vulkan compute queue and check GPU == CPU. */
    private static int runComputeGpu() {
        System.out.printf("%s computegpu — real Vulkan compute dispatch (Zig, dependency-free)%n%n", TOOL_NAME);
        try {
            org.aetherium.compute.ComputeGpuSelfTest.Result r = org.aetherium.compute.ComputeGpuSelfTest.run();
            System.out.printf("  kernel dispatched      : %s%n", r.ran() ? "OK" : "FAIL");
            System.out.printf("  ran on GPU device      : %s%n", r.gpuUsed() ? "yes" : "no (CPU fallback)");
            System.out.printf("  elements               : %d%n", r.elements());
            System.out.printf("  GPU vs CPU max diff    : %s%n", r.maxDiff());
            System.out.printf("  %s%n", r.note());
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("computegpu self-test crashed: %s%n", e);
            return 1;
        }
    }

    private static int runUi() {
        System.out.printf("%s ui — declarative GUI framework self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.ui.UiSelfTest.Result r = org.aetherium.ui.UiSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  flex layout            : %s%n", r.layoutOk() ? "OK" : "FAIL");
            System.out.printf("  buttons sized (grow)   : %s%n", r.buttonsLaidOut() ? "OK" : "FAIL");
            System.out.printf("  paint commands         : %s (%d fills, %d text)%n",
                    r.paintOk() ? "OK" : "FAIL", r.fillCount(), r.textCount());
            System.out.printf("  click dispatch         : %s%n", r.clickOk() ? "OK" : "FAIL");
            System.out.printf("  keyboard input + focus : %s%n", r.textInputOk() ? "OK" : "FAIL");
            System.out.printf("  scroll + clip          : %s%n", r.scrollOk() ? "OK" : "FAIL");
            System.out.printf("  flex-shrink + audit    : %s%n", (r.shrinkOk() && r.auditCatches()) ? "OK" : "FAIL");
            System.out.printf("  scroll pos on rebuild  : %s%n", r.scrollRestoreOk() ? "OK" : "FAIL");
            System.out.printf("  UI (align/audit/scrollbar): %s%n", r.roundThreeUiOk() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("ui self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code gfx} — verify the matrix/pose/skeleton/vertex pipeline (offline). */
    private static int runGfx() {
        System.out.printf("%s gfx — advanced rendering pipeline self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.gfx.GfxSelfTest.Result r = org.aetherium.gfx.GfxSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  matrix transforms      : %s%n", r.matrixOk() ? "OK" : "FAIL");
            System.out.printf("  PoseStack push/pop     : %s%n", r.poseOk() ? "OK" : "FAIL");
            System.out.printf("  skeleton kinematics    : %s%n", r.skeletonOk() ? "OK" : "FAIL");
            System.out.printf("  vertex mesh emit       : %s (%d vertices)%n",
                    r.meshOk() ? "OK" : "FAIL", r.verticesEmitted());
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("gfx self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code tree} — verify hierarchical TreeCodec sync (round-trip + depth guard). */
    private static int runTree() {
        System.out.printf("%s tree — hierarchical (NBT/JSON-like) sync self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.network.TreeSyncSelfTest.Result r = org.aetherium.network.TreeSyncSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  faction tree round-trip: %s (%d bytes)%n", r.roundTripOk() ? "OK" : "FAIL", r.wireBytes());
            System.out.printf("  typed accessors        : %s%n", r.accessorsOk() ? "OK" : "FAIL");
            System.out.printf("  depth guard (hardening): %s%n", r.depthGuarded() ? "OK" : "FAIL");
            System.out.printf("  namespace guard (multi-mod): %s%n", r.namespaceGuarded() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("tree self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code behavior} — verify content behaviors (@AetheriumMachineLogic ticking + index). */
    private static int runBehavior() {
        System.out.printf("%s behavior — content-behavior self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.content.ContentBehaviorSelfTest.Result r =
                    org.aetherium.content.ContentBehaviorSelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  machine-logic ticking  : %s (%d smelts)%n", r.tickingOk() ? "OK" : "FAIL", r.smeltCount());
            System.out.printf("  behavior index         : %s%n", r.indexOk() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("behavior self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code gameplay} — verify the gameplay PAL (player/inventory/interaction events). */
    private static int runGameplay() {
        System.out.printf("%s gameplay — gameplay PAL self-test%n%n", TOOL_NAME);
        try {
            org.aetherium.edge.EdgeGameplaySelfTest.Result r = org.aetherium.edge.EdgeGameplaySelfTest.run();
            r.notes().forEach(note -> System.out.println("  · " + note));
            System.out.println();
            System.out.printf("  inventory access       : %s%n", r.inventoryOk() ? "OK" : "FAIL");
            System.out.printf("  player handle          : %s%n", r.playerOk() ? "OK" : "FAIL");
            System.out.printf("  interaction cancel     : %s%n", r.interactionOk() ? "OK" : "FAIL");
            System.out.printf("  lifecycle events       : %s%n", r.lifecycleOk() ? "OK" : "FAIL");
            System.out.printf("  command registration   : %s%n", r.commandsOk() ? "OK" : "FAIL");
            System.out.printf("  world persistence      : %s%n", r.persistenceOk() ? "OK" : "FAIL");
            System.out.printf("%nRESULT: %s%n", r.passed() ? "PASS ✓" : "FAIL ✗");
            return r.passed() ? 0 : 1;
        } catch (Exception e) {
            System.err.printf("gameplay self-test crashed: %s%n", e);
            return 1;
        }
    }

    /** {@code doctor} — scan the host for readiness to run Aetherium's extreme features. */
    private static int runDoctor() {
        System.out.printf("%s doctor — environment health check%n%n", TOOL_NAME);
        java.util.List<String> jvmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();

        // (1) Java 21+
        int feature = Runtime.version().feature();
        boolean javaOk = feature >= 21;
        printCheck(javaOk, "Java 21+",
                Runtime.version() + " (" + System.getProperty("java.vendor") + ")");

        // (2) --enable-preview (required for the FFM preview API on 21)
        boolean previewOk = jvmArgs.contains("--enable-preview");
        printCheck(previewOk, "--enable-preview",
                previewOk ? "enabled" : "absent — FFM (java.lang.foreign) is preview on 21");

        // (3) Vector API incubator (SIMD acceleration)
        boolean vectorOk = org.aetherium.core.simd.SimdMath.isVectorApiAvailable();
        printCheck(vectorOk, "Vector API (SIMD)",
                vectorOk ? org.aetherium.core.simd.SimdMath.simdFloatBits() + "-bit lanes"
                         : "absent — add --add-modules=jdk.incubator.vector (falls back to scalar)");

        // (4) FFM native access — restricted downcalls + a live off-heap allocation
        boolean nativeFlag = jvmArgs.stream().anyMatch(a -> a.startsWith("--enable-native-access"));
        boolean ffmWorks;
        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            arena.allocate(64).set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 1);
            ffmWorks = true;
        } catch (Throwable t) {
            ffmWorks = false;
        }
        printCheck(ffmWorks && nativeFlag, "FFM native access",
                (ffmWorks ? "off-heap allocation OK" : "off-heap allocation FAILED")
                        + "; --enable-native-access " + (nativeFlag ? "granted" : "absent (downcalls warn)"));

        // (5) GraalWASM polyglot runtime (optional — enables Rust/C/Go .wasm mods)
        boolean graalWasm = org.aetherium.wasm.WasmSandbox.graalWasmInstalled();
        printCheck(graalWasm, "GraalWASM polyglot",
                graalWasm ? "installed — .wasm mods run in the strict sandbox (FS/network denied)"
                          : "absent — optional; install GraalVM polyglot+wasm for Rust/C/Go mods");

        boolean ready = javaOk && previewOk && vectorOk && ffmWorks;
        System.out.printf("%nDIAGNOSIS: %s%n", ready
                ? "READY — this host can run every Aetherium acceleration."
                : "NEEDS ATTENTION — some accelerations will fall back (see hints above).");
        return ready ? 0 : 1;
    }

    private static void printCheck(boolean ok, String label, String detail) {
        String dots = ".".repeat(Math.max(2, 22 - label.length()));
        System.out.printf("  [ %s ] %s %s %s%n", ok ? "OK " : "WARN", label, dots, detail);
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
