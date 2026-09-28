/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 테스트용 화면 쪽: 요청을 한 줄씩 쓰고, 응답과 알림을 받은 순서대로 모은다. 같은 JVM(파이프)과 하위 프로세스
 * 양쪽에 쓴다.
 */
final class Client implements AutoCloseable {
    /** 오류 응답. */
    static final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int code;
        final JsonObject error;

        Failure(JsonObject error) {
            super(error.get("code").getAsInt() + ": " + error.get("message").getAsString());
            this.code = error.get("code").getAsInt();
            this.error = error;
        }

        String reason() {
            JsonObject d = error.has("data") ? error.getAsJsonObject("data") : null;
            return d != null && d.has("reason") ? d.get("reason").getAsString() : null;
        }
    }

    static final long TIMEOUT_MS = 30_000;

    /** 기다리다 실패할 때 문구에 담는 마지막 메시지 수와 메시지 하나의 글자 수(문구가 힙을 넘지 않게). */
    static final int TAIL = 10;
    static final int TAIL_CHARS = 400;

    private final OutputStream out;
    private final Thread reader;
    /** 받은 모든 메시지(응답과 알림) 순서대로. {@link #lean()}이면 아직 가져가지 않은 응답만. */
    private final List<JsonObject> received = new ArrayList<>();
    private final List<String> raw = new ArrayList<>();
    /** 메서드마다 받은 알림 수(버린 것도 센다). */
    private final Map<String, Integer> notified = new TreeMap<>();
    private boolean lean;
    private boolean eof;
    private int nextId = 1;

    Client(InputStream fromEngine, OutputStream toEngine) {
        this.out = toEngine;
        this.reader = new Thread(() -> read(fromEngine), "test-client-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void read(InputStream in) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                synchronized (this) {
                    String method = optString(o, "method");
                    if (method != null) {
                        notified.merge(method, 1, Integer::sum);
                    }
                    if (lean && method != null) {
                        continue; // 알림은 세기만 한다
                    }
                    if (!lean) {
                        raw.add(line);
                    }
                    received.add(o);
                    notifyAll();
                }
            }
        } catch (IOException | RuntimeException e) {
            // 파이프가 닫혔다
        }
        synchronized (this) {
            eof = true;
            notifyAll();
        }
    }

    /** 한 줄을 그대로 보낸다. */
    synchronized void send(String line) {
        try {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 요청을 보내고 응답 객체(result 또는 error가 든 것)를 기다린다. */
    JsonObject request(String method, JsonObject params) {
        int id;
        synchronized (this) {
            id = nextId++;
        }
        JsonObject req = new JsonObject();
        req.addProperty("jsonrpc", "2.0");
        req.addProperty("id", id);
        req.addProperty("method", method);
        req.add("params", params == null ? new JsonObject() : params);
        send(req.toString());
        return awaitResponse(id);
    }

    /** result를 돌려주고, 오류면 {@link Failure}. */
    JsonElement call(String method, JsonObject params) {
        JsonObject resp = request(method, params);
        if (resp.has("error")) {
            throw new Failure(resp.getAsJsonObject("error"));
        }
        return resp.get("result");
    }

    JsonObject callObject(String method, JsonObject params) {
        return call(method, params).getAsJsonObject();
    }

    /** 오류 응답을 기대한다. */
    Failure fail(String method, JsonObject params) {
        JsonObject resp = request(method, params);
        if (!resp.has("error")) {
            throw new AssertionError("expected an error from " + method + " but got " + resp);
        }
        return new Failure(resp.getAsJsonObject("error"));
    }

    synchronized JsonObject awaitResponse(Object id) {
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (true) {
            for (JsonObject o : received) {
                if (o.has("id") && !o.get("id").isJsonNull() && o.get("id").getAsString().equals(id.toString())
                        && !o.has("method")) {
                    if (lean) {
                        received.remove(o);
                    }
                    return o;
                }
            }
            waitUntil(end, "response " + id);
        }
    }

    /**
     * 이제부터 알림은 세기만 하고 버리며, 응답은 돌려준 뒤 잊는다. 요청과 응답만 쓰는 오래 도는 테스트(엔진이 프레임마다
     * 보내는 값·진단 알림이 쌓여 테스트 JVM의 힙을 채우지 않게). 알림을 기다리는 메서드는 쓸 수 없다.
     */
    synchronized void lean() {
        lean = true;
        received.removeIf(o -> o.has("method"));
        raw.clear();
    }

    /** 응답 가운데 id가 null인 것(구문 오류 등)을 기다린다. */
    synchronized JsonObject awaitNullIdResponse(int index) {
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (true) {
            int seen = 0;
            for (JsonObject o : received) {
                if (o.has("id") && o.get("id").isJsonNull()) {
                    if (seen++ == index) {
                        return o;
                    }
                }
            }
            waitUntil(end, "null-id response " + index);
        }
    }

    /** 이름이 method이고 조건을 채우는 알림(받은 것 가운데 첫째, 없으면 기다린다). */
    synchronized JsonObject awaitNotification(String method, Predicate<JsonObject> test) {
        return awaitNotificationAfter(0, method, test);
    }

    /** from번째 받은 메시지 뒤에서 찾는다. */
    synchronized JsonObject awaitNotificationAfter(int from, String method, Predicate<JsonObject> test) {
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (true) {
            for (int i = from; i < received.size(); i++) {
                JsonObject o = received.get(i);
                if (method.equals(optString(o, "method")) && test.test(o.getAsJsonObject("params"))) {
                    return o.getAsJsonObject("params");
                }
            }
            waitUntil(end, "notification " + method);
        }
    }

    /** 지금까지 받은 메시지 수(뒤에 오는 알림만 보려고). */
    synchronized int mark() {
        return received.size();
    }

    synchronized List<JsonObject> notifications(String method) {
        List<JsonObject> ret = new ArrayList<>();
        for (JsonObject o : received) {
            if (method.equals(optString(o, "method"))) {
                ret.add(o.getAsJsonObject("params"));
            }
        }
        return ret;
    }

    synchronized List<JsonObject> notificationsAfter(int from, String method) {
        List<JsonObject> ret = new ArrayList<>();
        for (int i = from; i < received.size(); i++) {
            JsonObject o = received.get(i);
            if (method.equals(optString(o, "method"))) {
                ret.add(o.getAsJsonObject("params"));
            }
        }
        return ret;
    }

    synchronized List<String> rawLines() {
        return new ArrayList<>(raw);
    }

    synchronized int receivedCount() {
        return received.size();
    }

    synchronized boolean awaitEof(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (!eof) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) {
                return false;
            }
            try {
                wait(left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** 엔진의 stdin을 닫는다. */
    void closeInput() {
        try {
            out.close();
        } catch (IOException e) {
            // 이미 닫힘
        }
    }

    @Override
    public void close() {
        closeInput();
    }

    private void waitUntil(long end, String what) {
        long left = end - System.currentTimeMillis();
        if (left <= 0) {
            throw new AssertionError("timed out waiting for " + what + "; " + received());
        }
        if (eof) {
            throw new AssertionError("engine output closed while waiting for " + what + "; " + received());
        }
        try {
            wait(Math.min(left, 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /**
     * 기다리다 실패할 때의 문구: 받은 수, 메서드마다의 알림 수, 마지막 {@link #TAIL}개(하나에 {@link #TAIL_CHARS}자까지).
     * 받은 것 전부를 글자로 만들면 오래 돈 테스트에서는 그것만으로 힙을 넘는다.
     */
    private String received() {
        StringBuilder b = new StringBuilder("received ").append(received.size()).append(" messages, notifications ")
                .append(notified).append("; the last ").append(Math.min(TAIL, received.size())).append(':');
        for (int i = Math.max(0, received.size() - TAIL); i < received.size(); i++) {
            String s = received.get(i).toString();
            b.append("\n  ").append(s.length() <= TAIL_CHARS ? s : s.substring(0, TAIL_CHARS) + "… (" + s.length()
                    + " chars)");
        }
        return b.toString();
    }

    private static String optString(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    // ---- 인자 만들기 ----

    /** {"k": v, …}. v는 String, Number, Boolean, JsonElement, int[] (좌표). */
    static JsonObject params(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            o.add(k, toJson(v));
        }
        return o;
    }

    static JsonElement toJson(Object v) {
        if (v == null) {
            return com.google.gson.JsonNull.INSTANCE;
        } else if (v instanceof JsonElement) {
            return (JsonElement) v;
        } else if (v instanceof String) {
            return new com.google.gson.JsonPrimitive((String) v);
        } else if (v instanceof Number) {
            return new com.google.gson.JsonPrimitive((Number) v);
        } else if (v instanceof Boolean) {
            return new com.google.gson.JsonPrimitive((Boolean) v);
        } else if (v instanceof int[]) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (int x : (int[]) v) {
                a.add(x);
            }
            return a;
        } else if (v instanceof Object[]) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (Object x : (Object[]) v) {
                a.add(toJson(x));
            }
            return a;
        } else if (v instanceof List) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (Object x : (List<?>) v) {
                a.add(toJson(x));
            }
            return a;
        } else if (v instanceof java.util.Map) {
            JsonObject o = new JsonObject();
            for (java.util.Map.Entry<?, ?> e : ((java.util.Map<?, ?>) v).entrySet()) {
                o.add(e.getKey().toString(), toJson(e.getValue()));
            }
            return o;
        }
        throw new IllegalArgumentException("cannot convert " + v);
    }

    static int[] xy(int x, int y) {
        return new int[] {x, y};
    }
}
