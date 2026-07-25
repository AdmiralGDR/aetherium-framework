/*
 * Aetherium Framework — in-game mod inspector screen.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.verify;

import org.aetherium.ui.AetheriumScreen;
import org.aetherium.ui.AlignItems;
import org.aetherium.ui.Container;
import org.aetherium.ui.ScrollPanel;
import org.aetherium.ui.Ui;
import org.aetherium.ui.UiColor;
import org.aetherium.ui.Widget;

import java.util.List;

/**
 * The in-game inspector: a scrollable, font-accurate list of every loaded mod with its integrity verdict,
 * author, class count and content count. This is what lets a player <em>verify and analyze mods right in the
 * game</em> — built on the pure {@code aetherium-ui} (so it renders through the real loader adapter) and the
 * runtime verification of {@link ModInspector}. A tampered mod is shown in red.
 *
 * <p>EN: The {@link ScrollPanel} is a persistent field so its scroll position survives the per-frame
 * {@code build()} (the fix); each frame we just swap its content. Pure — no Minecraft — so the
 * screen lays out and hit-tests headless in the self-test.
 * RU: {@link ScrollPanel} — постоянное поле, чтобы позиция прокрутки переживала покадровый {@code build()}
 * (исправление ); каждый кадр меняем только содержимое. Чисто — без Minecraft.
 */
public final class AetheriumModInspectorScreen extends AetheriumScreen {

    private static final UiColor INTACT = UiColor.rgb(0x66DD88);
    private static final UiColor TAMPERED = UiColor.rgb(0xEE5555);
    private static final UiColor UNSIGNED = UiColor.rgb(0xAAAAAA);
    private static final UiColor HEADER = UiColor.rgb(0xFFE082);

    private final List<ModReport> reports;
    private final ScrollPanel scroll = Ui.scroll(Ui.column());

    public AetheriumModInspectorScreen(List<ModReport> reports) {
        this.reports = List.copyOf(reports);
    }

    @Override
    public String title() {
        return "Aetherium — Mod Inspector";
    }

    @Override
    public Widget<?> build() {
        Container list = Ui.column().gap(2);
        for (ModReport r : reports) {
            list.add(Ui.label(row(r)).color(colorFor(r)));
        }
        scroll.child(list);

        String guard = reports.stream().anyMatch(ModReport::nativeGuard) ? "native" : "java";
        String header = "Aetherium Mod Inspector — " + reports.size() + " mod(s), "
                + ModInspector.totalInjectedHooks() + " hooks, guard=" + guard;

        return Ui.column().padding(6).gap(4).align(AlignItems.STRETCH)
                .background(UiColor.rgb(0x14141A))
                .children(
                        Ui.label(header).color(HEADER),
                        scroll.grow(1f));
    }

    private static String row(ModReport r) {
        String mark = switch (r.verdict()) {
            case SIGNED_INTACT -> "✓"; // ✓
            case TAMPERED -> "✗";      // ✗
            case UNSIGNED -> "•";      // •
        };
        String author = r.author().isBlank() ? "unknown" : r.author();
        return mark + " " + r.modId() + "  —  by " + author + "  [" + r.verdict() + "]  "
                + r.classesChecked() + " cls, " + r.contentCount() + " content"
                + (r.tamperedClasses().isEmpty() ? "" : "  (" + r.tamperedClasses().size() + " tampered!)");
    }

    private static UiColor colorFor(ModReport r) {
        return switch (r.verdict()) {
            case SIGNED_INTACT -> INTACT;
            case TAMPERED -> TAMPERED;
            case UNSIGNED -> UNSIGNED;
        };
    }
}
