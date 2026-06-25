/*
 * Aetherium Framework — multi-jar index merge tests (the getResources collision fix).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.datagen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that {@link ContentIndex#load} / {@link BehaviorIndex#load} read and <strong>merge</strong> the
 * index from <em>every</em> classpath root (simulating two installed Aetherium mods), rather than letting
 * one silently shadow the other — the "first index wins" collision fix, demonstrated offline.
 */
final class IndexMergeTest {

    @Test
    void contentIndexMergesAcrossTwoJars(@TempDir Path root) throws IOException {
        // Two separate classpath roots ("mod A" and "mod B"), each with its own content.index.
        Path modA = writeIndex(root.resolve("modA"), ContentIndex.RESOURCE,
                ContentIndex.serialize(new ContentEntry(ContentKind.BLOCK, "moda", "steel_block", "", 5f, 6f, true, true, 64, "")));
        Path modB = writeIndex(root.resolve("modB"), ContentIndex.RESOURCE,
                ContentIndex.serialize(new ContentEntry(ContentKind.ITEM, "modb", "ruby", "", 0f, 0f, false, false, 16, "")));

        try (URLClassLoader cl = new URLClassLoader(new URL[]{modA.toUri().toURL(), modB.toUri().toURL()}, null)) {
            // getResources must see BOTH roots.
            assertEquals(2, java.util.Collections.list(cl.getResources(ContentIndex.RESOURCE)).size());

            List<ContentEntry> merged = ContentIndex.load(cl);
            assertEquals(2, merged.size(), "both mods' content must be merged, not overwritten");
            assertTrue(merged.stream().anyMatch(e -> e.modId().equals("moda") && e.name().equals("steel_block")));
            assertTrue(merged.stream().anyMatch(e -> e.modId().equals("modb") && e.name().equals("ruby")));
        }
    }

    @Test
    void behaviorIndexMergesAcrossTwoJars(@TempDir Path root) throws IOException {
        Path modA = writeIndex(root.resolve("a"), BehaviorIndex.RESOURCE,
                BehaviorIndex.serialize(new BehaviorEntry(ContentKind.BLOCK, "moda", "furnace", "a.FurnaceLogic", true)));
        Path modB = writeIndex(root.resolve("b"), BehaviorIndex.RESOURCE,
                BehaviorIndex.serialize(new BehaviorEntry(ContentKind.BLOCK, "modb", "crusher", "b.CrusherLogic", true)));

        try (URLClassLoader cl = new URLClassLoader(new URL[]{modA.toUri().toURL(), modB.toUri().toURL()}, null)) {
            List<BehaviorEntry> merged = BehaviorIndex.load(cl);
            assertEquals(2, merged.size(), "both mods' behaviors must be merged");
            assertEquals(2, merged.stream().filter(BehaviorEntry::machineLogic).count());
        }
    }

    @Test
    void loadingWithNoIndexDoesNotCrash(@TempDir Path root) throws IOException {
        try (URLClassLoader cl = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
            assertTrue(ContentIndex.load(cl).isEmpty());
            assertTrue(BehaviorIndex.load(cl).isEmpty());
        }
    }

    /** Write a one-line index file under {@code dir} and return {@code dir} (a classpath root). */
    private static Path writeIndex(Path dir, String resource, String line) throws IOException {
        Path file = dir.resolve(resource);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "# generated\n" + line + "\n");
        return dir;
    }
}
