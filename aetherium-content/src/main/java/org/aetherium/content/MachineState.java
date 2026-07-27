/*
 * Aetherium Framework — persistent state bag for a machine block entity (loader-agnostic).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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

    /** True if a long value was ever set for {@code key} — distinct from it being zero (). */
    public boolean hasLong(String key) {
        return longs.containsKey(key);
    }

    /** Remove {@code key}'s long value; afterwards {@link #hasLong} is false and {@link #getLong} returns its
     *  fallback. Lets a machine express "unclaimed" as absent, not a sentinel {@code 0}. */
    public void removeLong(String key) {
        longs.remove(key);
    }

    public String getString(String key, String fallback) {
        return strings.getOrDefault(key, fallback);
    }

    public void setString(String key, String value) {
        strings.put(key, value);
    }

    /** True if a string value was ever set for {@code key} — distinct from it being empty/"neutral". */
    public boolean hasString(String key) {
        return strings.containsKey(key);
    }

    /** Remove {@code key}'s string value; afterwards {@link #hasString} is false. */
    public void removeString(String key) {
        strings.remove(key);
    }

    /** Reset this machine to factory state — remove every long and string value (). */
    public void clear() {
        longs.clear();
        strings.clear();
    }

    /** A snapshot of the long keys, safe to iterate while calling {@link #removeLong} (). */
    public Set<String> longKeys() {
        return Set.copyOf(longs.keySet());
    }

    /** A snapshot of the string keys, safe to iterate while calling {@link #removeString} (). */
    public Set<String> stringKeys() {
        return Set.copyOf(strings.keySet());
    }

    /** An <strong>immutable copy</strong> of the long values — safe to keep and iterate; not a live view. */
    public Map<String, Long> longs() {
        return Map.copyOf(longs);
    }

    /** An <strong>immutable copy</strong> of the string values — safe to keep and iterate; not a live view. */
    public Map<String, String> strings() {
        return Map.copyOf(strings);
    }
}
