/*
 * Aetherium Framework — minimal, dependency-free JSON reader/writer for the LSP backend.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.lsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, self-contained JSON parser + writer — enough to speak JSON-RPC without a third-party library.
 *
 * <p>EN: The framework is offline-first and pulls in no JSON dependency, so the LSP backend ships its own
 * compact reader/writer. {@link #parse(String)} returns a tree of {@code Map<String,Object>} / {@code List}
 * / {@code String} / {@code Double} / {@code Boolean} / {@code null}; {@link #write(Object)} serializes the
 * same shapes back with correct string escaping. It is deliberately strict-enough for LSP traffic, not a
 * general validator.
 * RU: Крошечный самодостаточный парсер + писатель JSON — достаточно, чтобы говорить на JSON-RPC без
 * сторонней библиотеки. {@link #parse(String)} возвращает дерево из {@code Map}/{@code List}/{@code String}/
 * {@code Double}/{@code Boolean}/{@code null}; {@link #write(Object)} сериализует те же формы обратно с
 * корректным экранированием строк.
 */
public final class Json {

    private Json() {
    }

    // ---- writing ----------------------------------------------------------------------------------

    /** Serialize a tree of Map/List/String/Number/Boolean/null to compact JSON. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(128);
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        switch (v) {
            case null -> sb.append("null");
            case String s -> writeString(sb, s);
            case Boolean b -> sb.append(b.toString());
            case Integer i -> sb.append(i.toString());
            case Long l -> sb.append(l.toString());
            case Double d -> sb.append(d % 1 == 0 ? Long.toString((long) (double) d) : d.toString());
            case Map<?, ?> m -> writeObject(sb, m);
            case Iterable<?> it -> writeArray(sb, it);
            default -> writeString(sb, v.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> m) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            writeValue(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> it) {
        sb.append('[');
        boolean first = true;
        for (Object o : it) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(sb, o);
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
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

    // ---- parsing ----------------------------------------------------------------------------------

    /** Parse a JSON document into a Map/List/String/Double/Boolean/null tree. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (!p.atEnd()) {
            throw new IllegalArgumentException("trailing JSON at offset " + p.pos);
        }
        return v;
    }

    /** Convenience: parse and cast to an object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        return (Map<String, Object>) v;
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
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object value() {
            skipWs();
            if (atEnd()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't', 'f' -> bool();
                case 'n' -> nul();
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                expect(':');
                m.put(key, value());
                skipWs();
                char c = next();
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("expected ',' or '}' at " + pos);
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWs();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("expected ',' or ']' at " + pos);
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object bool() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("bad literal at " + pos);
        }

        private Object nul() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("bad literal at " + pos);
        }

        private Double number() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("unexpected character '" + s.charAt(pos) + "' at " + pos);
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        private char peek() {
            return s.charAt(pos);
        }

        private char next() {
            return s.charAt(pos++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalArgumentException("expected '" + c + "' at " + (pos - 1));
            }
        }
    }
}
