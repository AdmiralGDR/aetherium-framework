/*
 * Aetherium Framework — NeoForge inventory access (PAL implementation, shared by player handles).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.aetherium.edge.InventoryAccess;

/**
 * Loader-agnostic inventory over a real {@code Inventory}; items cross the boundary as namespaced string ids.
 *
 * <p>EN: Extracted to a top-level class () so both the server-side {@link NeoForgePlayerHandle} and
 * the client-side {@code ClientPlayerHandle} wrap the same adapter — a {@code LocalPlayer}'s inventory is the
 * same {@code Inventory} type as a {@code ServerPlayer}'s. The edge SPI stays pure: only ids (e.g.
 * {@code "minecraft:diamond"}) leave this class, never an {@code ItemStack}.
 * RU: Вынесен в отдельный класс (), чтобы и серверный {@link NeoForgePlayerHandle}, и клиентский
 * {@code ClientPlayerHandle} использовали один адаптер — инвентарь {@code LocalPlayer} того же типа
 * {@code Inventory}, что и у {@code ServerPlayer}. Наружу уходят только строковые id, не {@code ItemStack}.
 */
public final class NeoForgeInventoryAccess implements InventoryAccess {

    private final Inventory inv;

    public NeoForgeInventoryAccess(Inventory inv) {
        this.inv = inv;
    }

    @Override
    public int size() {
        return inv.getContainerSize();
    }

    @Override
    public String itemId(int slot) {
        ItemStack stack = inv.getItem(slot);
        if (stack.isEmpty()) {
            return AIR;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Override
    public int count(int slot) {
        return inv.getItem(slot).getCount();
    }

    @Override
    public void setItem(int slot, String itemId, int count) {
        if (itemId == null || AIR.equals(itemId) || count <= 0) {
            inv.setItem(slot, ItemStack.EMPTY);
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        inv.setItem(slot, new ItemStack(item, count));
    }
}
