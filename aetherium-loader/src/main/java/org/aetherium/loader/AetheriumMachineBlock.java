/*
 * Aetherium Framework — machine block (routes vanilla block callbacks to AetheriumMachineLogic). 
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.aetherium.content.AetheriumMachineLogic;

import java.util.function.Supplier;

/**
 * The block half of a {@code @AetheriumBlock(behavior = …)} machine — turns vanilla block callbacks into the
 * loader-agnostic {@link AetheriumMachineLogic} callbacks that, before , never fired in-game.
 *
 * <p>EN: {@link EntityBlock} so it carries a ticking {@link AetheriumMachineBlockEntity}; the ticker calls
 * {@code tick}, {@code useWithoutItem} routes a right-click to {@code onUse}, {@code setPlacedBy} to
 * {@code onPlaced} (with the placer), and {@code onRemove} to {@code onRemoved}. {@code onUse} runs on both
 * sides so a behaviour can open its screen on the client and mutate state on the server, keyed by
 * {@link org.aetherium.content.MachineContext#isClient()} — the idiomatic split.
 * RU: {@link EntityBlock}, поэтому несёт тикающую {@link AetheriumMachineBlockEntity}; тикер зовёт {@code tick},
 * {@code useWithoutItem} направляет правый клик в {@code onUse}, {@code setPlacedBy} — в {@code onPlaced} (с
 * установившим), {@code onRemove} — в {@code onRemoved}. {@code onUse} выполняется с обеих сторон.
 */
public final class AetheriumMachineBlock extends Block implements EntityBlock {

    private final AetheriumMachineLogic logic;
    private Supplier<BlockEntityType<AetheriumMachineBlockEntity>> beType = () -> null;

    public AetheriumMachineBlock(Properties properties, AetheriumMachineLogic logic) {
        super(properties);
        this.logic = logic;
    }

    /** Bind the block-entity type once it is registered (the BLOCK_ENTITY_TYPE phase runs after BLOCK). */
    void bindBlockEntityType(Supplier<BlockEntityType<AetheriumMachineBlockEntity>> type) {
        this.beType = type;
    }

    /** The bound behaviour — used by the registrar to build the block-entity type's supplier. */
    AetheriumMachineLogic logic() {
        return logic;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        BlockEntityType<AetheriumMachineBlockEntity> type = beType.get();
        return type == null ? null : new AetheriumMachineBlockEntity(type, pos, state, logic);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null; // machine simulation is server-authoritative
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof AetheriumMachineBlockEntity machine) {
                machine.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AetheriumMachineBlockEntity be) {
            org.aetherium.edge.InteractionResult r = be.onUse(player);
            return r == org.aetherium.edge.InteractionResult.CANCEL
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AetheriumMachineBlockEntity be) {
            be.onPlaced(placer);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof AetheriumMachineBlockEntity be) {
            be.onRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
