/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.LogisimRace;

/**
 * 엔진 스레드의 편집·모델·진단 요청과 원조 시뮬레이터 스레드의 사이클이 겹쳐도 시뮬레이터 스레드가 죽지 않고, 편집은
 * 모두 되고, 시뮬레이션은 꺼지지 않는다(D-143). 기록 엔진의 캡처와 동적 진단(DynamicCheck → Names 등)은 시뮬레이터
 * 스레드에서 회로의 부품 집합·선·넷을 훑는다. 원조 회로 읽기 잠금 없이 훑던 때는 엔진 스레드가 부품을 넣고 빼는 동안
 * ConcurrentModificationException이 나서 시뮬레이터 스레드가 끝났다(몇 번의 편집 안에 재현됐다). 고장 회로
 * broken-datapath는 클럭 에지마다 X 쓰기 검사가 돌고(MemWrite 3상태), 편집마다 기록을 새로 시작해 원인 문장(부품
 * 이름·번호)을 다시 만든다. 옮기기는 엔진의 화면 없는 Canvas를 만든다(원조 Canvas는 클럭 틱마다 시뮬레이터 스레드에서
 * 회로 경계를 쟀다). 서브회로(alu)에 핀을 넣고 빼는 편집은 원조가 두 회로(alu, main)의 쓰기 잠금을 함께 쥐는 경우다
 * (읽기 잠금과 교착하지 않는지).
 * <p>
 * 원조 CircuitState 청취자(엔진 스레드)와 원조 전파(시뮬레이터 스레드)가 더러운 부품·점 집합을 함께 고치던 원조 자체의
 * 경합은 엔진이 편집을 전파 밖에서 하므로({@code SimGate}) 나지 않는다: 편집이 원조 청취자 안에서 끝나거나 원조 전파가
 * 예외를 잡아 찍으면(시뮬레이션을 끄면) 실패한다.
 */
class SimEditRaceTest {
    static final File BROKEN = new File(System.getProperty("hcs.electronFixtures"), "broken-datapath.circ");
    /** 편집 묶음 수와 시간 상한(상수 identity hash JVM에서는 느리다: 시간이 먼저 끝나도 된다). */
    static final int ROUNDS = 80;
    static final long MAX_MS = 5000;

    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    /** 테스트 동안 스레드가 잡지 못한 예외로 끝난 것(시뮬레이터 스레드는 원조 코드라 처리기가 없다). */
    final List<String> uncaught = new CopyOnWriteArrayList<>();
    Thread.UncaughtExceptionHandler before;
    /** 원조 전파가 잡아 찍는 예외(System.err 사본). */
    LogisimRace race;

