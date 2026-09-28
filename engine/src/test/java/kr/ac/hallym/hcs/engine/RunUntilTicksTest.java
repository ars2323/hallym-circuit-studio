/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Simulator;
import com.google.gson.JsonObject;

/**
 * Run Until의 틱 스레드 깨우기(SimSession.fastTicks, D-144 결정 3): 도는 동안 원조 틱 주파수는 1024이고 sim.state의 hz는
 * 학생이 고른 값이다. 도는 동안 바꾼 속도는 끝난 뒤 적용되고, Run(틱 켜기)은 받지 않는다. 끝나면(만남·한계·멈춤·
 * 시뮬레이션 꺼짐·파일 닫기) 학생의 값으로 돌아온다.
 */
class RunUntilTicksTest {
    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
        JsonObject r = e.client.callObject("file.open", params("path", Fixtures.counter(tmp).getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        // the student's speed: 4 Hz, the clock off
        e.client.call("sim.run", params("fileId", fileId, "on", false, "hz", 4));
    }

    @AfterEach
    void stop() {
        e.close();
    }

    Simulator simulator() throws Exception {
        return e.onEngine(() -> e.engine.files().get(fileId).project().getSimulator());
    }

    double ticker() throws Exception {
        Simulator s = simulator();
        return e.onEngine(s::getTickFrequency);
    }

    double reported() {
        return e.client.callObject("sim.state", params("fileId", fileId)).get("hz").getAsDouble();
    }

    /** Starts a Run Until that does not end by itself (a PC the counter has not). */
    void startLong() {
        e.client.call("record.runUntil", params("fileId", fileId, "kind", "pc", "value", "0x00400000", "maxCycles",
                1_000_000));
    }

    JsonObject awaitDone(int mark) {
        return e.client.awaitNotificationAfter(mark, "record.runUntil", n -> n.get("fileId").getAsString()
                .equals(fileId));
    }

    @Test
    void whileItRunsTheTickerIsFastAndTheStudentsSpeedIsReported() throws Exception {
        assertEquals(4.0, ticker());
        int mark = e.client.mark();
        startLong();
        assertEquals(1024.0, ticker(), "the ticker wakes every millisecond");
        assertEquals(4.0, reported(), "sim.state says the student's speed");
        // a new speed while it runs: kept for afterwards
        e.client.call("sim.run", params("fileId", fileId, "on", false, "hz", 16));
        assertEquals(1024.0, ticker());
        assertEquals(16.0, reported());
        // Run (ticks on) is refused while Run Until steps one cycle at a time
        Client.Failure busy = e.client.fail("sim.run", params("fileId", fileId, "on", true));
        assertEquals(4, busy.code);
        assertEquals("busy", busy.reason());
        assertTrue(e.client.callObject("record.stop", params("fileId", fileId)).get("stopped").getAsBoolean());
        assertEquals("stopped", awaitDone(mark).get("result").getAsString());
        assertEquals(16.0, ticker(), "stopped: the speed chosen meanwhile");
        assertEquals(16.0, reported());
        e.client.call("sim.run", params("fileId", fileId, "on", true, "hz", 16)); // Run works again
        e.client.call("sim.run", params("fileId", fileId, "on", false));
    }

    @Test
    void metAndLimitGiveTheSpeedBack() throws Exception {
        int mark = e.client.mark();
        e.client.call("record.runUntil", params("fileId", fileId, "kind", "errorOrX", "maxCycles", 3));
        assertEquals("limit", awaitDone(mark).get("result").getAsString());
        assertEquals(4.0, ticker(), "limit");
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        String counter = Fixtures.byName(snap.getAsJsonArray("components"), "Counter").get(0).get("id").getAsString();
        String q = Fixtures.netOf(snap.getAsJsonArray("nets"), counter, 0);
        JsonObject row = e.client.callObject("record.addRow", params("fileId", fileId, "circuitId", main,
                "netId", q));
        int m2 = e.client.mark();
        e.client.call("record.runUntil", params("fileId", fileId, "kind", "row", "value", row.get("id").getAsString()));
        assertEquals("met", awaitDone(m2).get("result").getAsString());
        assertEquals(4.0, ticker(), "met");
        assertEquals(4.0, reported());
    }

    @Test
    void theSimulationTurnedOffGivesTheSpeedBack() throws Exception {
        int mark = e.client.mark();
        startLong();
        e.client.call("sim.enable", params("fileId", fileId, "on", false));
        assertEquals("off", awaitDone(mark).get("result").getAsString());
        assertEquals(4.0, ticker());
        assertEquals(4.0, reported());
    }

    @Test
    void closingTheFileGivesTheSpeedBack() throws Exception {
        Simulator s = simulator();
        startLong();
        assertEquals(1024.0, e.onEngine(s::getTickFrequency));
        e.client.call("file.close", params("fileId", fileId));
        assertEquals(4.0, e.onEngine(s::getTickFrequency), "the file's simulator is back at the student's speed");
    }
}
