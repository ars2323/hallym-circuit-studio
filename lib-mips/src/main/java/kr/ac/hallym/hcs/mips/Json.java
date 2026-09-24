/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * hcs-asm 출력만 읽으면 되는 작은 JSON 파서. lib-mips는 외부 의존성 없는 단일 jar여야 해서 직접 짰다
 * (CLAUDE.md 5절). 값은 Map(순서 유지), List, String, Long 또는 Double, Boolean, null이다.
 */
final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        p.space();
        Object v = p.value();
        p.space();
        if (p.i != p.s.length()) {
            throw p.error("trailing characters");
        }
        return v;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("JSON: " + what + " at " + i);
    }

    private void space() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i += 1;
        }
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) {
            throw error("expected '" + c + "'");
        }
        i += 1;
    }

    private Object value() {
        if (i >= s.length()) {
            throw error("unexpected end");
        }
        char c = s.charAt(i);
        if (c == '{') {
            return object();
        } else if (c == '[') {
            return array();
        } else if (c == '"') {
            return string();
        } else if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        } else if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        } else if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        expect('{');
        space();
        if (i < s.length() && s.charAt(i) == '}') {
            i += 1;
            return m;
        }
        while (true) {
            space();
            String key = string();
            space();
            expect(':');
            space();
            m.put(key, value());
            space();
            if (i < s.length() && s.charAt(i) == ',') {
                i += 1;
            } else {
                expect('}');
                return m;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        expect('[');
        space();
        if (i < s.length() && s.charAt(i) == ']') {
            i += 1;
            return list;
        }
        while (true) {
            space();
            list.add(value());
            space();
            if (i < s.length() && s.charAt(i) == ',') {
                i += 1;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw error("unterminated string");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: sb.append(e);
            }
        }
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i += 1;
        }
        String n = s.substring(start, i);
        if (n.isEmpty()) {
            throw error("unexpected character");
        }
        if (n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0) {
            return Double.valueOf(n);
        }
        return Long.valueOf(n);
    }
}
