/*
 * LoomThreader demo — declarative content with the Aetherium plugin (zero config).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 */
package com.example.loomthreader;

import org.aetherium.content.AetheriumBlock;

/**
 * One annotation, no modId, no JSON — the Aetherium Gradle plugin already wired the content
 * annotation processor and injected the mod id, so the demo's namespace ({@code loomthreader_demo})
 * is applied automatically. Building {@code aetheriumBundle} generates every asset JSON and bundles
 * it next to this compiled class.
 */
@AetheriumBlock(name = "demo_steel_block", hardness = 4.0f, requiresTool = true)
public final class DemoSteelBlock {
}
