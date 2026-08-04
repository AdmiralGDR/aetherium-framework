/*
 * Aetherium Framework — PAL inventory access (loader-agnostic, item ids as strings).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

/**
 * A loader-agnostic view of a player/container inventory — items identified by namespaced string id.
 *
 * <p>EN: No {@code ItemStack} type crosses this boundary; an item is its registry id (e.g.
 * {@code "minecraft:diamond"}) plus a count. The loader implements this over the real container. Empty
 * slots read as {@link #AIR} with count 0.
 * RU: Тип {@code ItemStack} не пересекает границу; предмет — это его registry-id (напр.
 * {@code "minecraft:diamond"}) и количество. Загрузчик реализует это поверх реального контейнера.
 */
public interface InventoryAccess {

    /** The canonical empty-slot id. */
    String AIR = "minecraft:air";

    /** Number of slots. */
    int size();

    /** The item id in {@code slot}, or {@link #AIR} if empty. */
    String itemId(int slot);

    /** The stack count in {@code slot} (0 if empty). */
    int count(int slot);

    /** Set the contents of {@code slot}. Pass {@link #AIR}/0 to clear. */
    void setItem(int slot, String itemId, int count);

    /** True if {@code slot} holds nothing. */
    default boolean isEmpty(int slot) {
        return count(slot) <= 0 || AIR.equals(itemId(slot));
    }

    /**
     * The slot the player currently has selected (held), or {@code -1} where the concept does not apply
     * (off-platform, a container without a selection, a fake/test inventory). keyed-by-exact-id
     * admin surfaces ("restrict the item I'm holding") need the held slot to turn a multi-step hotbar picker
     * into one always-correctly-spelled click. The loader overrides this with the real selected hotbar index.
     */
    default int selectedSlot() {
        return -1;
    }

    /**
     * The id of the item in the {@link #selectedSlot() selected slot}, or {@link #AIR} when nothing is
     * selected/held. Convenience over {@code itemId(selectedSlot())} that stays safe when no slot applies.
     */
    default String heldItemId() {
        int slot = selectedSlot();
        return slot < 0 || slot >= size() ? AIR : itemId(slot);
    }

    /** Index of the first empty slot, or {@code -1} if the inventory is full. */
    default int firstEmptySlot() {
        for (int i = 0; i < size(); i++) {
            if (isEmpty(i)) {
                return i;
            }
        }
        return -1;
    }

    /** Place {@code count} of {@code itemId} into the first empty slot; returns false if full. */
    default boolean give(String itemId, int count) {
        int slot = firstEmptySlot();
        if (slot < 0) {
            return false;
        }
        setItem(slot, itemId, count);
        return true;
    }

    /**
     * A zero-slot, read-only inventory — the no-op a fake {@link PlayerHandle} can return from
     * {@code inventory()} in tests. mirrors {@link PlayerAccess#EMPTY} /
     * {@link EdgeCommands#NONE} / {@code WorldStore.inMemory()} so no consumer has to return {@code null}.
     */
    InventoryAccess EMPTY = new InventoryAccess() {
        @Override
        public int size() {
            return 0;
        }

        @Override
        public String itemId(int slot) {
            return AIR;
        }

        @Override
        public int count(int slot) {
            return 0;
        }

        @Override
        public void setItem(int slot, String itemId, int count) {
            // read-only
        }
    };
}
