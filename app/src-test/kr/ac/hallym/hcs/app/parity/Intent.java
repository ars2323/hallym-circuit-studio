/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.parity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.data.Location;

/**
 * 의도 하나(N-01): {@code .intents} 파일의 한 줄. 메서드 이름은 docs/engine-api.md의 JSON-RPC 메서드 이름이고, 나머지
 * 키는 그 메서드의 params다(fileId·circuitId 대신 회로 이름 {@code circuit}, 부품은 기호·라벨·자리로 가리킨다).
 */
final class Intent {
    final String file;
    final int line;
    final String method;
    final Map<String, Object> params;

    Intent(String file, int line, String method, Map<String, Object> params) {
        this.file = file;
        this.line = line;
        this.method = method;
        this.params = params;
    }

    @Override
    public String toString() {
        return file + ":" + line + " " + method + " " + params;
    }

    IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(this + ": " + msg);
    }

    boolean has(String key) {
        return params.containsKey(key) && params.get(key) != null;
    }

    String str(String key) {
        Object v = params.get(key);
        if (!(v instanceof String)) {
            throw error("'" + key + "' must be a string");
        }
        return (String) v;
    }

    String optStr(String key) {
        return has(key) ? str(key) : null;
    }

    /** 글자로 적은 값(수나 참·거짓도 .circ에 저장되는 글자로). */
    String text(String key) {
        Object v = params.get(key);
        if (v == null) {
            throw error("'" + key + "' is missing");
        }
        return v instanceof Double && ((Double) v) == Math.rint((Double) v) ? Long.toString(((Double) v).longValue())
                : String.valueOf(v);
    }

    int integer(String key) {
        Object v = params.get(key);
        if (!(v instanceof Long)) {
            throw error("'" + key + "' must be an integer");
        }
        return ((Long) v).intValue();
    }

    int integer(String key, int def) {
        return has(key) ? integer(key) : def;
    }

    double number(String key) {
        Object v = params.get(key);
        if (!(v instanceof Number)) {
            throw error("'" + key + "' must be a number");
        }
        return ((Number) v).doubleValue();
    }

    boolean bool(String key, boolean def) {
        Object v = params.get(key);
        if (v == null) {
            return def;
        }
        if (!(v instanceof Boolean)) {
            throw error("'" + key + "' must be true or false");
        }
        return (Boolean) v;
    }

    Location loc(String key) {
        return toLoc(params.get(key), key);
    }

    private Location toLoc(Object v, String what) {
        if (!(v instanceof List) || ((List<?>) v).size() != 2 || !(((List<?>) v).get(0) instanceof Long)
                || !(((List<?>) v).get(1) instanceof Long)) {
            throw error("'" + what + "' must be [x, y]");
        }
        List<?> l = (List<?>) v;
        return Location.create(((Long) l.get(0)).intValue(), ((Long) l.get(1)).intValue());
    }

    List<Location> points(String key) {
        Object v = params.get(key);
        if (!(v instanceof List)) {
            throw error("'" + key + "' must be a list of [x, y]");
        }
        List<Location> out = new ArrayList<>();
        for (Object o : (List<?>) v) {
            out.add(toLoc(o, key));
        }
        return out;
    }

    int[] ints(String key, int n) {
        Object v = params.get(key);
        if (!(v instanceof List) || ((List<?>) v).size() != n) {
            throw error("'" + key + "' must be a list of " + n + " integers");
        }
        int[] out = new int[n];
        for (int k = 0; k < n; k++) {
            Object o = ((List<?>) v).get(k);
            if (!(o instanceof Long)) {
                throw error("'" + key + "' must be a list of " + n + " integers");
            }
            out[k] = ((Long) o).intValue();
        }
        return out;
    }

    /** 글자 목록. 없으면 빈 목록. 글자 하나면 한 칸짜리 목록. */
    List<String> strings(String key) {
        Object v = params.get(key);
        if (v == null) {
            return Collections.emptyList();
        }
        if (v instanceof String) {
            return Collections.singletonList((String) v);
        }
        if (!(v instanceof List)) {
            throw error("'" + key + "' must be a list of strings");
        }
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) v) {
            if (!(o instanceof String)) {
                throw error("'" + key + "' must be a list of strings");
            }
            out.add((String) o);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> map(String key) {
        Object v = params.get(key);
        if (v == null) {
            return Collections.emptyMap();
        }
        if (!(v instanceof Map)) {
            throw error("'" + key + "' must be an object");
        }
        return (Map<String, Object>) v;
    }
}
