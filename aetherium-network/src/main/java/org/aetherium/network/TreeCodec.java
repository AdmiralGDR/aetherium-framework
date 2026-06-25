/*
 * Aetherium Framework — hierarchical (NBT/JSON-like) tree codec.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes/deserializes a {@link TreeNode} over the loader-agnostic {@link PayloadSink}/{@link PayloadSource}.
 *
 * <p>EN: A compact, self-describing tag stream ({@code [tag][payload]}). Object/list children recurse;
 * scalars write their bits; strings/blobs use the length-prefixed byte primitive. This rides the <em>same</em>
 * buffer abstraction as the flat {@link StructArenaDeltaCodec}, so a mod can sync uniform off-heap entities
 * and irregular nested business logic (factions, skill trees) over one transport. Decoding is hardened: a
 * depth limit defeats stack-overflow trees and per-element size limits defeat hostile length fields, so a
 * corrupt or malicious packet throws cleanly instead of crashing or over-allocating.
 * RU: Компактный самоописывающий поток тегов ({@code [тег][данные]}). Дети объекта/списка рекурсивны;
 * скаляры пишут свои биты; строки/блобы — через длина-префиксованный примитив. Использует <em>ту же</em>
 * абстракцию буфера, что и плоский {@link StructArenaDeltaCodec}. Декодирование укреплено: лимит глубины
 * против переполнения стека и лимиты размера против враждебных длин — битый пакет бросает чисто.
 */
public final class TreeCodec {

    private static final int TAG_OBJ = 1;
    private static final int TAG_ARR = 2;
    private static final int TAG_STR = 3;
    private static final int TAG_I64 = 4;
    private static final int TAG_F64 = 5;
    private static final int TAG_BOOL = 6;
    private static final int TAG_BYTES = 7;

    /** Max nesting depth accepted on decode (defeats stack-overflow trees). */
    public static final int MAX_DEPTH = 512;
    /** Max children in one object/list, and max bytes in one string/blob (defeats hostile lengths). */
    public static final int MAX_ELEMENTS = 1 << 20;
    public static final int MAX_BYTES = 1 << 24; // 16 MiB

    private TreeCodec() {
    }

    /** Write {@code node} to {@code sink}. */
    public static void encode(TreeNode node, PayloadSink sink) {
        encode(node, sink, 0);
    }

    private static void encode(TreeNode node, PayloadSink sink, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("tree exceeds max depth " + MAX_DEPTH);
        }
        switch (node) {
            case TreeNode.Obj o -> {
                sink.writeInt(TAG_OBJ);
                sink.writeInt(o.entries().size());
                for (Map.Entry<String, TreeNode> e : o.entries().entrySet()) {
                    sink.writeBytes(e.getKey().getBytes(StandardCharsets.UTF_8));
                    encode(e.getValue(), sink, depth + 1);
                }
            }
            case TreeNode.Arr a -> {
                sink.writeInt(TAG_ARR);
                sink.writeInt(a.items().size());
                for (TreeNode item : a.items()) {
                    encode(item, sink, depth + 1);
                }
            }
            case TreeNode.Str s -> {
                sink.writeInt(TAG_STR);
                sink.writeBytes(s.value().getBytes(StandardCharsets.UTF_8));
            }
            case TreeNode.I64 i -> {
                sink.writeInt(TAG_I64);
                sink.writeLong(i.value());
            }
            case TreeNode.F64 d -> {
                sink.writeInt(TAG_F64);
                sink.writeLong(Double.doubleToLongBits(d.value()));
            }
            case TreeNode.Bool b -> {
                sink.writeInt(TAG_BOOL);
                sink.writeInt(b.value() ? 1 : 0);
            }
            case TreeNode.Bytes by -> {
                sink.writeInt(TAG_BYTES);
                sink.writeBytes(by.value());
            }
        }
    }

    /** Read a {@link TreeNode} from {@code source}. */
    public static TreeNode decode(PayloadSource source) {
        return decode(source, 0);
    }

    private static TreeNode decode(PayloadSource source, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("tree exceeds max depth " + MAX_DEPTH);
        }
        int tag = source.readInt();
        return switch (tag) {
            case TAG_OBJ -> {
                int n = count(source.readInt());
                Map<String, TreeNode> entries = new LinkedHashMap<>(Math.max(4, n * 2));
                for (int i = 0; i < n; i++) {
                    String key = new String(source.readBytes(MAX_BYTES), StandardCharsets.UTF_8);
                    entries.put(key, decode(source, depth + 1));
                }
                yield new TreeNode.Obj(entries);
            }
            case TAG_ARR -> {
                int n = count(source.readInt());
                List<TreeNode> items = new ArrayList<>(Math.min(n, 1024));
                for (int i = 0; i < n; i++) {
                    items.add(decode(source, depth + 1));
                }
                yield new TreeNode.Arr(items);
            }
            case TAG_STR -> new TreeNode.Str(new String(source.readBytes(MAX_BYTES), StandardCharsets.UTF_8));
            case TAG_I64 -> new TreeNode.I64(source.readLong());
            case TAG_F64 -> new TreeNode.F64(Double.longBitsToDouble(source.readLong()));
            case TAG_BOOL -> new TreeNode.Bool(source.readInt() != 0);
            case TAG_BYTES -> new TreeNode.Bytes(source.readBytes(MAX_BYTES));
            default -> throw new IllegalArgumentException("unknown TreeNode tag " + tag);
        };
    }

    private static int count(int n) {
        if (n < 0 || n > MAX_ELEMENTS) {
            throw new IllegalArgumentException("element count " + n + " out of range [0, " + MAX_ELEMENTS + "]");
        }
        return n;
    }
}
