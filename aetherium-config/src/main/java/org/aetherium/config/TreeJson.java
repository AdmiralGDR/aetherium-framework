/*
 * Aetherium Framework — hardened JSON <-> TreeNode reader/writer.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.config;

import org.aetherium.core.AetheriumException;
import org.aetherium.core.Diagnostic;
import org.aetherium.network.Tree;
import org.aetherium.network.TreeNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, dependency-free, <strong>hardened</strong> JSON reader/writer over {@link TreeNode} — so a config
 * file is human-editable text yet round-trips through the same tree the framework already trusts.
 *
 * <p>EN: The reader is a bounded recursive-descent parser (max nesting depth, no unbounded recursion, no
 * trailing garbage) — treating the file as hostile input per the negative-trust axiom. Numbers without a
 * fraction/exponent become {@code I64}, otherwise {@code F64}; {@code null} maps to an empty string.
 * {@code Bytes} nodes are written as base64 strings (config is not meant for raw blobs). The writer emits
 * stable, pretty-printed, sorted-key JSON so diffs stay clean.
 * RU: Читатель — ограниченный парсер рекурсивного спуска (макс. глубина, без переполнения стека, без мусора
 * в хвосте) — файл считается враждебным вводом. Числа без дробной/экспоненты → {@code I64}, иначе
 * {@code F64}. Писатель выдаёт стабильный отформатированный JSON с сортировкой ключей.
 */
public final class TreeJson {

    /** Maximum nesting depth accepted by the parser (mirrors {@code TreeCodec.MAX_DEPTH}). */
    public static final int MAX_DEPTH = 512;

    private TreeJson() {
    }

    // --- writer ---------------------------------------------------------------------------------

    /** Serialize a {@link TreeNode} to pretty-printed JSON with sorted object keys. */
    public static String write(TreeNode node) {
        StringBuilder sb = new StringBuilder(256);
        writeNode(node, sb, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void writeNode(TreeNode node, StringBuilder sb, int indent) {
        switch (node) {
            case TreeNode.Obj o -> writeObject(o, sb, indent);
            case TreeNode.Arr a -> writeArray(a, sb, indent);
            case TreeNode.Str s -> writeString(s.value(), sb);
            case TreeNode.I64 i -> sb.append(i.value());
            case TreeNode.F64 d -> sb.append(d.value());
            case TreeNode.Bool b -> sb.append(b.value());
            case TreeNode.Bytes by -> writeString(java.util.Base64.getEncoder().encodeToString(by.value()), sb);
        }
    }

    private static void writeObject(TreeNode.Obj o, StringBuilder sb, int indent) {
        if (o.entries().isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        List<String> keys = new ArrayList<>(o.entries().keySet());
        keys.sort(String::compareTo);
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            indent(sb, indent + 1);
            writeString(key, sb);
            sb.append(": ");
            writeNode(o.entries().get(key), sb, indent + 1);
            sb.append(i < keys.size() - 1 ? ",\n" : "\n");
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(TreeNode.Arr a, StringBuilder sb, int indent) {
        if (a.items().isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < a.items().size(); i++) {
            indent(sb, indent + 1);
            writeNode(a.items().get(i), sb, indent + 1);
            sb.append(i < a.items().size() - 1 ? ",\n" : "\n");
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append("  ".repeat(depth));
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // --- reader (bounded recursive descent) -----------------------------------------------------

    /** Parse JSON text into a {@link TreeNode}. Throws {@link AetheriumException} on malformed/hostile input. */
    public static TreeNode parse(String json) {
        if (json == null) {
            throw error("input is null");
        }
        Parser p = new Parser(json);
        p.skipWs();
        TreeNode node = p.value(0);
        p.skipWs();
        if (!p.atEnd()) {
            throw error("trailing content at index " + p.pos);
        }
        return node;
    }

    private static AetheriumException error(String message) {
        return new AetheriumException(Diagnostic.error("AE-CONFIG-JSON", "Invalid config JSON: " + message));
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        TreeNode value(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nesting exceeds max depth " + MAX_DEPTH);
            }
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> object(depth);
                case '[' -> array(depth);
                case '"' -> new TreeNode.Str(string());
                case 't', 'f' -> bool();
                case 'n' -> nullValue();
                default -> number();
            };
        }

        TreeNode object(int depth) {
            expect('{');
            Map<String, TreeNode> map = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                pos++;
                return new TreeNode.Obj(map);
            }
            while (true) {
                skipWs();
                if (peek() != '"') {
                    throw error("expected string key at index " + pos);
                }
                String key = string();
                skipWs();
                expect(':');
                skipWs();
                map.put(key, value(depth + 1));
                skipWs();
                char c = next();
                if (c == '}') {
                    return new TreeNode.Obj(map);
                }
                if (c != ',') {
                    throw error("expected ',' or '}' at index " + (pos - 1));
                }
            }
        }

        TreeNode array(int depth) {
            expect('[');
            List<TreeNode> items = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                pos++;
                return new TreeNode.Arr(items);
            }
            while (true) {
                skipWs();
                items.add(value(depth + 1));
                skipWs();
                char c = next();
                if (c == ']') {
                    return new TreeNode.Arr(items);
                }
                if (c != ',') {
                    throw error("expected ',' or ']' at index " + (pos - 1));
                }
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > s.length()) {
                                throw error("truncated \\u escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw error("bad escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        TreeNode number() {
            int start = pos;
            boolean floating = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    floating = true;
                    pos++;
                } else {
                    break;
                }
            }
            String tok = s.substring(start, pos);
            if (tok.isEmpty()) {
                throw error("expected value at index " + start);
            }
            try {
                return floating ? new TreeNode.F64(Double.parseDouble(tok)) : new TreeNode.I64(Long.parseLong(tok));
            } catch (NumberFormatException e) {
                throw error("bad number '" + tok + "'");
            }
        }

        TreeNode bool() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return new TreeNode.Bool(true);
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return new TreeNode.Bool(false);
            }
            throw error("expected boolean at index " + pos);
        }

        TreeNode nullValue() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return Tree.of(""); // null maps to an empty string (TreeNode has no null variant)
            }
            throw error("expected null at index " + pos);
        }

        char peek() {
            return atEnd() ? '\0' : s.charAt(pos);
        }

        char next() {
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            return s.charAt(pos++);
        }

        void expect(char c) {
            if (next() != c) {
                throw error("expected '" + c + "' at index " + (pos - 1));
            }
        }
    }
}