    @BeforeEach
    void start() throws Exception {
        before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, x) -> {
            StringWriter w = new StringWriter();
            x.printStackTrace(new PrintWriter(w));
            uncaught.add(t.getName() + ": " + w);
        });
        race = LogisimRace.watch();
        e = new InProcess();
        // 요청·응답만 쓴다: 사이클이 도는 동안 프레임마다 오는 값·기록·진단 알림(진단은 목록 전체)을 쌓아 두면 빠른
        // 사이클에서 테스트 JVM의 힙이 찬다(PR #431의 CI에서 엔진 출력이 끊기고 그 실패 문구를 만들다 힙이 넘쳤다)
        e.client.lean();
    }

    /** 실패 문구에 넣을 글(길면 앞만). */
    static String brief(Object o) {
        String s = String.valueOf(o);
        return s.length() <= 2000 ? s : s.substring(0, 2000) + "… (" + s.length() + " chars)";
    }

    @AfterEach
    void stop() {
        e.close();
        race.close();
        Thread.setDefaultUncaughtExceptionHandler(before);
    }

    JsonObject call(String method, Object... kv) {
        return e.client.callObject(method, params(kv));
    }

    long cycle() {
        return call("sim.state", "fileId", fileId).get("cycle").getAsLong();
    }

    void noUncaught(String when) {
        if (!uncaught.isEmpty()) {
            fail(uncaught.size() + " threads died " + when + ", the first:\n" + brief(uncaught.get(0)));
        }
    }

    static boolean hasDynamic(JsonArray messages) {
        for (JsonElement m : messages) {
            if (m.getAsJsonObject().get("kind").getAsString().equals("dynamic")) {
                return true;
            }
        }
        return false;
    }

    @Test
    void editsAndQueriesDuringCyclesDoNotKillTheSimulatorThread() throws Exception {
        File f = Fixtures.copyWithSiblings(BROKEN, tmp);
        JsonObject opened = call("file.open", "path", f.getPath());
        fileId = opened.get("fileId").getAsString();
        String main = opened.get("main").getAsString();
        String alu = null;
        for (JsonElement c : opened.getAsJsonArray("circuits")) {
            if (c.getAsJsonObject().get("name").getAsString().equals("alu")) {
                alu = c.getAsJsonObject().get("circuitId").getAsString();
            }
        }
        assertTrue(alu != null, "broken-datapath has the alu subcircuit");

        call("sim.cycles", "fileId", fileId, "n", 1_000_000);
        long cycleAtStart = cycle();
        long end = System.currentTimeMillis() + MAX_MS;
        int rounds = 0;
        while (rounds < ROUNDS && System.currentTimeMillis() < end) {
            rounds++;
            // 부품 여럿을 잇달아 넣는다(편집마다 시뮬레이터 스레드가 기록을 새로 시작하며 넷을 다시 읽는다)
            List<String> ids = new ArrayList<>();
            for (int k = 0; k < 4; k++) {
                int x = 2000 + 60 * k;
                int y = 2000 + 90 * rounds;
                ids.add(call("edit.addComponent", "fileId", fileId, "circuitId", main, "lib", "Gates", "name",
                        "NOT Gate", "loc", new int[] {x, y}).get("id").getAsString());
            }
            call("edit.setAttr", "fileId", fileId, "circuitId", main, "ids", new Object[] {ids.get(0)}, "attr",
                    "label", "value", "n" + rounds);
            // 옮기면 원조는 새 부품을 넣는다(새 id): 옮긴 것은 두고 나머지를 지운다
            call("edit.move", "fileId", fileId, "circuitId", main, "ids", new Object[] {ids.remove(1)}, "dx", 0,
                    "dy", 30);
            call("model.circuit", "fileId", fileId, "circuitId", main);
            call("diag.list", "fileId", fileId);
            call("edit.delete", "fileId", fileId, "circuitId", main, "ids", ids.toArray());
            call("edit.undo", "fileId", fileId);
            call("edit.redo", "fileId", fileId);
            if (rounds % 3 == 0) {
                // 서브회로의 핀: main의 alu 인스턴스 포트가 바뀐다(원조가 alu·main 쓰기 잠금을 함께 쥔다)
                call("edit.addComponent", "fileId", fileId, "circuitId", alu, "lib", "Wiring", "name", "Pin", "loc",
                        new int[] {1500, 1500 + 20 * (rounds % 10)});
                call("model.circuit", "fileId", fileId, "circuitId", main);
                call("edit.undo", "fileId", fileId);
            }
            noUncaught("after " + rounds + " rounds of edits");
        }
        assertTrue(rounds >= 10, "only " + rounds + " rounds ran");

        // 시뮬레이터 스레드가 살아 있다: 사이클이 늘고, 동적 진단(X 쓰기)이 다시 보인다
        long wait = System.currentTimeMillis() + Client.TIMEOUT_MS;
        while (cycle() <= cycleAtStart + 2 && System.currentTimeMillis() < wait) {
            Thread.sleep(20);
        }
        assertTrue(cycle() > cycleAtStart + 2, "the cycles stopped: " + cycleAtStart + " → " + cycle());
        JsonArray messages = call("diag.list", "fileId", fileId).getAsJsonArray("messages");
        while (!hasDynamic(messages) && System.currentTimeMillis() < wait) {
            Thread.sleep(20);
            messages = call("diag.list", "fileId", fileId).getAsJsonArray("messages");
        }
        assertTrue(hasDynamic(messages), "no dynamic message after the edits: " + brief(messages));
        noUncaught("after the edits");
        // 편집이 전파와 겹치지 않았다: 원조 전파가 잡은 예외가 없고 시뮬레이션은 켜져 있다
        List<String> caught = race.propagatorTraces();
        assertTrue(caught.isEmpty(), () -> caught.size() + " exceptions caught by Logisim's propagator, the first:\n"
                + brief(caught.get(0)));
        assertTrue(call("sim.state", "fileId", fileId).get("running").getAsBoolean(), "the simulation is off");
    }
}
