/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.rpc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** 요청의 이름 있는 인자. 모양이 틀리면 {@link RpcError#INVALID_PARAMS}로 알린다. */
public final class Params {
    private final JsonObject o;

    public Params(JsonObject o) {
        this.o = o == null ? new JsonObject() : o;
    }

    public JsonObject raw() {
        return o;
    }

    public boolean has(String name) {
        return o.has(name) && !o.get(name).isJsonNull();
    }

    public String str(String name) throws RpcError {
        String s = optStr(name, null);
        if (s == null) {
            throw RpcError.params("missing string param '" + name + "'");
        }
        return s;
    }

    public String optStr(String name, String dflt) throws RpcError {
        if (!has(name)) {
            return dflt;
        }
        JsonElement e = o.get(name);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw RpcError.params("param '" + name + "' must be a string");
        }
        return e.getAsString();
    }

    public int integer(String name) throws RpcError {
        if (!has(name)) {
            throw RpcError.params("missing integer param '" + name + "'");
        }
        return toInt(name, o.get(name));
    }

    public int optInt(String name, int dflt) throws RpcError {
        return has(name) ? toInt(name, o.get(name)) : dflt;
    }

    public double optDouble(String name, double dflt) throws RpcError {
        if (!has(name)) {
            return dflt;
        }
        JsonElement e = o.get(name);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw RpcError.params("param '" + name + "' must be a number");
        }
        return e.getAsDouble();
    }

    public boolean bool(String name) throws RpcError {
        if (!has(name)) {
            throw RpcError.params("missing boolean param '" + name + "'");
        }
        return optBool(name, false);
    }

    public boolean optBool(String name, boolean dflt) throws RpcError {
        if (!has(name)) {
            return dflt;
        }
        JsonElement e = o.get(name);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            throw RpcError.params("param '" + name + "' must be a boolean");
        }
        return e.getAsBoolean();
    }

    /** [x, y] 정수 쌍. */
    public int[] point(String name) throws RpcError {
        if (!has(name)) {
            throw RpcError.params("missing point param '" + name + "'");
        }
        return toPoint(name, o.get(name));
    }

    public int[] optPoint(String name) throws RpcError {
        return has(name) ? toPoint(name, o.get(name)) : null;
    }

    /** [[x, y], …]. */
    public List<int[]> points(String name) throws RpcError {
        JsonArray a = array(name);
        List<int[]> ret = new ArrayList<>();
        for (JsonElement e : a) {
            ret.add(toPoint(name, e));
        }
        return ret;
    }

    /** 글자 배열(예: 부품 id 목록). */
    public List<String> strings(String name) throws RpcError {
        JsonArray a = array(name);
        List<String> ret = new ArrayList<>();
        for (JsonElement e : a) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                throw RpcError.params("param '" + name + "' must be an array of strings");
            }
            ret.add(e.getAsString());
        }
        return ret;
    }

    public List<String> optStrings(String name) throws RpcError {
        return has(name) ? strings(name) : new ArrayList<>();
    }

    /** {이름: 글자} 객체(예: 속성). 없으면 빈 것. */
    public Map<String, String> optStringMap(String name) throws RpcError {
        Map<String, String> ret = new LinkedHashMap<>();
        if (!has(name)) {
            return ret;
        }
        JsonElement e = o.get(name);
        if (!e.isJsonObject()) {
            throw RpcError.params("param '" + name + "' must be an object");
        }
        for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
            JsonElement v = en.getValue();
            if (!v.isJsonPrimitive()) {
                throw RpcError.params("param '" + name + "." + en.getKey() + "' must be a string");
            }
            ret.put(en.getKey(), v.getAsString());
        }
        return ret;
    }

    /** 정수 배열(예: 영역 메모의 [x, y, w, h]). */
    public int[] ints(String name) throws RpcError {
        JsonArray a = array(name);
        int[] ret = new int[a.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = toInt(name, a.get(i));
        }
        return ret;
    }

    private JsonArray array(String name) throws RpcError {
        if (!has(name) || !o.get(name).isJsonArray()) {
            throw RpcError.params("param '" + name + "' must be an array");
        }
        return o.get(name).getAsJsonArray();
    }

    private static int toInt(String name, JsonElement e) throws RpcError {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            double d = e.getAsDouble();
            if (d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE) {
                return (int) d;
            }
        }
        throw RpcError.params("param '" + name + "' must be an integer");
    }

    private static int[] toPoint(String name, JsonElement e) throws RpcError {
        if (e.isJsonArray() && e.getAsJsonArray().size() == 2) {
            JsonArray a = e.getAsJsonArray();
            return new int[] {toInt(name, a.get(0)), toInt(name, a.get(1))};
        }
        throw RpcError.params("param '" + name + "' must be [x, y]");
    }

    /** 요청 id로 쓸 수 있는 값인가(숫자, 글자, null). */
    static boolean validId(JsonElement id) {
        if (id == null || id.isJsonNull()) {
            return true;
        }
        if (!id.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive p = id.getAsJsonPrimitive();
        return p.isNumber() || p.isString();
    }
}
