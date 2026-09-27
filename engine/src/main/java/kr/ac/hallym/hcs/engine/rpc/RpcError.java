/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.rpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** JSON-RPC 오류 응답 하나(docs/engine-api.md 2절의 코드). */
public final class RpcError extends Exception {
    private static final long serialVersionUID = 1L;

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    /** 없는 파일·회로·부품 id. */
    public static final int NOT_FOUND = 1;
    /** 파일을 읽거나 쓰지 못함({@code data.path}, {@code data.reason}). */
    public static final int FILE_ERROR = 2;
    /** 편집할 수 없음({@code data.reason}). */
    public static final int NOT_EDITABLE = 3;
    /** 시뮬레이션 상태 때문에 할 수 없음({@code data.reason}). */
    public static final int SIM_STATE = 4;

    private final int code;
    private final transient JsonElement data;

    public RpcError(int code, String message, JsonElement data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public RpcError(int code, String message) {
        this(code, message, null);
    }

    public int code() {
        return code;
    }

    public JsonElement data() {
        return data;
    }

    public static RpcError params(String message) {
        return new RpcError(INVALID_PARAMS, message);
    }

    /** 없는 id. kind는 "file"·"circuit"·"component" 등. */
    public static RpcError notFound(String kind, String id) {
        JsonObject d = new JsonObject();
        d.addProperty("kind", kind);
        d.addProperty("id", id);
        return new RpcError(NOT_FOUND, "no " + kind + " " + id, d);
    }

    public static RpcError file(String path, String reason, String message) {
        JsonObject d = new JsonObject();
        d.addProperty("path", path);
        d.addProperty("reason", reason);
        return new RpcError(FILE_ERROR, message, d);
    }

    public static RpcError notEditable(String reason, String message) {
        JsonObject d = new JsonObject();
        d.addProperty("reason", reason);
        return new RpcError(NOT_EDITABLE, message, d);
    }

    public static RpcError simState(String reason, String message) {
        JsonObject d = new JsonObject();
        d.addProperty("reason", reason);
        return new RpcError(SIM_STATE, message, d);
    }
}
