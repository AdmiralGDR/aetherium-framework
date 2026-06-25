/*
 * Aetherium Framework — hierarchical (NBT/JSON-like) data tree.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A loader-agnostic hierarchical value — the NBT/JSON-like shape gameplay business logic syncs.
 *
 * <p>EN: The flat {@link StructArenaDeltaCodec} is perfect for thousands of uniform off-heap entities, but
 * gameplay state (faction rosters, skill trees, quest graphs) is <em>irregular and nested</em>. {@code
 * TreeNode} is a small tagged union — object/list/string/long/double/bool/bytes — that {@link TreeCodec}
 * serializes alongside the flat path, with no Minecraft NBT type involved. Build trees fluently with
 * {@link Tree}.
 * RU: Плоский {@link StructArenaDeltaCodec} идеален для тысяч однородных off-heap сущностей, но
 * геймплейное состояние (составы фракций, деревья навыков, графы квестов) — <em>нерегулярное и
 * вложенное</em>. {@code TreeNode} — небольшое размеченное объединение, сериализуемое {@link TreeCodec}
 * рядом с плоским путём, без типов NBT Minecraft. Деревья строятся через {@link Tree}.
 */
public sealed interface TreeNode
        permits TreeNode.Obj, TreeNode.Arr, TreeNode.Str, TreeNode.I64,
                TreeNode.F64, TreeNode.Bool, TreeNode.Bytes {

    /** An ordered map of named children (a compound/object). */
    record Obj(Map<String, TreeNode> entries) implements TreeNode {
        public Obj {
            entries = new LinkedHashMap<>(entries); // defensive, order-preserving copy
        }

        public TreeNode get(String key) {
            return entries.get(key);
        }

        public String getString(String key, String fallback) {
            return entries.get(key) instanceof Str s ? s.value() : fallback;
        }

        public long getLong(String key, long fallback) {
            return entries.get(key) instanceof I64 i ? i.value() : fallback;
        }

        public double getDouble(String key, double fallback) {
            return entries.get(key) instanceof F64 d ? d.value() : fallback;
        }

        public boolean getBool(String key, boolean fallback) {
            return entries.get(key) instanceof Bool b ? b.value() : fallback;
        }
    }

    /** An ordered list of children (a sequence). */
    record Arr(List<TreeNode> items) implements TreeNode {
        public Arr {
            items = List.copyOf(items);
        }
    }

    record Str(String value) implements TreeNode {
    }

    /** A 64-bit signed integer (covers byte/short/int/long). */
    record I64(long value) implements TreeNode {
    }

    /** A 64-bit float (covers float/double). */
    record F64(double value) implements TreeNode {
    }

    record Bool(boolean value) implements TreeNode {
    }

    /** A raw byte blob. */
    record Bytes(byte[] value) implements TreeNode {
        public Bytes {
            value = value.clone();
        }

        @Override
        public byte[] value() {
            return value.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Bytes other && Arrays.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "Bytes[" + value.length + "]";
        }
    }
}
