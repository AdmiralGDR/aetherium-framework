/*
 * Aetherium Framework — PAL block-entity access.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A loader-agnostic handle to a block entity's persistent data.
 *
 * <p>EN: Block entities (chests, furnaces, mod machines) carry NBT. Rather than leak
 * {@code CompoundTag}/{@code BlockEntity} into mod code, the PAL exposes a small, typed key/value
 * surface: read for pulling state into off-heap compute, write for pushing results back during the
 * commit phase. The loader maps these onto the platform's NBT. Reads return {@code Optional*} so a
 * missing key never throws on the hot path.
 *
 * <p>RU: Блок-сущности (сундуки, печи, машины модов) несут NBT. Чтобы не протекали
 * {@code CompoundTag}/{@code BlockEntity}, PAL раскрывает небольшую типизированную поверхность
 * ключ/значение: чтение — для загрузки состояния в off-heap вычисления, запись — для возврата
 * результатов на фазе commit. Загрузчик отображает их на NBT платформы. Чтения возвращают
 * {@code Optional*}, поэтому отсутствующий ключ не бросает исключение на горячем пути.
 */
public interface BlockEntityAccess {

    /** Position of this block entity. */
    BlockPos pos();

    /** The block entity's registry type id, e.g. {@code "minecraft:chest"}. */
    String typeId();

    OptionalInt readInt(String key);

    OptionalLong readLong(String key);

    Optional<String> readString(String key);

    void writeInt(String key, int value);

    void writeLong(String key, long value);

    void writeString(String key, String value);
}
