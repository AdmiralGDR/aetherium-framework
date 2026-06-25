/*
 * Aetherium Framework — fluent builder for TreeNode hierarchies.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A concise builder for {@link TreeNode} trees.
 *
 * <pre>{@code
 * TreeNode faction = Tree.object()
 *     .put("name", "Iron Vanguard")
 *     .put("level", 7)
 *     .put("treasury", 10_500.50)
 *     .put("members", Tree.list(Tree.of("Steve"), Tree.of("Alex")))
 *     .put("skills", Tree.object().put("mining", 5).put("smithing", 3).build())
 *     .build();
 * }</pre>
 */
public final class Tree {

    private Tree() {
    }

    public static TreeNode of(String value) {
        return new TreeNode.Str(value);
    }

    public static TreeNode of(long value) {
        return new TreeNode.I64(value);
    }

    public static TreeNode of(double value) {
        return new TreeNode.F64(value);
    }

    public static TreeNode of(boolean value) {
        return new TreeNode.Bool(value);
    }

    public static TreeNode of(byte[] value) {
        return new TreeNode.Bytes(value);
    }

    public static TreeNode list(TreeNode... items) {
        return new TreeNode.Arr(List.of(items));
    }

    public static TreeNode list(List<TreeNode> items) {
        return new TreeNode.Arr(items);
    }

    public static ObjectBuilder object() {
        return new ObjectBuilder();
    }

    /** A fluent object builder that auto-wraps primitives. */
    public static final class ObjectBuilder {
        private final Map<String, TreeNode> entries = new LinkedHashMap<>();

        public ObjectBuilder put(String key, TreeNode value) {
            entries.put(key, value);
            return this;
        }

        public ObjectBuilder put(String key, String value) {
            return put(key, of(value));
        }

        public ObjectBuilder put(String key, long value) {
            return put(key, of(value));
        }

        public ObjectBuilder put(String key, double value) {
            return put(key, of(value));
        }

        public ObjectBuilder put(String key, boolean value) {
            return put(key, of(value));
        }

        public ObjectBuilder put(String key, byte[] value) {
            return put(key, of(value));
        }

        /** Build the immutable object node. */
        public TreeNode.Obj build() {
            return new TreeNode.Obj(entries);
        }

        /** Build a list of these object's values (rarely needed; for symmetry). */
        public List<TreeNode> values() {
            return new ArrayList<>(entries.values());
        }
    }
}
