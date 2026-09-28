/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static kr.ac.hallym.hcs.engine.Client.xy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.sim.CyclePacer;

/** sim.*: 사이클, 값 스트림, Poke, Reset, Run, 발진, 서브회로 보기. */
class SimTest {
    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    JsonObject open(File f) {
        JsonObject r = e.client.callObject("file.open", params("path", f.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        return r;
    }

    JsonObject snapshot() {
        return e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
    }

    /** from 이후 받은 sim.values를 차례로 겹친 넷 값(보고 있는 회로 circuitId). */
    Map<String, String> values(int from, String circuitId) {
        Map<String, String> ret = new HashMap<>();
        for (JsonObject v : e.client.notificationsAfter(from, "sim.values")) {
            if (v.get("fileId").getAsString().equals(fileId) && v.get("circuitId").getAsString().equals(circuitId)) {
                for (Map.Entry<String, JsonElement> n : v.getAsJsonObject("nets").entrySet()) {
                    ret.put(n.getKey(), n.getValue().getAsString());
                }
            }
        }
        return ret;
    }

    /** 넷 값이 want가 될 때까지 기다린다. */
    void awaitValue(int from, String circuitId, String net, String want) throws InterruptedException {
        long end = System.currentTimeMillis() + Client.TIMEOUT_MS;
        while (!want.equals(values(from, circuitId).get(net))) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError(net + " never became " + want + ": " + values(from, circuitId));
            }
            Thread.sleep(10);
        }
    }

    JsonObject awaitCycle(int from, long cycle) {
        return e.client.awaitNotificationAfter(from, "sim.state",
                s -> s.get("fileId").getAsString().equals(fileId) && s.get("cycle").getAsLong() == cycle);
    }

    @Test
    void cyclesTickACounterAndStreamTheValue() throws Exception {
        open(Fixtures.counter(tmp));
        JsonObject s = snapshot();
        String counter = Fixtures.byName(s.getAsJsonArray("components"), "Counter").get(0).get("id").getAsString();
        String q = Fixtures.netOf(s.getAsJsonArray("nets"), counter, 0);
        assertNotNull(q);
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        awaitValue(mark, main, q, "00000000");

        assertEquals(new JsonObject(), e.client.call("sim.cycles", params("fileId", fileId, "n", 5)));
        JsonObject done = awaitCycle(mark, 5);
        assertTrue(done.get("running").getAsBoolean());
        assertEquals("00000101", values(mark, main).get(q), "values are flushed before the state that ends the run");

        e.client.call("sim.cycles", params("fileId", fileId, "n", 50));
        awaitCycle(mark, 55);
        assertEquals("00110111", values(mark, main).get(q), "no tick is lost: 55 cycles");
        int maxPending = e.onEngine(() -> e.engine.sim(fileId).maxPendingTicks());
        assertTrue(maxPending <= CyclePacer.MAX_PENDING, "never more than 8 pending ticks (D-123): " + maxPending);
        assertEquals(CyclePacer.MAX_PENDING, maxPending, "the pacer fills up to the limit");

        int m2 = e.client.mark();
        e.client.call("sim.reset", params("fileId", fileId));
        awaitCycle(m2, 0);
        awaitValue(m2, main, q, "00000000");
        assertEquals(-32602, e.client.fail("sim.cycles", params("fileId", fileId, "n", 0)).code);
    }

    @Test
    void resetDuringCyclesWaitsForTheTicksInFlight() throws Exception {
        open(Fixtures.counter(tmp));
        JsonObject s = snapshot();
        String counter = Fixtures.byName(s.getAsJsonArray("components"), "Counter").get(0).get("id").getAsString();
        String q = Fixtures.netOf(s.getAsJsonArray("nets"), counter, 0);
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        e.client.call("sim.cycles", params("fileId", fileId, "n", 1000));
        e.client.awaitNotification("sim.values", v -> v.getAsJsonObject("nets").has(q)
                && !v.getAsJsonObject("nets").get(q).getAsString().equals("00000000"));
        int mark = e.client.mark();
        e.client.call("sim.reset", params("fileId", fileId));
        awaitCycle(mark, 0);
        awaitValue(mark, main, q, "00000000");
        Thread.sleep(400);
        boolean reset = false;
        for (JsonObject st : e.client.notificationsAfter(mark, "sim.state")) {
            reset |= st.get("cycle").getAsLong() == 0; // 앞의 것은 처리 중이던 틱(8개 이하)이 끝나는 동안
            if (reset) {
                assertEquals(0, st.get("cycle").getAsLong(), "no tick runs after the reset: " + st);
            }
        }
        assertEquals("00000000", values(mark, main).get(q));
        assertEquals(0, (int) e.onEngine(() -> e.engine.sim(fileId).pendingTicks()));
    }

