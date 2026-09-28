/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.rpc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * JSON-RPC 2.0, 한 줄에 한 객체(docs/engine-api.md 2절). 읽기는 따로 된 스레드가 하고, 요청·알림·시뮬레이션 사건은
 * 모두 엔진 스레드 하나({@link #executor()})에서 차례로 처리한다(Swing 앱의 이벤트 스레드 자리). stdout에는 규약
 * 메시지만 쓰고 로그는 stderr로 보낸다. stdin이 닫히거나 {@code engine.shutdown}을 받으면 정리하고 {@link #serve}가
 * 돌아온다.
 */
public final class Server {
    /** 메서드 하나. params는 늘 객체이고, 돌려준 값이 result다(null이면 {}). */
    public interface Handler {
        JsonElement handle(Params params, Call call) throws RpcError;
    }

    /** 요청 하나의 문맥: 응답을 보낸 뒤 할 일(예: model.changed 알림)을 둔다. */
    public static final class Call {
        private final List<Runnable> after = new ArrayList<>();

        /** 응답을 쓴 뒤 엔진 스레드에서 실행한다. */
        public void after(Runnable r) {
            after.add(r);
        }
    }

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private final InputStream in;
    private final Writer out;
    private final PrintStream log;
    private final Map<String, Handler> handlers = new HashMap<>();
    private final List<Runnable> shutdownHooks = new ArrayList<>();
    private final ScheduledExecutorService executor;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean closing;
    private volatile Thread engineThread;
    private String shutdownReason;
    /** 화면이 사라진 뒤 끝나야 하는 시한(ms). 0이면 없다(같은 JVM의 테스트). {@link #haltAfter}. */
    private volatile long endDeadlineMs;
    private final java.util.concurrent.atomic.AtomicBoolean deadlineStarted = new java.util.concurrent.atomic.AtomicBoolean();

    public Server(InputStream in, OutputStream out, PrintStream log) {
        this.in = in;
        this.out = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        this.log = log;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hcs-engine");
            t.setDaemon(true);
            engineThread = t;
            return t;
        });
    }

    public void register(String method, Handler h) {
        handlers.put(method, h);
    }

    /** 단 메서드 이름들(정렬). 테스트가 모든 메서드 묶음을 한 번씩 부르는지 본다(실습실 규칙, N-19). */
    public java.util.SortedSet<String> methods() {
        return new java.util.TreeSet<>(handlers.keySet());
    }

    /** 이 메서드를 받는가(테스트: 아직 없는 의도를 쓰는 편집 동등성 장면). */
    public boolean handles(String method) {
        return handlers.containsKey(method);
    }

    /** 끝낼 때(엔진 스레드에서) 부를 일. */
    public void onShutdown(Runnable r) {
        shutdownHooks.add(r);
    }

    /** 엔진 스레드. 모델과 시뮬레이션 제어는 여기서만 건드린다. */
    public ScheduledExecutorService executor() {
        return executor;
    }

    public boolean isEngineThread() {
        return Thread.currentThread() == engineThread;
    }

    /** 엔진 스레드에서 실행한다(끝나는 중이면 버린다). */
    public void submit(Runnable r) {
        try {
            executor.execute(() -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    log("error", "engine task failed: " + t, true);
                    t.printStackTrace(log);
                }
            });
        } catch (RejectedExecutionException e) {
            // 끝나는 중
        }
    }

    /**
     * 읽기를 시작하고 끝날 때까지 기다린다. 끝난 이유를 돌려준다("shutdown", "stdin closed", "stdout closed").
     */
    public String serve() throws InterruptedException {
        Thread reader = new Thread(this::readLoop, "hcs-rpc-reader");
        reader.setDaemon(true);
        reader.start();
        done.await();
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        return shutdownReason;
    }

    /**
     * 끝나는 이유("shutdown": 화면이 {@code engine.shutdown}으로 끝냄, "stdin closed", "parent ended", "stdout closed").
     * 끝낼 때 부르는 일({@link #onShutdown})이 이것을 보고 정상 종료와 화면이 사라진 끝을 가른다(N-19, D-152).
     * 끝나기 전에는 null.
     */
    public String shutdownReason() {
        return shutdownReason;
    }

    /**
     * 화면이 사라져 끝날 때(stdin 닫힘·부모 끝남)의 시한(N-19, D-152). 끝내기는 엔진 스레드에 줄을 서므로, 엔진 스레드가
     * 바쁘거나 멈춰 있으면 끝나지 않는다: 그 뒤 ms 안에 정리가 끝나지 않으면 프로세스를 멈춘다({@code Runtime.halt}).
     * Windows에서 엔진은 부모와 함께 죽는 작업 개체 밖에 있으므로(detached) 이것이 떠도는 java를 막는다. Main만 켠다.
     */
    public void haltAfter(long ms) {
        endDeadlineMs = ms;
    }

    /** 화면(클라이언트)이 사라졌다: 끝내기를 요청하고, 시한이 있으면 그 시계를 건다. */
    public void clientGone(String reason) {
        long ms = endDeadlineMs;
        if (ms > 0 && deadlineStarted.compareAndSet(false, true)) {
            Thread t = new Thread(() -> {
                try {
                    if (!done.await(ms, TimeUnit.MILLISECONDS)) {
                        log("error", "not ended " + ms + " ms after " + reason + ": halting", false);
                        log.flush();
                        Runtime.getRuntime().halt(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "hcs-end-deadline");
            t.setDaemon(true);
            t.start();
        }
        requestShutdown(reason);
    }

    /** 끝내기를 요청한다(엔진 스레드에서 정리한 뒤 {@link #serve}가 돌아온다). */
    public void requestShutdown(String reason) {
        submit(() -> shutdown(reason));
    }

    private void shutdown(String reason) {
        if (closing) {
            return;
        }
        closing = true;
        shutdownReason = reason;
        log("info", "shutting down: " + reason, false);
        for (Runnable r : shutdownHooks) {
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace(log);
            }
        }
        done.countDown();
    }

    private void readLoop() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while (!closing && (line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String l = line;
                submit(() -> handleLine(l));
            }
        } catch (IOException e) {
            log("warn", "stdin: " + e, false);
        }
        clientGone("stdin closed");
    }

    /** 한 줄을 처리한다(엔진 스레드). */
    void handleLine(String line) {
        if (closing) {
            return;
        }
        JsonElement el;
        try {
            el = JsonParser.parseString(line);
        } catch (JsonParseException e) {
            sendError(JsonNull.INSTANCE, RpcError.PARSE_ERROR, "parse error: " + e.getMessage(), null);
            return;
        }
        if (!el.isJsonObject()) {
            sendError(JsonNull.INSTANCE, RpcError.INVALID_REQUEST,
                    el.isJsonArray() ? "batch requests are not supported" : "a request must be a JSON object", null);
            return;
        }
        JsonObject o = el.getAsJsonObject();
        boolean notification = !o.has("id");
        JsonElement id = notification ? JsonNull.INSTANCE : o.get("id");
        if (!Params.validId(id)) {
            sendError(JsonNull.INSTANCE, RpcError.INVALID_REQUEST, "id must be a number, a string or null", null);
            return;
        }
        JsonElement version = o.get("jsonrpc");
        JsonElement method = o.get("method");
        if (version == null || !version.isJsonPrimitive() || !"2.0".equals(version.getAsString())) {
            sendError(id, RpcError.INVALID_REQUEST, "jsonrpc must be \"2.0\"", null);
            return;
        }
        if (method == null || !method.isJsonPrimitive() || !method.getAsJsonPrimitive().isString()) {
            sendError(id, RpcError.INVALID_REQUEST, "method must be a string", null);
            return;
        }
        JsonElement params = o.get("params");
        if (params != null && !params.isJsonNull() && !params.isJsonObject()) {
            if (!notification) {
                sendError(id, RpcError.INVALID_PARAMS, "params must be an object", null);
            }
            return;
        }
        Handler h = handlers.get(method.getAsString());
        if (h == null) {
            if (!notification) {
                sendError(id, RpcError.METHOD_NOT_FOUND, "method not found: " + method.getAsString(), null);
            }
            return;
        }
        Call call = new Call();
        try {
            JsonElement result = h.handle(new Params(params == null || params.isJsonNull() ? null
                    : params.getAsJsonObject()), call);
            if (!notification) {
                JsonObject resp = new JsonObject();
                resp.addProperty("jsonrpc", "2.0");
                resp.add("id", id);
                resp.add("result", result == null ? new JsonObject() : result);
                write(resp);
            }
        } catch (RpcError e) {
            if (!notification) {
                sendError(id, e.code(), e.getMessage(), e.data());
            }
        } catch (Throwable t) {
            log("error", method.getAsString() + " failed: " + t, false);
            t.printStackTrace(log);
            if (!notification) {
                sendError(id, RpcError.INTERNAL_ERROR, "internal error: " + t, null);
            }
        } finally {
            for (Runnable r : call.after) {
                try {
                    r.run();
                } catch (Throwable t) {
                    log("error", "after " + method.getAsString() + ": " + t, true);
                    t.printStackTrace(log);
                }
            }
        }
    }

    /** 알림(id 없음)을 보낸다. */
    public void notify(String method, JsonElement params) {
        JsonObject msg = new JsonObject();
        msg.addProperty("jsonrpc", "2.0");
        msg.addProperty("method", method);
        msg.add("params", params == null ? new JsonObject() : params);
        write(msg);
    }

    /** stderr에 남기고, notify면 화면에도 {@code engine.log}로 알린다. */
    public void log(String level, String message, boolean notify) {
        log.println("[hcs-engine] " + level + ": " + message);
        log.flush();
        if (notify && !closing) {
            JsonObject p = new JsonObject();
            p.addProperty("level", level);
            p.addProperty("message", message);
            notify("engine.log", p);
        }
    }

    private void sendError(JsonElement id, int code, String message, JsonElement data) {
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        if (data != null) {
            err.add("data", data);
        }
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id);
        resp.add("error", err);
        write(resp);
    }

    private synchronized void write(JsonObject msg) {
        try {
            out.write(GSON.toJson(msg));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            if (!closing) {
                log.println("[hcs-engine] error: stdout: " + e);
                clientGone("stdout closed");
            }
        }
    }
}
