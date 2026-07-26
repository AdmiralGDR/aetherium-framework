/*
 * Aetherium Framework — machine block entity (dispatches AetheriumMachineLogic in-game). 
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.aetherium.content.AetheriumMachineLogic;
import org.aetherium.content.MachineContext;
import org.aetherium.content.MachineState;
import org.aetherium.edge.InteractionResult;
import org.aetherium.edge.PlayerHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * The real, ticking block entity behind a {@code @AetheriumBlock(behavior = …)} machine block — the piece that
 * was missing (), so declared behaviours had never once run in-game.
 *
 * <p>EN: Holds the bound {@link AetheriumMachineLogic}, its persistent {@link MachineState}, an age counter,
 * and the placer's id. It builds a loader-agnostic {@link MachineContext} and drives every callback:
 * {@code serverTick} → {@link AetheriumMachineLogic#tick}, {@code onUse}/{@code onPlaced}/{@code onRemoved}
 * from the owning block. {@link MachineState} is serialised into the block entity's NBT, so machine state
 * survives a restart. No Minecraft type ever reaches the mod's logic — the context is pure.
 * RU: Хранит привязанную {@link AetheriumMachineLogic}, её сохраняемое {@link MachineState}, счётчик возраста
 * и id установившего. Строит loader-нейтральный {@link MachineContext} и вызывает каждый колбэк; состояние
 * сериализуется в NBT блок-сущности и переживает рестарт. Ни один тип Minecraft не доходит до логики мода.
 */
public final class AetheriumMachineBlockEntity extends BlockEntity {

    private static final Logger LOG = LoggerFactory.getLogger("Aetherium/Machine");

    private final AetheriumMachineLogic logic;
    private final MachineState machineState = new MachineState();
    private long age;
    private UUID placerId;

    public AetheriumMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
            AetheriumMachineLogic logic) {
        super(type, pos, blockState);
        this.logic = logic;
    }

    /** Server ticker target (the loader wires this through the block's ticker). */
    void serverTick() {
        age++;
        run("tick", ctx -> {
            logic.tick(ctx);
            return null;
        });
        setChanged();
    }

    /** Route a right-click to the behaviour; returns whether it consumed the click. */
    InteractionResult onUse(net.minecraft.world.entity.player.Player player) {
        InteractionResult result = run("onUse", ctx -> logic.onUse(ctx, playerHandle(player)));
        setChanged();
        return result == null ? InteractionResult.PASS : result;
    }

    /** Record the placer and fire {@code onPlaced}. */
    void onPlaced(LivingEntity placer) {
        if (placer instanceof ServerPlayer sp) {
            this.placerId = sp.getUUID();
        }
        run("onPlaced", ctx -> {
            logic.onPlaced(ctx);
            return null;
        });
        setChanged();
    }

    /** Fire {@code onRemoved} just before the block leaves the world. */
    void onRemoved() {
        run("onRemoved", ctx -> {
            logic.onRemoved(ctx);
            return null;
        });
    }

    /** Build the pure context and run one callback; contains failures so one bad machine can't crash the tick. */
    private <T> T run(String phase, java.util.function.Function<MachineContext, T> body) {
        try {
            return body.apply(context());
        } catch (Throwable t) {
            LOG.warn("Aetherium machine '{}' {} failed at {}: {}",
                    logic.getClass().getName(), phase, worldPosition, t.toString());
            return null;
        }
    }

    private MachineContext context() {
        BlockPos pos = worldPosition;
        boolean client = level != null && level.isClientSide();
        Optional<PlayerHandle> placer = resolvePlacer();
        return new MachineContext() {
            @Override public long ticks() { return age; }
            @Override public boolean isClient() { return client; }
            @Override public int x() { return pos.getX(); }
            @Override public int y() { return pos.getY(); }
            @Override public int z() { return pos.getZ(); }
            @Override public MachineState state() { return machineState; }
            @Override public Optional<PlayerHandle> placer() { return placer; }
        };
    }

    private Optional<PlayerHandle> resolvePlacer() {
        if (placerId == null || level == null || level.isClientSide() || level.getServer() == null) {
            return Optional.empty();
        }
        ServerPlayer sp = level.getServer().getPlayerList().getPlayer(placerId);
        return sp == null ? Optional.empty() : Optional.of(new NeoForgePlayerHandle(sp));
    }

    private static PlayerHandle playerHandle(net.minecraft.world.entity.player.Player player) {
        return player instanceof ServerPlayer sp ? new NeoForgePlayerHandle(sp) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("aeth_age", age);
        if (placerId != null) {
            tag.putUUID("aeth_placer", placerId);
        }
        CompoundTag longs = new CompoundTag();
        machineState.longs().forEach(longs::putLong);
        tag.put("aeth_longs", longs);
        CompoundTag strings = new CompoundTag();
        machineState.strings().forEach(strings::putString);
        tag.put("aeth_strings", strings);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        age = tag.getLong("aeth_age");
        placerId = tag.hasUUID("aeth_placer") ? tag.getUUID("aeth_placer") : null;
        CompoundTag longs = tag.getCompound("aeth_longs");
        for (String k : longs.getAllKeys()) {
            machineState.setLong(k, longs.getLong(k));
        }
        CompoundTag strings = tag.getCompound("aeth_strings");
        for (String k : strings.getAllKeys()) {
            machineState.setString(k, strings.getString(k));
        }
    }

    /** Adapt a level's side to the loader-agnostic context flag (unused helper kept for clarity). */
    static boolean isClient(Level level) {
        return level != null && level.isClientSide();
    }
}
