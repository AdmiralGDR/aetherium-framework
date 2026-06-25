/*
 * Aetherium Framework — persistent state bag for a machine block entity (loader-agnostic).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tiny persistent key/value store for an {@link AetheriumMachineLogic} block entity — no NBT type.
 *
 * <p>EN: A machine's saved fields (smelt progress, energy, mode) live here as {@code long}/{@code String}
 * values; the loader serializes this to/from the block entity's NBT. Keeping it a plain map means a
 * machine's logic is pure and unit-testable with no game present.
 * RU: Сохраняемые поля машины (прогресс плавки, энергия, режим) хранятся здесь как {@code long}/{@code String};
 * загрузчик сериализует это в/из NBT блок-сущности. Простая карта делает логику машины чистой и тестируемой.
 */
public final class MachineState {

    private final Map<String, Long> longs = new LinkedHashMap<>();
    private final Map<String, String> strings = new LinkedHashMap<>();

    public long getLong(String key, long fallback) {
        return longs.getOrDefault(key, fallback);
    }

    public void setLong(String key, long value) {
        longs.put(key, value);
    }

    public long increment(String key, long delta) {
        long v = getLong(key, 0L) + delta;
        longs.put(key, v);
        return v;
    }

    public String getString(String key, String fallback) {
        return strings.getOrDefault(key, fallback);
    }

    public void setString(String key, String value) {
        strings.put(key, value);
    }

    public Map<String, Long> longs() {
        return Map.copyOf(longs);
    }

    public Map<String, String> strings() {
        return Map.copyOf(strings);
    }
}
