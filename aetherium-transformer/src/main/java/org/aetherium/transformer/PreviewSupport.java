/*
 * Aetherium Framework — JVM preview-flag detection (c).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.transformer;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Answers "was this JVM launched with {@code --enable-preview}?" — from the earliest Aetherium code that
 * runs (the boot-layer transformation service), before any preview-stamped class is touched.
 *
 * <p>EN: c: the framework's high-performance surface (off-heap {@code StructArena}, the Vector
 * API, the native bridge) is built on the Java 21 FFM <em>preview</em> API, so those classes carry
 * class-file minor {@code 0xFFFF} and throw {@code UnsupportedClassVersionError} on a JVM without the flag.
 * No vanilla launcher passes it, and the {@code Enable-Preview} manifest attribute enables nothing at
 * runtime. This class is pure ({@code 0x0000}, no FFM), so it loads anywhere and lets the transformer print
 * a clear, actionable message instead of letting a player hit an opaque stack trace deep in class loading.
 * Detection reads the JVM input arguments via {@link ManagementFactory} — the flag is a VM argument, so it
 * appears there (never in the program arguments).
 * RU: c: высокопроизводительная поверхность фреймворка (off-heap {@code StructArena}, Vector API, нативный
 * мост) построена на <em>preview</em>-API FFM Java 21, поэтому такие классы имеют minor {@code 0xFFFF} и
 * бросают {@code UnsupportedClassVersionError} на JVM без флага. Ни один ванильный лаунчер его не передаёт, а
 * атрибут манифеста {@code Enable-Preview} ничего не включает в рантайме. Класс чист ({@code 0x0000}, без
 * FFM), грузится где угодно и позволяет напечатать понятное сообщение вместо тёмного стека при загрузке.
 * Определение читает аргументы JVM через {@link ManagementFactory} — флаг является VM-аргументом.
 */
public final class PreviewSupport {

    private PreviewSupport() {
    }

    private static final boolean ENABLED = detect();

    /** True iff the JVM was launched with {@code --enable-preview}. Computed once. */
    public static boolean enabled() {
        return ENABLED;
    }

    private static boolean detect() {
        try {
            List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : jvmArgs) {
                if (arg.equals("--enable-preview") || arg.startsWith("--enable-preview")) {
                    return true;
                }
            }
        } catch (Throwable restricted) {
            // A locked-down SecurityManager/agent can deny the MXBean; treat as "unknown → off" and let
            // the advisory print. This never throws into the launch pipeline.
        }
        return false;
    }

    /** One-line English advisory shown when the flag is absent (features degrade, launch continues). */
    public static String advisoryEnglish() {
        return "[AE-JAVA-002] Aetherium's high-performance features (off-heap StructArena, the Vector API, "
                + "the native bridge) need the Java 21 preview flag. Add \"--enable-preview\" to your JVM "
                + "arguments to enable them. The framework still loads; those features stay disabled until "
                + "the flag is present.";
    }

    /** One-line Russian advisory (bilingual-docs rule) — same content as {@link #advisoryEnglish()}. */
    public static String advisoryRussian() {
        return "[AE-JAVA-002] Высокопроизводительные возможности Aetherium (off-heap StructArena, Vector API, "
                + "нативный мост) требуют флага preview Java 21. Добавьте \"--enable-preview\" в аргументы JVM, "
                + "чтобы включить их. Фреймворк всё равно загрузится; эти возможности останутся отключёнными, "
                + "пока флаг отсутствует.";
    }
}
