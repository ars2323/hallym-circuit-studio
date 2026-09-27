/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import kr.ac.hallym.hcs.engine.rpc.RpcError;

/** 전송 규약(docs/engine-api.md 2절): 같은 JVM 안에서 파이프로 주고받는다. */
class ProtocolTest {
    InProcess e;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() throws Exception {
        e.close();
    }

    @Test
    void helloAnswersEngineLogisimAndJava() {
        JsonObject r = e.client.callObject("engine.hello", params("client", "test", "version", "0.0.1"));
        assertEquals("hcs-engine", r.get("engine").getAsString());
        assertEquals("2.7.1", r.get("logisim").getAsString());
        assertEquals(System.getProperty("java.version"), r.get("java").getAsString());
        assertTrue(r.has("version"));
        assertEquals("0", r.get("api").getAsString());
    }

    @Test
    void everyMessageIsOneLineOfJsonRpc20() {
        e.client.call("engine.hello", params("client", "test", "version", "0"));
        e.client.fail("file.open", params("path", "no such\nfile.circ"));
        for (String line : e.client.rawLines()) {
            assertFalse(line.contains("\n"));
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            assertEquals("2.0", o.get("jsonrpc").getAsString());
        }
    }

    @Test
    void parseErrorHasNullId() {
        e.client.send("{not json");
        JsonObject r = e.client.awaitNullIdResponse(0);
        assertEquals(RpcError.PARSE_ERROR, r.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void invalidRequests() {
        e.client.send("[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"engine.hello\"}]"); // 묶음은 받지 않는다
        e.client.send("\"text\"");
        e.client.send("{\"jsonrpc\":\"2.0\",\"id\":{\"a\":1},\"method\":\"engine.hello\"}");
        for (int i = 0; i < 3; i++) {
            assertEquals(RpcError.INVALID_REQUEST,
                    e.client.awaitNullIdResponse(i).getAsJsonObject("error").get("code").getAsInt());
        }
        e.client.send("{\"jsonrpc\":\"1.0\",\"id\":7,\"method\":\"engine.hello\"}");
        assertEquals(RpcError.INVALID_REQUEST, e.client.awaitResponse(7).getAsJsonObject("error").get("code").getAsInt());
        e.client.send("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":5}");
        assertEquals(RpcError.INVALID_REQUEST, e.client.awaitResponse(8).getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void unknownMethodAndBadParams() {
        assertEquals(RpcError.METHOD_NOT_FOUND, e.client.fail("no.such", params()).code);
        e.client.send("{\"jsonrpc\":\"2.0\",\"id\":\"s1\",\"method\":\"file.dirty\",\"params\":[1,2]}");
        JsonObject r = e.client.awaitResponse("s1");
        assertEquals("s1", r.get("id").getAsString(), "string ids come back as strings");
        assertEquals(RpcError.INVALID_PARAMS, r.getAsJsonObject("error").get("code").getAsInt());
        assertEquals(RpcError.INVALID_PARAMS, e.client.fail("file.dirty", params()).code);
        assertEquals(RpcError.INVALID_PARAMS, e.client.fail("file.dirty", params("fileId", 3)).code);
        assertEquals(RpcError.NOT_FOUND, e.client.fail("file.dirty", params("fileId", "f999999")).code);
    }

    @Test
    void notificationsFromTheScreenGetNoResponse() {
        int before = e.client.receivedCount();
        e.client.send("{\"jsonrpc\":\"2.0\",\"method\":\"engine.hello\",\"params\":{}}");
        e.client.send("{\"jsonrpc\":\"2.0\",\"method\":\"no.such\"}");
        e.client.call("engine.hello", params()); // 요청은 차례로 처리되므로 이 응답 앞에 다른 응답이 없어야 한다
        assertEquals(before + 1, e.client.receivedCount());
    }

    @Test
    void shutdownAnswersThenStops() throws Exception {
        assertEquals(new JsonObject(), e.client.call("engine.shutdown", params()));
        assertEquals("shutdown", e.awaitExit(10_000));
        assertTrue(e.client.awaitEof(10_000));
    }

    @Test
    void closingStdinStops() throws Exception {
        e.client.call("engine.hello", params());
        e.client.closeInput();
        assertEquals("stdin closed", e.awaitExit(10_000));
    }

    @Test
    void requestsAreAnsweredInOrder() {
        for (int i = 0; i < 20; i++) {
            e.client.send("{\"jsonrpc\":\"2.0\",\"id\":" + (100 + i) + ",\"method\":\"engine.hello\"}");
        }
        e.client.awaitResponse(119);
        List<String> lines = e.client.rawLines();
        int expected = 100;
        for (String l : lines) {
            JsonObject o = JsonParser.parseString(l).getAsJsonObject();
            if (o.has("id") && !o.has("method")) {
                assertEquals(expected++, o.get("id").getAsInt());
            }
        }
        assertEquals(120, expected);
    }
}
