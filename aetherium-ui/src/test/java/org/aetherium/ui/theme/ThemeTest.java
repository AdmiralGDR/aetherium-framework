/*
 * Aetherium Framework — Theme design-token tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui.theme;

import org.aetherium.ui.UiColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ThemeTest {

    @Test
    void lightAndDarkAreDistinctPalettes() {
        assertNotEquals(Theme.light().background(), Theme.dark().background());
        assertNotEquals(Theme.light().text(), Theme.dark().text());
    }

    @Test
    void spacingScaleMultipliesTheUnitAndClampsAtZero() {
        Theme t = Theme.dark();
        assertEquals(t.spacingUnit(), t.space(1));
        assertEquals(t.spacingUnit() * 3, t.space(3));
        assertEquals(0, t.space(0));
        assertEquals(0, t.space(-5), "a negative step floors at 0");
    }

    @Test
    void withersChangeOnlyTheTargetedToken() {
        Theme base = Theme.light();
        UiColor brand = UiColor.rgb(0x00C853);
        Theme rebranded = base.withPrimary(brand);
        assertSame(brand, rebranded.primary());
        assertEquals(base.text(), rebranded.text(), "withPrimary leaves other tokens untouched");
        assertEquals(base.spacingUnit(), rebranded.spacingUnit());

        Theme roomier = base.withSpacingUnit(8);
        assertEquals(8, roomier.spacingUnit());
        assertEquals(base.primary(), roomier.primary());
    }

    @Test
    void constructorRejectsInvalidScale() {
        Theme t = Theme.light();
        assertThrows(IllegalArgumentException.class, () -> t.withSpacingUnit(0));
        assertThrows(IllegalArgumentException.class,
                () -> new Theme(t.background(), t.surface(), t.surfaceMuted(), t.border(), t.primary(),
                        t.onPrimary(), t.text(), t.textMuted(), t.danger(), t.success(), 4, -1));
    }
}
