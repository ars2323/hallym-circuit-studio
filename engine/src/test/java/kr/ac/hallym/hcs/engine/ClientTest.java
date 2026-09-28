/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/**
 * 테스트용 화면(Client)이 오래 도는 테스트에서 힙을 채우지 않는다: 기다리다 실패한 문구는 받은 것 전부가 아니라 수와
 * 마지막 몇 개(잘라서)만 담고, 알림을 버리는 모드는 응답만 들고 있는다(PR #431 CI: SimEditRaceTest가 엔진 출력이 끊긴
 * 뒤 받은 알림 전부로 문구를 만들다 힙을 넘었다).
 */
class ClientTest {
    /** 큰 알림 n개를 보내고 엔진 쪽 출력을 닫는다. */
    static void feed(PipedOutputStream engineOut, int n) throws IOException {
        String big = "x".repeat(20_000);
        for (int i = 0; i < n; i++) {
            String line = "{\"jsonrpc\":\"2.0\",\"method\":\"diag.changed\",\"params\":{\"i\":" + i + ",\"pad\":\"" + big
                    + "\"}}\n";
            engineOut.write(line.getBytes(StandardCharsets.UTF_8));
        }
        engineOut.close();
    }

    @Test
    void theMessageWhenTheEngineStopsIsBounded() throws Exception {
        PipedOutputStream engineOut = new PipedOutputStream();
        Client c = new Client(new PipedInputStream(engineOut, 1 << 16), new ByteArrayOutputStream());
        Thread t = new Thread(() -> {
            try {
                feed(engineOut, 200);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        t.start();
        AssertionError e = assertThrows(AssertionError.class, () -> c.call("diag.list", new JsonObject()));
        t.join();
        String m = e.getMessage();
        assertTrue(m.startsWith("engine output closed while waiting for response 1"),
                () -> m.substring(0, Math.min(300, m.length())));
        assertTrue(m.length() < (Client.TAIL + 1) * (Client.TAIL_CHARS + 50) + 500, "the message is " + m.length()
                + " chars (4 MB received)");
        assertTrue(m.contains("{diag.changed=200}"), () -> m.substring(0, Math.min(300, m.length())));
    }

    @Test
    void aLeanClientKeepsOnlyTheResponsesItHasNotHandedOut() throws Exception {
        PipedOutputStream engineOut = new PipedOutputStream();
        Client c = new Client(new PipedInputStream(engineOut, 1 << 16), new ByteArrayOutputStream());
        c.lean();
        Thread t = new Thread(() -> {
            try {
                String big = "x".repeat(20_000);
                for (int i = 0; i < 100; i++) {
                    engineOut.write(("{\"jsonrpc\":\"2.0\",\"method\":\"sim.values\",\"params\":{\"pad\":\"" + big
                            + "\"}}\n").getBytes(StandardCharsets.UTF_8));
                }
                engineOut.write("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"ok\":true}}\n"
                        .getBytes(StandardCharsets.UTF_8));
                engineOut.flush();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        t.start();
        assertTrue(c.call("any", new JsonObject()).getAsJsonObject().get("ok").getAsBoolean());
        t.join();
        assertEquals(0, c.receivedCount(), "notifications dropped, the response forgotten once handed out");
        engineOut.close();
    }
}
