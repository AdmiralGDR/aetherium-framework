/*
 * Aetherium Framework — startup boot banner (dependency-free ASCII).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the concise startup banner logged once when Aetherium initializes in-game.
 *
 * <p>EN: A small, framed banner that states the framework version and the live status of each hardware
 * acceleration tier (SIMD, AppCDS, Vulkan, compute tier). Built entirely from plain strings — no ASCII-art
 * dependency — and emitted exactly once at construct time so it never spams the Minecraft log. The status
 * column is computed dynamically from the real probes, so the banner doubles as an at-a-glance health
 * readout.
 *
 * <p>RU: Небольшой обрамлённый баннер с версией фреймворка и текущим статусом каждого уровня аппаратного
 * ускорения (SIMD, AppCDS, Vulkan, уровень вычислений). Построен целиком из обычных строк — без
 * зависимостей для ASCII-арта — и выводится ровно один раз при конструировании, чтобы не засорять лог
 * Minecraft. Колонка статуса вычисляется динамически из реальных зондов.
 */
public final class BootBanner {

    private static final int INNER = 53; // inner width between the side borders

    private BootBanner() {
    }

    /**
     * Dynamic banner inputs.
     *
     * @param version        framework version string
     * @param simdActive     true if the Vector API path is live
     * @param simdBits       SIMD lane width in bits (e.g. 256), 0 if scalar
     * @param appCdsEntries  cached transformed classes; {@code -1} means the cache is disabled
     * @param vulkanReady    true if a Vulkan instance was created during pre-flight
     * @param vulkanDevices  enumerated physical devices
     * @param tier           compute tier name (FFM / JNI / PURE_JAVA / …)
     */
    public record Status(String version, boolean simdActive, int simdBits, int appCdsEntries,
                         boolean vulkanReady, int vulkanDevices, String tier) {
    }

    /** Render the framed banner as a list of log lines (one per {@code LOG.info}). */
    public static List<String> render(Status s) {
        List<String> out = new ArrayList<>();
        out.add("+" + "-".repeat(INNER) + "+");
        out.add(row("  /\\ AETHERIUM", "v" + s.version()));
        out.add(row(" /--\\ universal high-performance modding meta-loader", ""));
        out.add("+" + "-".repeat(INNER) + "+");
        out.add(row("  SIMD Vector API", simd(s)));
        out.add(row("  AppCDS Cache", appcds(s)));
        out.add(row("  Vulkan Compute", vulkan(s)));
        out.add(row("  Compute Tier", "[ " + s.tier() + " ]"));
        out.add("+" + "-".repeat(INNER) + "+");
        return out;
    }

    private static String simd(Status s) {
        return s.simdActive() ? "[ ACTIVE " + s.simdBits() + "-bit ]" : "[ scalar ]";
    }

    private static String appcds(Status s) {
        if (s.appCdsEntries() < 0) {
            return "[ disabled ]";
        }
        return s.appCdsEntries() > 0 ? "[ WARM " + s.appCdsEntries() + " ]" : "[ COLD ]";
    }

    private static String vulkan(Status s) {
        return s.vulkanReady() ? "[ READY " + s.vulkanDevices() + " dev ]" : "[ n/a ]";
    }

    /** Build a bordered line: left-justified label, right-justified value, padded to {@link #INNER}. */
    private static String row(String left, String right) {
        int space = INNER - left.length() - right.length();
        if (space < 1) {
            // truncate the label defensively so the frame never breaks
            int keep = Math.max(0, INNER - right.length() - 1);
            left = left.substring(0, Math.min(left.length(), keep));
            space = Math.max(1, INNER - left.length() - right.length());
        }
        return "|" + left + " ".repeat(space) + right + "|";
    }
}
