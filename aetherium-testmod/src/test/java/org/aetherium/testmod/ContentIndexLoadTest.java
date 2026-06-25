/*
 * Aetherium Framework — testmod demonstration of the getResources content-index load.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.testmod;

import org.aetherium.datagen.ContentEntry;
import org.aetherium.datagen.ContentIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrates the resource-collision fix from a real mod's perspective: the testmod's own
 * {@code @AetheriumBlock} bakes a {@code content.index} into its output, and {@link ContentIndex#load}
 * reads it via {@code ClassLoader.getResources} (plural) without crashing — the path the loader uses to
 * merge content from every installed mod.
 */
final class ContentIndexLoadTest {

    @Test
    void loadsThisModsContentIndexViaGetResources() {
        List<ContentEntry> entries = assertDoesNotThrow(
                () -> ContentIndex.load(ContentIndexLoadTest.class.getClassLoader()));
        // The processor baked this mod's steel_block declaration into the index on the classpath.
        assertTrue(entries.stream().anyMatch(e -> e.modId().equals("aetherium") && e.name().equals("steel_block")),
                () -> "expected the testmod's declarative steel_block in the merged index, got: " + entries);
    }
}
