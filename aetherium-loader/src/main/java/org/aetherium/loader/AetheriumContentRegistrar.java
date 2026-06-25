/*
 * Aetherium Framework — NeoForge content registrar (declarative-content bridge).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.aetherium.datagen.BehaviorEntry;
import org.aetherium.datagen.BehaviorIndex;
import org.aetherium.datagen.ContentEntry;
import org.aetherium.datagen.ContentIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns declarative Aetherium content into real vanilla registry entries — eliminating the
 * {@code DeferredRegister}/{@code BlockItem} boilerplate entirely.
 *
 * <p>EN: On NeoForge's {@code RegisterEvent} (which fires once per registry) this reads the
 * {@link ContentIndex} that the {@code aetherium-content} annotation processor baked onto the
 * classpath at build time, then: during the <strong>BLOCK</strong> phase it builds each {@link Block}
 * from its {@link ContentEntry} ({@code strength(hardness, resistance)} + optional
 * {@code requiresCorrectToolForDrops}) and registers it; during the <strong>ITEM</strong> phase it
 * auto-wraps every block in a {@link BlockItem} and registers standalone {@link Item}s. This is the
 * <em>only</em> place that knows both the Aetherium content model and Minecraft's registries — the
 * mod author writes a single annotation and never touches either. Failures are contained per entry so
 * one bad declaration can't abort registration.
 *
 * <p>RU: На {@code RegisterEvent} NeoForge (срабатывает один раз на реестр) читает {@link ContentIndex},
 * который процессор {@code aetherium-content} «запёк» в classpath на этапе сборки, затем: в фазе
 * <strong>BLOCK</strong> строит каждый {@link Block} из его {@link ContentEntry}
 * ({@code strength(hardness, resistance)} + опц. {@code requiresCorrectToolForDrops}) и регистрирует
 * его; в фазе <strong>ITEM</strong> автоматически оборачивает каждый блок в {@link BlockItem} и
 * регистрирует отдельные {@link Item}. Это <em>единственное</em> место, знающее и модель контента
 * Aetherium, и реестры Minecraft. Ошибки изолируются по записи.
 */
public final class AetheriumContentRegistrar {

    private static final Logger LOG = LoggerFactory.getLogger("Aetherium/Content");

    private final List<ContentEntry> entries;
    private final List<BehaviorEntry> behaviors;
    /** Blocks created in the BLOCK phase, keyed by "modId:name", reused to build BlockItems. */
    private final Map<String, Block> registeredBlocks = new LinkedHashMap<>();

    public AetheriumContentRegistrar() {
        ClassLoader cl = AetheriumContentRegistrar.class.getClassLoader();
        // Both indices are read via ClassLoader.getResources (plural): EVERY installed Aetherium mod
        // contributes its own content.index / behaviors.index, and they are merged here — no mod can
        // silently overwrite another's declarations (the QA "first index wins" hazard).
        this.entries = ContentIndex.load(cl);
        this.behaviors = BehaviorIndex.load(cl);
        if (!entries.isEmpty() || !behaviors.isEmpty()) {
            long machines = behaviors.stream().filter(BehaviorEntry::machineLogic).count();
            LOG.info("Aetherium content: merged {} declarative entr(ies) and {} behavior(s) "
                    + "({} machine-logic) from all mods on the classpath.",
                    entries.size(), behaviors.size(), machines);
        }
    }

    /** Whether any declarative content exists (lets the entrypoint skip wiring when there's none). */
    public boolean hasContent() {
        return !entries.isEmpty() || !behaviors.isEmpty();
    }

    /** Merged behavior bindings from every Aetherium mod (machine-logic blocks, item behaviors). */
    public List<BehaviorEntry> behaviors() {
        return behaviors;
    }

    /** Mod-bus {@code RegisterEvent} handler — registers blocks, then their items + standalone items. */
    public void onRegister(RegisterEvent event) {
        if (entries.isEmpty()) {
            return;
        }
        if (Registries.BLOCK.equals(event.getRegistryKey())) {
            registerBlocks(event);
        } else if (Registries.ITEM.equals(event.getRegistryKey())) {
            registerItems(event);
        }
    }

    private void registerBlocks(RegisterEvent event) {
        for (ContentEntry e : entries) {
            if (e.kind() != org.aetherium.datagen.ContentKind.BLOCK) {
                continue;
            }
            try {
                BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                        .strength(e.hardness(), e.effectiveResistance());
                if (e.requiresTool()) {
                    props = props.requiresCorrectToolForDrops();
                }
                Block block = new Block(props);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.modId(), e.name());
                event.register(Registries.BLOCK, id, () -> block);
                registeredBlocks.put(e.modId() + ":" + e.name(), block);
                LOG.info("Registered Aetherium block {} (hardness={}, resistance={}, requiresTool={}).",
                        id, e.hardness(), e.effectiveResistance(), e.requiresTool());
            } catch (Throwable t) {
                LOG.error("Failed to register Aetherium block '{}:{}': {}", e.modId(), e.name(), t.toString());
            }
        }
    }

    private void registerItems(RegisterEvent event) {
        for (ContentEntry e : entries) {
            try {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.modId(), e.name());
                switch (e.kind()) {
                    case BLOCK -> {
                        // Auto-wrap the block in a BlockItem — the boilerplate the modder never writes.
                        Block block = registeredBlocks.get(e.modId() + ":" + e.name());
                        if (block == null) {
                            LOG.warn("No block for '{}:{}' when creating its BlockItem; skipping.",
                                    e.modId(), e.name());
                            continue;
                        }
                        BlockItem item = new BlockItem(block, new Item.Properties());
                        event.register(Registries.ITEM, id, () -> item);
                    }
                    case ITEM -> {
                        Item.Properties props = new Item.Properties().stacksTo(e.maxStackSize());
                        Item item = new Item(props);
                        event.register(Registries.ITEM, id, () -> item);
                        LOG.info("Registered Aetherium item {} (maxStack={}).", id, e.maxStackSize());
                    }
                }
            } catch (Throwable t) {
                LOG.error("Failed to register Aetherium item '{}:{}': {}", e.modId(), e.name(), t.toString());
            }
        }
    }
}