    @Test
    void onlyChangedNetsAreSentAndBatched() throws Exception {
        open(Fixtures.counter(tmp));
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        int nets = snapshot().getAsJsonArray("nets").size();
        JsonObject first = e.client.awaitNotificationAfter(mark, "sim.values", v -> true);
        assertEquals(nets, first.getAsJsonObject("nets").size(), "a new watch sends every net once");
        long t0 = System.nanoTime();
        int m2 = e.client.mark();
        e.client.call("sim.cycles", params("fileId", fileId, "n", 20));
        awaitCycle(mark, 20);
        long frames = (System.nanoTime() - t0) / 1_000_000 / 16 + 2;
        for (JsonObject v : e.client.notificationsAfter(mark, "sim.values")) {
            if (v != first) {
                assertTrue(v.getAsJsonObject("nets").size() < nets, "later batches carry only changed nets");
            }
        }
        int batches = e.client.notificationsAfter(m2, "sim.values").size();
        assertTrue(batches <= frames && batches <= 40, "values are batched per frame (" + batches + " batches, "
                + frames + " frames, 40 ticks)");
    }

    @Test
    void pokeTogglesAnInputPin() throws Exception {
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        String in = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "Wiring", "name", "Pin", "loc", xy(100, 100), "attrs", params("tristate", "false"))).get("id")
                .getAsString();
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", "Wiring", "name", "Pin",
                "loc", xy(200, 100), "attrs", params("output", "true", "facing", "west")));
        e.client.call("edit.addWire", params("fileId", fileId, "circuitId", main, "points",
                new Object[] {xy(100, 100), xy(200, 100)}));
        String net = Fixtures.netOf(snapshot().getAsJsonArray("nets"), in, 0);
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        awaitValue(mark, main, net, "0");
        assertTrue(e.client.callObject("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", in))
                .get("poked").getAsBoolean());
        awaitValue(mark, main, net, "1");
        e.client.call("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", in));
        awaitValue(mark, main, net, "0");
        String w = snapshot().getAsJsonArray("wires").get(0).getAsJsonObject().get("id").getAsString();
        assertFalse(e.client.callObject("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", w))
                .get("poked").getAsBoolean(), "a wire has nothing to poke");
        assertEquals(-32602, e.client.fail("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", in,
                "action", "wiggle")).code);
    }

    @Test
    void aButtonIsHighWhilePressed() throws Exception {
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        String button = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "I/O", "name", "Button", "loc", xy(100, 100))).get("id").getAsString();
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", "Wiring", "name", "Pin",
                "loc", xy(200, 100), "attrs", params("output", "true", "facing", "west")));
        e.client.call("edit.addWire", params("fileId", fileId, "circuitId", main, "points",
                new Object[] {xy(100, 100), xy(200, 100)}));
        String net = Fixtures.netOf(snapshot().getAsJsonArray("nets"), button, 0);
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        awaitValue(mark, main, net, "0");
        e.client.call("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", button, "action",
                "press"));
        awaitValue(mark, main, net, "1");
        e.client.call("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", button, "action",
                "release"));
        awaitValue(mark, main, net, "0");
    }

    @Test
    void runTicksTheClockAtTheGivenRate() throws Exception {
        open(Fixtures.counter(tmp));
        int mark = e.client.mark();
        e.client.call("sim.run", params("fileId", fileId, "on", true, "hz", 256));
        e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("ticking").getAsBoolean()
                && s.get("hz").getAsDouble() == 256.0);
        e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("cycle").getAsLong() >= 3);
        e.client.call("sim.run", params("fileId", fileId, "on", false));
        e.client.awaitNotificationAfter(mark, "sim.state", s -> !s.get("ticking").getAsBoolean());
        JsonObject st = e.client.callObject("sim.state", params("fileId", fileId));
        assertFalse(st.get("ticking").getAsBoolean());
        assertEquals(-32602, e.client.fail("sim.run", params("fileId", fileId, "on", true, "hz", -1)).code);
    }

    @Test
    void oscillationStopsTheSimulationAndRefusesCycles() throws Exception {
        open(new File(Fixtures.CIRC_DIR, "faults/dynamic-oscillation.circ"));
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        // 클럭이 1이 되면 NAND 고리가 발진한다
        e.client.call("sim.cycles", params("fileId", fileId, "n", 3));
        e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("oscillating").getAsBoolean()
                && !s.get("running").getAsBoolean());
        e.client.awaitNotificationAfter(mark, "engine.log", l -> l.get("message").getAsString()
                .contains("oscillation"));
        Client.Failure f = e.client.fail("sim.cycles", params("fileId", fileId, "n", 1));
        assertEquals(4, f.code);
        assertEquals("oscillating", f.reason());
        assertEquals(4, e.client.fail("sim.run", params("fileId", fileId, "on", true)).code);
        int m2 = e.client.mark();
        e.client.call("sim.enable", params("fileId", fileId, "on", true));
        e.client.awaitNotificationAfter(m2, "sim.state", s -> s.get("fileId").getAsString().equals(fileId));
    }

    @Test
    void watchingASubcircuitInstanceSendsItsNets() throws Exception {
        JsonObject opened = open(new File(Fixtures.CIRC_DIR, "subcircuit.circ"));
        JsonObject s = snapshot();
        JsonObject inst = null;
        for (JsonElement c : s.getAsJsonArray("components")) {
            if (c.getAsJsonObject().has("subcircuit")) {
                inst = c.getAsJsonObject();
                break;
            }
        }
        assertNotNull(inst);
        String sub = inst.get("subcircuit").getAsString();
        int subNets = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", sub))
                .getAsJsonArray("nets").size();
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main, "path",
                new Object[] {inst.get("id").getAsString()}));
        JsonObject v = e.client.awaitNotificationAfter(mark, "sim.values", x -> x.get("circuitId").getAsString()
                .equals(sub));
        assertEquals(main, v.get("root").getAsString());
        assertEquals(inst.get("id").getAsString(), v.getAsJsonArray("path").get(0).getAsString());
        assertEquals(subNets, v.getAsJsonObject("nets").size());
        for (Map.Entry<String, JsonElement> n : v.getAsJsonObject("nets").entrySet()) {
            assertTrue(n.getValue().getAsString().matches("[01xE]+"), n.toString());
        }
        assertEquals(1, e.client.fail("sim.watch", params("fileId", fileId, "circuitId", main, "path",
                new Object[] {"k999999"})).code);
        assertNotNull(opened);
    }

    @Test
    void anEditRenumbersNetsAndResendsAllValues() throws Exception {
        open(Fixtures.counter(tmp));
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        e.client.awaitNotificationAfter(mark, "sim.values", v -> true);
        int m2 = e.client.mark();
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", "Gates", "name",
                "OR Gate", "loc", xy(700, 700)));
        JsonObject changed = e.client.awaitNotificationAfter(m2, "model.changed", c -> true);
        int nets = changed.getAsJsonArray("nets").size();
        JsonObject v = e.client.awaitNotificationAfter(m2, "sim.values", x -> true);
        assertEquals(nets, v.getAsJsonObject("nets").size(), "after a model change every net is sent again");
        // model.changed가 값보다 먼저 온다(화면이 새 넷 번호를 먼저 안다)
        int changedAt = -1;
        int valuesAt = -1;
        int i = 0;
        for (String line : e.client.rawLines()) {
            if (i >= m2 && changedAt < 0 && line.contains("\"model.changed\"")) {
                changedAt = i;
            }
            if (i >= m2 && valuesAt < 0 && line.contains("\"sim.values\"")) {
                valuesAt = i;
            }
            i++;
        }
        assertTrue(changedAt >= 0 && changedAt < valuesAt);
    }

    // ---- N-07(D-145): 빠른 N 사이클, 클럭 멈춤, Tick Once, Step, 키로 Poke ----

    /** 카운터 회로를 열고 q 넷을 본다. q 넷 id. */
    String openCounterAndWatch(int[] markOut) throws Exception {
        open(Fixtures.counter(tmp));
        JsonObject s = snapshot();
        String counter = Fixtures.byName(s.getAsJsonArray("components"), "Counter").get(0).get("id").getAsString();
        String q = Fixtures.netOf(s.getAsJsonArray("nets"), counter, 0);
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        awaitValue(mark, main, q, "00000000");
        markOut[0] = mark;
        return q;
    }

    static String bits8(long n) {
        String b = Long.toBinaryString(n & 0xff);
        return "00000000".substring(b.length()) + b;
    }

    @Test
    void cyclesStopTheRunningClockAndCountEveryTick() throws Exception {
        int[] m = new int[1];
        String q = openCounterAndWatch(m);
        e.client.call("sim.run", params("fileId", fileId, "on", true, "hz", 64));
        e.client.awaitNotificationAfter(m[0], "sim.state", s -> s.get("cycle").getAsLong() >= 2);
        int m2 = e.client.mark();
        e.client.call("sim.cycles", params("fileId", fileId, "n", 30));
        JsonObject first = e.client.awaitNotificationAfter(m2, "sim.state", s -> true);
        assertFalse(first.get("ticking").getAsBoolean(), "N Cycles stops the clock first");
        JsonObject done = e.client.awaitNotificationAfter(m2, "sim.state", s -> s.get("cyclesLeft").getAsLong() == 0
                && !s.get("ticking").getAsBoolean() && s.get("cycle").getAsLong() >= 32);
        long cycle = done.get("cycle").getAsLong();
        // exactly its own 60 ticks after the clock's queued ones (the counter counts rising edges: (ticks + 1) / 2)
        long[] run = e.onEngine(() -> e.engine.sim(fileId).lastPacer());
        assertEquals(60, run[1], "requested");
        assertEquals(60, run[2], "counted: only its own ticks");
        assertEquals((run[0] + 60) / 2, cycle, "the cycle: where the clock stopped, then exactly 30 more");
        awaitValue(m2, main, q, bits8((run[0] + 60 + 1) / 2));
        Thread.sleep(300);
        assertEquals(cycle, e.client.callObject("sim.state", params("fileId", fileId)).get("cycle").getAsLong(),
                "nothing ticks once the cycles are done");
        assertEquals(64.0, done.get("hz").getAsDouble());
        assertEquals(64.0, (double) e.onEngine(() -> e.engine.files().get(fileId).project().getSimulator()
                .getTickFrequency()), "the clock's own speed is the student's again");
    }

    /**
     * N Cycles while the clock runs fast (the original ticker has queued ticks in the propagation manager, up to 16): those
     * queued automatic ticks are done first and not counted as N Cycles' own; then exactly 2n ticks.
     */
    @Test
    void nCyclesWhileTheClockRunsFastCountsOnlyItsOwnTicks() throws Exception {
        int[] m = new int[1];
        String q = openCounterAndWatch(m);
        e.client.call("sim.run", params("fileId", fileId, "on", true, "hz", 4096));
        e.client.awaitNotificationAfter(m[0], "sim.state", s -> s.get("cycle").getAsLong() >= 40);
        int m2 = e.client.mark();
        e.client.call("sim.cycles", params("fileId", fileId, "n", 10));
        JsonObject done = e.client.awaitNotificationAfter(m2, "sim.state", s -> s.get("cyclesLeft").getAsLong() == 0
                && !s.get("ticking").getAsBoolean() && e.client.notificationsAfter(m2, "sim.state").size() > 1);
        long[] run = e.onEngine(() -> e.engine.sim(fileId).lastPacer());
        assertEquals(20, run[1]);
        assertEquals(20, run[2]);
        long end = run[0] + 20;
        Thread.sleep(300);
        long cycle = e.client.callObject("sim.state", params("fileId", fileId)).get("cycle").getAsLong();
        assertEquals(end / 2, cycle, "exactly 10 cycles after the clock's own ticks; nothing after: " + done);
        awaitValue(m2, main, q, bits8((end + 1) / 2));
        assertEquals(4096.0, done.get("hz").getAsDouble());
    }

    /** Stop (or Reset) while N Cycles waits for the clock's queued ticks: nothing of its own is ever asked. */
    @Test
    void stopWhileWaitingForTheClocksTicksAsksNothing() throws Exception {
        int[] m = new int[1];
        openCounterAndWatch(m);
        e.client.call("sim.run", params("fileId", fileId, "on", true, "hz", 4096));
        e.client.awaitNotificationAfter(m[0], "sim.state", s -> s.get("cycle").getAsLong() >= 10);
        e.client.call("sim.cycles", params("fileId", fileId, "n", 50));
        e.client.call("sim.run", params("fileId", fileId, "on", false));   // at once: still waiting
        Thread.sleep(400);
        long[] run = e.onEngine(() -> e.engine.sim(fileId).lastPacer());
        assertEquals(0, run[1], "no tick of its own was asked");
        JsonObject st = e.client.callObject("sim.state", params("fileId", fileId));
        assertEquals(0, st.get("cyclesLeft").getAsLong());
        assertFalse(st.get("ticking").getAsBoolean());
    }

    /**
     * Keys to a poked counter while N Cycles runs: the original poker's key handlers change the circuit state on the engine
     * thread while the simulator thread propagates; they wait for the propagation as edits do (SimGate, D-143). No thread
     * dies and the simulation stays on.
     */
    @Test
    void keysToAPokedPartWhileCyclesRunKeepTheSimulationOn() throws Exception {
        java.util.List<String> died = new java.util.concurrent.CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((th, x) -> died.add(th.getName() + ": " + x));
        try {
            int[] m = new int[1];
            openCounterAndWatch(m);
            String counter = Fixtures.byName(snapshot().getAsJsonArray("components"), "Counter").get(0).get("id")
                    .getAsString();
            assertTrue(e.client.callObject("sim.poke", params("fileId", fileId, "circuitId", main, "componentId",
                    counter)).get("caret").getAsBoolean());
            int m2 = e.client.mark();
            e.client.call("sim.cycles", params("fileId", fileId, "n", 5000));
            for (int k = 0; k < 100; k++) {
                e.client.call("sim.pokeKey", params("fileId", fileId, "key", Integer.toHexString(k % 16)));
            }
            e.client.call("sim.pokeStop", params("fileId", fileId));
            e.client.call("sim.run", params("fileId", fileId, "on", false));
            e.client.awaitNotificationAfter(m2, "sim.state", s -> s.get("cyclesLeft").getAsLong() == 0);
            JsonObject st = e.client.callObject("sim.state", params("fileId", fileId));
            assertTrue(st.get("running").getAsBoolean(), "the simulation is still on (no propagation error): " + st);
            assertTrue(st.get("cycle").getAsLong() > 0);
            assertEquals(java.util.List.of(), died);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before);
        }
    }

    @Test
    void stopEndsLongCyclesAfterTheTicksInFlight() throws Exception {
        int[] m = new int[1];
        String q = openCounterAndWatch(m);
        int m2 = e.client.mark();
        e.client.call("sim.cycles", params("fileId", fileId, "n", 100000));
        JsonObject going = e.client.awaitNotificationAfter(m2, "sim.state", s -> s.get("cyclesLeft").getAsLong() > 0);
        assertTrue(going.get("cyclesLeft").getAsLong() <= 100000);
        e.client.awaitNotificationAfter(m2, "sim.state", s -> s.get("cycle").getAsLong() >= 5);
        e.client.call("sim.run", params("fileId", fileId, "on", false));
        JsonObject stopped = e.client.awaitNotificationAfter(m2, "sim.state",
                s -> s.get("cyclesLeft").getAsLong() == 0);
        long cycle = stopped.get("cycle").getAsLong();
        assertTrue(cycle < 100000);
        awaitValue(m2, main, q, bits8(cycle));
        Thread.sleep(300);
        assertEquals(cycle, e.client.callObject("sim.state", params("fileId", fileId)).get("cycle").getAsLong());
        assertEquals(1.0, (double) e.onEngine(() -> e.engine.files().get(fileId).project().getSimulator()
                .getTickFrequency()));
    }

    @Test
    void tickOnceIsHalfACycle() throws Exception {
        int[] m = new int[1];
        String q = openCounterAndWatch(m);
        int m2 = e.client.mark();
        e.client.call("sim.tick", params("fileId", fileId));
        Thread.sleep(200);
        assertEquals(0, e.client.callObject("sim.state", params("fileId", fileId)).get("cycle").getAsLong());
        e.client.call("sim.tick", params("fileId", fileId));
        awaitCycle(m2, 1);
        awaitValue(m2, main, q, "00000001");
    }

    @Test
    void stepSimulationOnlyWhileOff() throws Exception {
        open(Fixtures.counter(tmp));
        Client.Failure f = e.client.fail("sim.step", params("fileId", fileId));
        assertEquals(4, f.code);
        assertEquals("running", f.reason());
        e.client.call("sim.enable", params("fileId", fileId, "on", false));
        assertEquals(new JsonObject(), e.client.call("sim.step", params("fileId", fileId)));
    }

    @Test
    void keysGoToThePokedRegister() throws Exception {
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        String reg = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "Memory", "name", "Register", "loc", xy(200, 200))).get("id").getAsString();
        JsonObject regJson = Fixtures.byId(snapshot().getAsJsonArray("components"), reg);
        String qNet = Fixtures.netOf(snapshot().getAsJsonArray("nets"), reg, 0);
        assertNotNull(regJson);
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        awaitValue(mark, main, qNet, "00000000");
        assertFalse(e.client.callObject("sim.pokeKey", params("fileId", fileId, "key", "7")).get("poked")
                .getAsBoolean(), "nothing poked yet: no caret");
        JsonObject poked = e.client.callObject("sim.poke", params("fileId", fileId, "circuitId", main,
                "componentId", reg));
        assertTrue(poked.get("caret").getAsBoolean());
        assertTrue(e.client.callObject("sim.pokeKey", params("fileId", fileId, "key", "a")).get("poked")
                .getAsBoolean());
        awaitValue(mark, main, qNet, "00001010");
        e.client.call("sim.pokeKey", params("fileId", fileId, "key", "5"));
        awaitValue(mark, main, qNet, "10100101");
        e.client.call("sim.pokeKey", params("fileId", fileId, "key", "z")); // 16진 글자가 아니면 그대로
        e.client.call("sim.pokeKey", params("fileId", fileId, "key", "ArrowLeft"));
        e.client.call("sim.pokeStop", params("fileId", fileId));
        assertFalse(e.client.callObject("sim.pokeKey", params("fileId", fileId, "key", "1")).get("poked")
                .getAsBoolean());
        assertEquals("10100101", values(mark, main).get(qNet));
        assertEquals(-32602, e.client.fail("sim.pokeKey", params("fileId", fileId, "key", "Hyper")).code);
    }

    @Test
    void fastTicksAreCountedPerHolder() throws Exception {
        open(Fixtures.counter(tmp));
        double[] hz = e.onEngine(() -> {
            kr.ac.hallym.hcs.engine.sim.SimSession s = e.engine.sim(fileId);
            com.cburch.logisim.circuit.Simulator sim = e.engine.files().get(fileId).project().getSimulator();
            double[] out = new double[4];
            s.fastTicks(true);
            s.fastTicks(true);
            out[0] = sim.getTickFrequency();
            s.fastTicks(false);
            out[1] = sim.getTickFrequency();
            out[2] = s.state().get("hz").getAsDouble();
            s.fastTicks(false);
            out[3] = sim.getTickFrequency();
            s.fastTicks(false); // 한 번 더 놓아도 그대로
            return out;
        });
        assertEquals(1024.0, hz[0]);
        assertEquals(1024.0, hz[1], "Run Until and N Cycles can hold it at once");
        assertEquals(1.0, hz[2], "sim.state tells the student's speed meanwhile");
        assertEquals(1.0, hz[3]);
    }

    @Test
    void valueText() {
        assertEquals("x", kr.ac.hallym.hcs.engine.sim.SimSessionTestAccess.text(null, 1));
        assertEquals("01x1", kr.ac.hallym.hcs.engine.sim.SimSessionTestAccess.text(
                com.cburch.logisim.data.Value.create(new com.cburch.logisim.data.Value[] {
                    com.cburch.logisim.data.Value.TRUE, com.cburch.logisim.data.Value.UNKNOWN,
                    com.cburch.logisim.data.Value.TRUE, com.cburch.logisim.data.Value.FALSE}), 4));
        assertEquals("E", kr.ac.hallym.hcs.engine.sim.SimSessionTestAccess.text(com.cburch.logisim.data.Value.ERROR, 1));
        assertEquals("xx1", kr.ac.hallym.hcs.engine.sim.SimSessionTestAccess.text(com.cburch.logisim.data.Value.TRUE, 3),
                "missing high bits are unknown");
    }
}
