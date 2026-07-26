/*
 * Aetherium Framework — NeoForge content registrar (declarative-content bridge).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.aetherium.content.AetheriumMachineLogic;

import java.util.ArrayList;
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
    /** Items registered per mod id, in registration order — populated in the ITEM phase, consumed by the
     *  CREATIVE_MODE_TAB phase to auto-build one reachable tab per mod (). */
    private final Map<String, List<Item>> itemsByMod = new LinkedHashMap<>();
    /** Machine blocks created in the BLOCK phase, keyed by "modId:name" — the BLOCK_ENTITY_TYPE phase then
     *  registers a ticking {@link BlockEntityType} for each so behaviours actually run (). */
    private final Map<String, AetheriumMachineBlock> machineBlocks = new LinkedHashMap<>();

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
        } else if (Registries.BLOCK_ENTITY_TYPE.equals(event.getRegistryKey())) {
            registerBlockEntityTypes(event);
        } else if (Registries.ITEM.equals(event.getRegistryKey())) {
            registerItems(event);
        } else if (Registries.CREATIVE_MODE_TAB.equals(event.getRegistryKey())) {
            registerCreativeTabs(event);
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
                // a block that declares a machine-logic behaviour becomes a real ticking
                // AetheriumMachineBlock (EntityBlock) so its tick/onUse/onPlaced/onRemoved actually fire —
                // instead of the inert new Block(props) that shipped for five rounds.
                AetheriumMachineLogic logic = machineLogicFor(e.modId(), e.name());
                Block block;
                if (logic != null) {
                    AetheriumMachineBlock machine = new AetheriumMachineBlock(props, logic);
                    machineBlocks.put(e.modId() + ":" + e.name(), machine);
                    block = machine;
                } else {
                    block = new Block(props);
                }
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.modId(), e.name());
                event.register(Registries.BLOCK, id, () -> block);
                registeredBlocks.put(e.modId() + ":" + e.name(), block);
                LOG.info("Registered Aetherium block {} ({}hardness={}, resistance={}, requiresTool={}).",
                        id, logic != null ? "machine, " : "", e.hardness(), e.effectiveResistance(),
                        e.requiresTool());
            } catch (Throwable t) {
                LOG.error("Failed to register Aetherium block '{}:{}': {}", e.modId(), e.name(), t.toString());
            }
        }
    }

    /**
     * register one ticking {@link BlockEntityType} per machine block, bound to it, so the loader
     * calls {@code tick} every server tick and {@code onUse}/{@code onPlaced}/{@code onRemoved} on the block's
     * events. Fires after the BLOCK phase, so {@link #machineBlocks} is populated.
     */
    private void registerBlockEntityTypes(RegisterEvent event) {
        for (Map.Entry<String, AetheriumMachineBlock> entry : machineBlocks.entrySet()) {
            String key = entry.getKey();
            AetheriumMachineBlock block = entry.getValue();
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                final BlockEntityType<AetheriumMachineBlockEntity>[] holder = new BlockEntityType[1];
                BlockEntityType<AetheriumMachineBlockEntity> type = BlockEntityType.Builder
                        .of((pos, state) -> new AetheriumMachineBlockEntity(holder[0], pos, state, block.logic()),
                                block)
                        .build(null);
                holder[0] = type;
                block.bindBlockEntityType(() -> type);
                String[] parts = key.split(":", 2);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
                event.register(Registries.BLOCK_ENTITY_TYPE, id, () -> type);
                LOG.info("Registered Aetherium machine block-entity {} (behavior {}).",
                        id, block.logic().getClass().getName());
            } catch (Throwable t) {
                LOG.error("Failed to register Aetherium machine block-entity '{}': {}", key, t.toString());
            }
        }
    }

    /** Instantiate the {@link AetheriumMachineLogic} declared for a block, or {@code null} if none/failed. */
    private AetheriumMachineLogic machineLogicFor(String modId, String name) {
        for (BehaviorEntry b : behaviors) {
            if (b.machineLogic() && b.kind() == org.aetherium.datagen.ContentKind.BLOCK
                    && modId.equals(b.modId()) && name.equals(b.ownerName())) {
                try {
                    Class<?> cls = Class.forName(b.behaviorClass(), true, getClass().getClassLoader());
                    Object instance = cls.getDeclaredConstructor().newInstance();
                    return (AetheriumMachineLogic) instance;
                } catch (Throwable t) {
                    LOG.error("Aetherium machine behavior '{}' for {}:{} could not be instantiated "
                            + "(needs a public no-arg constructor): {}", b.behaviorClass(), modId, name, t.toString());
                    return null;
                }
            }
        }
        return null;
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
                        rememberForTab(e.modId(), item);
                    }
                    case ITEM -> {
                        Item.Properties props = new Item.Properties().stacksTo(e.maxStackSize());
                        Item item = new Item(props);
                        event.register(Registries.ITEM, id, () -> item);
                        rememberForTab(e.modId(), item);
                        LOG.info("Registered Aetherium item {} (maxStack={}).", id, e.maxStackSize());
                    }
                }
            } catch (Throwable t) {
                LOG.error("Failed to register Aetherium item '{}:{}': {}", e.modId(), e.name(), t.toString());
            }
        }
    }

    private void rememberForTab(String modId, Item item) {
        itemsByMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(item);
    }

    /**
     * auto-register one creative tab per mod id, holding that mod's items, so registered
     * content is actually reachable in survival instead of only via {@code /give}. The title comes from the
     * {@code itemGroup.<modId>} translation key (generated by the content processor); the first item is the
     * tab icon. Fires after the ITEM phase, so {@link #itemsByMod} is fully populated.
     */
    private void registerCreativeTabs(RegisterEvent event) {
        for (Map.Entry<String, List<Item>> byMod : itemsByMod.entrySet()) {
            String modId = byMod.getKey();
            List<Item> items = byMod.getValue();
            if (items.isEmpty()) {
                continue;
            }
            try {
                CreativeModeTab tab = CreativeModeTab.builder()
                        .title(Component.translatable("itemGroup." + modId))
                        .icon(() -> new ItemStack(items.get(0)))
                        .displayItems((params, output) -> items.forEach(output::accept))
                        .build();
                ResourceLocation tabId = ResourceLocation.fromNamespaceAndPath(modId, "aetherium_tab");
                event.register(Registries.CREATIVE_MODE_TAB, tabId, () -> tab);
                LOG.info("Registered Aetherium creative tab {} with {} item(s).", tabId, items.size());
            } catch (Throwable t) {
                LOG.error("Failed to register Aetherium creative tab for '{}': {}", modId, t.toString());
            }
        }
    }
}
