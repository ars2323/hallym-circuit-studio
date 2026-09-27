/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.parity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 의도 파일 한 줄(JSON 객체)을 읽는 작은 파서(N-01). 객체는 {@link LinkedHashMap}(키 순서 유지), 배열은
 * {@link List}, 수는 {@link Long} 또는 {@link Double}, 그 밖에 {@link String}·{@link Boolean}·null. 앱 테스트
 * 클래스 경로에 JSON 라이브러리를 더하지 않으려고 따로 둔다.
 */
final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) {
            throw p.error("unexpected text after the value");
        }
        return v;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(msg + " at column " + (i + 1));
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private Object value() {
        if (i >= s.length()) {
            throw error("value expected");
        }
        char c = s.charAt(i);
        switch (c) {
        case '{':
            return object();
        case '[':
            return array();
        case '"':
            return string();
        case 't':
            return word("true", Boolean.TRUE);
        case 'f':
            return word("false", Boolean.FALSE);
        case 'n':
            return word("null", null);
        default:
            if (c == '-' || Character.isDigit(c)) {
                return number();
            }
            throw error("unexpected '" + c + "'");
        }
    }

    private Object word(String w, Object v) {
        if (!s.startsWith(w, i)) {
            throw error(w + " expected");
        }
        i += w.length();
        return v;
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (peek('}')) {
            i++;
            return m;
        }
        while (true) {
            ws();
            if (!peek('"')) {
                throw error("key expected");
            }
            String k = string();
            ws();
            expect(':');
            ws();
            if (m.containsKey(k)) {
                throw error("duplicate key " + k);
            }
            m.put(k, value());
            ws();
            if (peek(',')) {
                i++;
                continue;
            }
            expect('}');
            return m;
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (peek(']')) {
            i++;
            return l;
        }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (peek(',')) {
                i++;
                continue;
            }
            expect(']');
            return l;
        }
    }

    private String string() {
        i++;
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                return b.toString();
            }
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (i >= s.length()) {
                break;
            }
            char e = s.charAt(i++);
            switch (e) {
            case 'n':
                b.append('\n');
                break;
            case 't':
                b.append('\t');
                break;
            case 'r':
                b.append('\r');
                break;
            case 'b':
                b.append('\b');
                break;
            case 'f':
                b.append('\f');
                break;
            case 'u':
                if (i + 4 > s.length()) {
                    throw error("bad \\u escape");
                }
                b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                i += 4;
                break;
            default:
                b.append(e);
            }
        }
        throw error("unterminated string");
    }

    private Object number() {
        int start = i;
        if (peek('-')) {
            i++;
        }
        boolean real = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                i++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                real = true;
                i++;
            } else {
                break;
            }
        }
        String t = s.substring(start, i);
        try {
            return real ? (Object) Double.valueOf(t) : (Object) Long.valueOf(t);
        } catch (NumberFormatException e) {
            throw error("bad number " + t);
        }
    }

    private boolean peek(char c) {
        return i < s.length() && s.charAt(i) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("'" + c + "' expected");
        }
        i++;
    }
}
