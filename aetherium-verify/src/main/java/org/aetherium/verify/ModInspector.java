/*
 * Aetherium Framework — runtime mod inspector (enumerate + verify + analyze).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.verify;

import org.aetherium.core.mod.AetheriumMod;
import org.aetherium.datagen.ContentEntry;
import org.aetherium.datagen.ContentIndex;
import org.aetherium.injector.HookTable;
import org.aetherium.shield.IntegrityManifest;
import org.aetherium.shield.ModVerifier;
import org.aetherium.shield.NativeGuard;
import org.aetherium.shield.WatermarkAttribute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Builds a verifiable, analyzable snapshot of the loaded Aetherium mods — the data behind the in-game
 * inspector and the {@code /aetherium mods|verify} commands.
 *
 * <p>EN: For each {@code AetheriumMod} discovered by {@code ServiceLoader}, it attributes the classes it can
 * (the mod's entrypoint + every content class the {@code content.index} declares for that mod id), verifies
 * each against the merged Shield integrity manifest ({@link ModVerifier}), reads the author from the Shield
 * watermark ({@link WatermarkAttribute}), and counts the declarative content. Pure — no Minecraft — so the
 * whole thing runs headless (that is what {@link ModVerifySelfTest} exercises).
 * RU: Для каждого {@code AetheriumMod} из {@code ServiceLoader} атрибутирует доступные классы (точка входа +
 * классы контента из {@code content.index} для этого modId), проверяет их против слитого манифеста
 * целостности Щита, читает автора из водяного знака и считает контент. Чисто — без Minecraft.
 */
public final class ModInspector {

    private ModInspector() {
    }

    /** Snapshot every loaded mod's verification + analysis state. */
    public static List<ModReport> snapshot(ClassLoader loader) {
        IntegrityManifest manifest = ModVerifier.loadManifest(loader);
        boolean nativeGuard = NativeGuard.get().isNative();

        // Group declared content classes + counts by mod id.
        Map<String, List<String>> contentClasses = new LinkedHashMap<>();
        Map<String, Integer> contentCount = new LinkedHashMap<>();
        for (ContentEntry e : safeContent(loader)) {
            contentClasses.computeIfAbsent(e.modId(), k -> new ArrayList<>()).add(e.className());
            contentCount.merge(e.modId(), 1, Integer::sum);
        }

        List<ModReport> reports = new ArrayList<>();
        for (AetheriumMod mod : ServiceLoader.load(AetheriumMod.class, loader)) {
            String modId = safeId(mod);
            Set<String> classes = new LinkedHashSet<>();
            classes.add(mod.getClass().getName());
            classes.addAll(contentClasses.getOrDefault(modId, List.of()));

            List<String> tampered = new ArrayList<>();
            int signed = 0;
            String author = "";
            boolean watermark = false;
            for (String cn : classes) {
                ModVerifier.Verdict v = ModVerifier.verifyClass(loader, manifest, cn);
                if (v == ModVerifier.Verdict.TAMPERED) {
                    tampered.add(cn);
                } else if (v == ModVerifier.Verdict.INTACT) {
                    signed++;
                }
                if (author.isEmpty()) {
                    byte[] bytes = ModVerifier.readClassBytes(loader, cn);
                    if (bytes != null) {
                        WatermarkAttribute w = WatermarkAttribute.extract(bytes);
                        if (w != null && !w.author().isBlank()) {
                            author = w.author();
                            watermark = true;
                        }
                    }
                }
            }
            ModReport.Verdict verdict = !tampered.isEmpty()
                    ? ModReport.Verdict.TAMPERED
                    : (signed > 0 ? ModReport.Verdict.SIGNED_INTACT : ModReport.Verdict.UNSIGNED);
            reports.add(new ModReport(modId, author, verdict, classes.size(), tampered,
                    contentCount.getOrDefault(modId, 0), watermark, nativeGuard));
        }
        return reports;
    }

    /** Framework-level count of injected hooks (across all mods) — shown in the inspector header. */
    public static int totalInjectedHooks() {
        return HookTable.size() + HookTable.contextSize();
    }

    private static List<ContentEntry> safeContent(ClassLoader loader) {
        try {
            return ContentIndex.load(loader);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static String safeId(AetheriumMod mod) {
        try {
            return mod.id();
        } catch (Throwable t) {
            return mod.getClass().getName();
        }
    }
}
