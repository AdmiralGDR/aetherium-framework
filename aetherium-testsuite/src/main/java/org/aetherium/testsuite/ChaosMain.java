/*
 * Aetherium Framework — chaos suite standalone entry point.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testsuite;

/**
 * Standalone launcher for the Chaos Engineering suite ({@code ./gradlew :aetherium-testsuite:run}).
 *
 * <p>EN: Also reachable via {@code aetherium-cli chaos}. Prints the report and exits non-zero only
 * if the framework failed to contain a hostile input — which, by design, must never happen.
 * RU: Также доступно через {@code aetherium-cli chaos}. Печатает отчёт и завершает работу с
 * ненулевым кодом только если фреймворк не локализовал враждебный вход — чего по дизайну быть не
 * должно.
 */
public final class ChaosMain {

    private ChaosMain() {
    }

    public static void main(String[] args) {
        int modCount = args.length > 0 ? parse(args[0], ChaosHarness.DEFAULT_MOD_COUNT) : ChaosHarness.DEFAULT_MOD_COUNT;
        ChaosReport report = ChaosHarness.run(modCount);
        System.out.println(ChaosReportRenderer.render(report));
        System.exit(report.passed() ? 0 : 1);
    }

    private static int parse(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
