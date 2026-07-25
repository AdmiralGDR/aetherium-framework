/*
 * Aetherium Framework — a per-mod verification report.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.verify;

import java.util.List;

/**
 * The verified, analyzable snapshot of one loaded Aetherium mod — what the in-game inspector and the
 * {@code /aetherium mods} command display.
 *
 * @param modId           the mod's id (from its {@code AetheriumMod})
 * @param author          the author from the Shield watermark, or {@code ""} if unsigned
 * @param verdict         the aggregate integrity verdict over the classes we could attribute to this mod
 * @param classesChecked  how many classes were verified
 * @param tamperedClasses the classes whose bytes no longer match the integrity manifest (empty if clean)
 * @param contentCount    how many declarative content pieces (@AetheriumBlock/@AetheriumItem) the mod ships
 * @param watermarkPresent whether an author watermark was found
 * @param nativeGuard     whether the native (Zig) guard is the live checksum backend
 */
public record ModReport(String modId,
                        String author,
                        Verdict verdict,
                        int classesChecked,
                        List<String> tamperedClasses,
                        int contentCount,
                        boolean watermarkPresent,
                        boolean nativeGuard) {

    /** The aggregate integrity verdict for a mod. */
    public enum Verdict {
        /** Every attributed class matched the integrity manifest — protected and unmodified. */
        SIGNED_INTACT,
        /** At least one class's bytes differ from the manifest — tampered. */
        TAMPERED,
        /** No attributed class was in any manifest — the mod shipped without the Shield. */
        UNSIGNED
    }

    public ModReport {
        tamperedClasses = List.copyOf(tamperedClasses);
    }

    public boolean intact() {
        return verdict == Verdict.SIGNED_INTACT;
    }
}
