package org.aetherium.cli;

/**
 * Aetherium developer CLI &amp; IDE-tooling entry point — <strong>placeholder</strong>.
 *
 * <p><b>EN.</b> This is the foundation-phase stub for the Aetherium command-line tool.
 * It will eventually drive mod inspection, loader-target selection, transform dry-runs,
 * and diagnostics export. For now it only prints environment and capability info so the
 * module compiles, runs, and proves the toolchain (GraalVM 21, {@code --enable-preview}).
 * Nothing here is hardcoded as final behaviour; commands are intentionally absent until
 * the {@code aetherium-core} contracts land.
 *
 * <p><b>RU.</b> Это заглушка этапа основания для инструмента командной строки Aetherium.
 * В дальнейшем он будет управлять инспекцией модов, выбором целевого загрузчика, пробными
 * прогонами трансформаций и экспортом диагностики. Сейчас он лишь печатает информацию об
 * окружении и возможностях, чтобы модуль компилировался, запускался и подтверждал тулчейн
 * (GraalVM 21, {@code --enable-preview}). Здесь ничего не зашито как финальное поведение;
 * команды намеренно отсутствуют до появления контрактов {@code aetherium-core}.
 */
public final class AetheriumCli {

    /** Tool identity; the version is a placeholder until {@code aetherium-core} owns it. */
    private static final String TOOL_NAME = "aetherium";
    private static final String PHASE = "foundation (no commands yet)";

    private AetheriumCli() {
        // Utility entry point; not instantiable.
    }

    public static void main(String[] args) {
        // Foundation-phase behaviour: report environment and exit cleanly.
        // Real argument parsing arrives with the core API; we do not guess a CLI
        // grammar now to avoid hardcoding choices we will have to undo.
        System.out.printf("%s — Aetherium Framework CLI%n", TOOL_NAME);
        System.out.printf("  phase   : %s%n", PHASE);
        System.out.printf("  java    : %s (%s)%n",
                System.getProperty("java.version"),
                System.getProperty("java.vm.name"));
        System.out.printf("  preview : %s%n", previewEnabled() ? "enabled" : "disabled");
        System.out.printf("  os/arch : %s / %s%n",
                System.getProperty("os.name"),
                System.getProperty("os.arch"));

        if (args.length > 0) {
            // Strict, honest error handling: refuse unknown input rather than pretend.
            System.err.printf("%nNo commands are implemented yet (phase: %s).%n", PHASE);
            System.err.printf("Received %d argument(s); ignoring. See ARCHITECTURE.md.%n",
                    args.length);
        }
    }

    /**
     * Best-effort probe of whether class-file preview features are enabled. This is a
     * heuristic only; the authoritative gate is the build's {@code --enable-preview}.
     */
    private static boolean previewEnabled() {
        // The JVM exposes no portable public API for this; we report unknown-safe.
        // Centralised detection will move into aetherium-core's capability layer.
        return Runtime.version().feature() >= 21
                && Boolean.parseBoolean(System.getProperty("aetherium.preview", "true"));
    }
}
