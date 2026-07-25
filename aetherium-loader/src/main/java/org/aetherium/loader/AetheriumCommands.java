/*
 * Aetherium Framework — the built-in /aetherium command (in-game verification & analysis).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import org.aetherium.edge.EdgeCommands;
import org.aetherium.edge.InteractionResult;
import org.aetherium.edge.PlayerHandle;
import org.aetherium.ui.AetheriumUi;
import org.aetherium.verify.AetheriumModInspectorScreen;
import org.aetherium.verify.ModInspector;
import org.aetherium.verify.ModReport;

import java.util.List;

/**
 * Registers the framework's own {@code /aetherium <mods|verify|inspect>} command — the in-game surface for
 * verifying and analyzing the loaded mods (the request to "check and analyze mods right in the game").
 *
 * <p>EN: {@code mods} lists every loaded mod with its integrity verdict, author and content count in chat;
 * {@code verify} summarises integrity (and flags any tampered mod); {@code inspect} (client) opens the
 * scrollable {@link AetheriumModInspectorScreen} through the real loader UI adapter. Built on the pure
 * {@link EdgeCommands}/{@link AetheriumUi} SPIs — no Brigadier or Minecraft type leaks here.
 * RU: Регистрирует собственную команду {@code /aetherium <mods|verify|inspect>} — внутриигровая поверхность
 * для проверки и анализа загруженных модов. {@code mods}/{@code verify} — в чат; {@code inspect} (клиент) —
 * открывает прокручиваемый экран-инспектор через настоящий UI-адаптер загрузчика.
 */
final class AetheriumCommands {

    private final ClassLoader loader;

    AetheriumCommands(ClassLoader loader) {
        this.loader = loader;
    }

    /** Register the command on the given command surface (level 2 — an operator affordance). */
    void register(EdgeCommands commands) {
        commands.register("aetherium",
                EdgeCommands.CommandSpec.of(2, "Aetherium mod verification & analysis", EdgeCommands.ArgType.WORD),
                this::run);
    }

    private InteractionResult run(PlayerHandle sender, List<String> args) {
        String sub = args.isEmpty() ? "mods" : args.get(0).toLowerCase(java.util.Locale.ROOT);
        List<ModReport> reports;
        try {
            reports = ModInspector.snapshot(loader);
        } catch (Throwable t) {
            reply(sender, "aetherium: inspection failed (" + t.getClass().getSimpleName() + ")");
            return InteractionResult.CANCEL;
        }
        return switch (sub) {
            case "verify" -> verify(sender, reports);
            case "inspect" -> inspect(sender, reports);
            default -> listMods(sender, reports);
        };
    }

    private InteractionResult listMods(PlayerHandle sender, List<ModReport> reports) {
        reply(sender, "Aetherium — " + reports.size() + " mod(s), " + ModInspector.totalInjectedHooks() + " hook(s):");
        for (ModReport r : reports) {
            reply(sender, "  " + mark(r) + " " + r.modId() + " — by " + author(r) + " [" + r.verdict() + "] "
                    + r.classesChecked() + " cls, " + r.contentCount() + " content");
        }
        return InteractionResult.PASS;
    }

    private InteractionResult verify(PlayerHandle sender, List<ModReport> reports) {
        long tampered = reports.stream().filter(r -> r.verdict() == ModReport.Verdict.TAMPERED).count();
        long signed = reports.stream().filter(ModReport::intact).count();
        reply(sender, "Aetherium integrity: " + signed + " signed-intact, " + tampered + " TAMPERED, "
                + (reports.size() - signed - tampered) + " unsigned.");
        for (ModReport r : reports) {
            if (r.verdict() == ModReport.Verdict.TAMPERED) {
                reply(sender, "  ✗ " + r.modId() + " tampered: " + r.tamperedClasses());
            }
        }
        return tampered == 0 ? InteractionResult.PASS : InteractionResult.CANCEL;
    }

    private InteractionResult inspect(PlayerHandle sender, List<ModReport> reports) {
        if (!AetheriumUi.isAvailable()) {
            reply(sender, "aetherium inspect: no client display (run it in-game on the client).");
            return listMods(sender, reports);
        }
        AetheriumUi.open(new AetheriumModInspectorScreen(reports));
        return InteractionResult.PASS;
    }

    private static void reply(PlayerHandle sender, String message) {
        if (sender != null) {
            sender.sendMessage(message);
        } else {
            org.slf4j.LoggerFactory.getLogger("Aetherium").info(message);
        }
    }

    private static String mark(ModReport r) {
        return switch (r.verdict()) {
            case SIGNED_INTACT -> "✓";
            case TAMPERED -> "✗";
            case UNSIGNED -> "•";
        };
    }

    private static String author(ModReport r) {
        return r.author().isBlank() ? "unknown" : r.author();
    }
}
