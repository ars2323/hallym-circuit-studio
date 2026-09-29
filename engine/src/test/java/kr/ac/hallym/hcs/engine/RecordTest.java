/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.mips.disasm.Disassembler;
import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.HmxParser;

/**
 * record.*(N-14, D-144): 사이클 표, 지난 사이클, Run Until, Registers·Memory·Instruction, Mark as PC·Register File·
 * Register Mapping. v1의 기록 엔진(Recorder·Recording)을 엔진에서 돌린다.
 */
class RecordTest {
    static final File TESTS = Fixtures.REF_MIPS.getParentFile().getParentFile();
    static final Path GOLDENS = TESTS.toPath().resolve("hmx/hallym-mips-v2.4.0");
    static final File DATAPATH = new File(Fixtures.CIRC_DIR, "demo-datapath.circ");

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

    JsonObject call(String method, Object... kv) {
        Object[] all = new Object[kv.length + 2];
        all[0] = "fileId";
        all[1] = fileId;
        System.arraycopy(kv, 0, all, 2, kv.length);
        return e.client.callObject(method, params(all));
    }

    void cycles(int n) {
        long want = call("sim.state").get("cycle").getAsLong() + n;
        int mark = e.client.mark();
        call("sim.cycles", "n", n);
        e.client.awaitNotificationAfter(mark, "sim.state",
                s -> s.get("fileId").getAsString().equals(fileId) && s.get("cycle").getAsLong() == want);
    }

    /** Reset, and wait until the new recording has its step 0 (a new generation). */
    void reset() throws InterruptedException {
        int before = call("record.state").get("generation").getAsInt();
        int mark = e.client.mark();
        call("sim.reset");
        e.client.awaitNotificationAfter(mark, "sim.state",
                s -> s.get("fileId").getAsString().equals(fileId) && s.get("cycle").getAsLong() == 0);
        long end = System.currentTimeMillis() + Client.TIMEOUT_MS;
        while (call("record.state").get("generation").getAsInt() <= before) {
            assertTrue(System.currentTimeMillis() < end, "the reset never started a new recording");
            Thread.sleep(5);
        }
    }

    /** 기록이 last 사이클까지 적었을 때까지(record.state). */
    JsonObject awaitRecorded(int last) throws InterruptedException {
        long end = System.currentTimeMillis() + Client.TIMEOUT_MS;
        while (true) {
            JsonObject s = call("record.state");
            if (!s.get("empty").getAsBoolean() && s.get("last").getAsInt() == last) {
                return s;
            }
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("never recorded cycle " + last + ": " + s);
            }
            Thread.sleep(10);
        }
    }

    JsonObject runUntil(Object... kv) {
        int mark = e.client.mark();
        Object[] all = new Object[kv.length + 2];
        all[0] = "fileId";
        all[1] = fileId;
        System.arraycopy(kv, 0, all, 2, kv.length);
        e.client.call("record.runUntil", params(all));
        // A run to exit is a few hundred cycles of ref-mips: in the constant identity hash JVM (D-129, every
        // HashMap of components one bucket) that is slower than the client's usual 30 s, so wait longer here.
        long end = System.currentTimeMillis() + 240_000;
        while (true) {
            for (JsonObject n : e.client.notificationsAfter(mark, "record.runUntil")) {
                if (n.get("fileId").getAsString().equals(fileId)) {
                    return n;
                }
            }
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("Run Until never ended: " + call("record.state"));
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        }
    }

    String counterNet() {
        JsonObject s = snapshot();
        String counter = Fixtures.byName(s.getAsJsonArray("components"), "Counter").get(0).get("id").getAsString();
        return Fixtures.netOf(s.getAsJsonArray("nets"), counter, 0);
    }

    static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement x : a) {
            out.add(x.isJsonNull() ? null : x.getAsString());
        }
        return out;
    }

    static String bits8(int v) {
        String s = Integer.toBinaryString(v & 0xff);
        return "00000000".substring(s.length()) + s;
    }

    // ---- 사이클 표(C-02) ----

    @Test
    void theTableHoldsEveryCycleOfACounter() throws Exception {
        open(Fixtures.counter(tmp));
        JsonObject row = call("record.addRow", "circuitId", main, "netId", counterNet());
        assertTrue(row.get("added").getAsBoolean());
        assertEquals("q", row.get("name").getAsString(), "the net's name: the output pin's label");
        assertEquals(8, row.get("width").getAsInt());
        assertFalse(call("record.addRow", "circuitId", main, "netId", counterNet()).get("added").getAsBoolean(),
                "the same net once");
        // 1비트 줄: 클럭(반 사이클 파형)
        JsonObject s = snapshot();
        String clock = Fixtures.byName(s.getAsJsonArray("components"), "Clock").get(0).get("id").getAsString();
        JsonObject clk = call("record.addRow", "circuitId", main, "netId", Fixtures.netOf(s.getAsJsonArray("nets"),
                clock, 0));
        cycles(12);
        awaitRecorded(12);
        JsonObject t = call("record.table", "from", 0, "to", 12);
        assertFalse(t.get("empty").getAsBoolean());
        assertEquals(0, t.get("first").getAsInt());
        assertEquals(12, t.get("last").getAsInt());
        assertEquals(12, t.get("cycle").getAsInt());
        assertFalse(t.get("cpu").getAsBoolean(), "no Instruction Memory");
        JsonArray cols = t.getAsJsonArray("columns");
        assertEquals(13, cols.size());
        for (int c = 0; c <= 12; c++) {
            JsonObject col = cols.get(c).getAsJsonObject();
            assertEquals(c, col.get("cycle").getAsInt());
            assertTrue(col.get("pc").isJsonNull());
            assertEquals("", col.get("text").getAsString());
        }
        JsonObject q = t.getAsJsonArray("rows").get(0).getAsJsonObject();
        List<String> want = new ArrayList<>();
        for (int c = 0; c <= 12; c++) {
            want.add(bits8(c));
        }
        assertEquals(want, strings(q.getAsJsonArray("values")), "column c = the counter after c rising edges");
        assertFalse(q.has("halves"), "a bus has no half-cycle wave");
        JsonObject k = t.getAsJsonArray("rows").get(1).getAsJsonObject();
        assertEquals(clk.get("id").getAsString(), k.get("id").getAsString());
        List<String> halves = strings(k.getAsJsonArray("halves"));
        List<String> values = strings(k.getAsJsonArray("values"));
        for (int c = 1; c <= 12; c++) {
            assertEquals("1", halves.get(c), "the first half of cycle " + c + ": after its rising edge");
            assertEquals("0", values.get(c), "the second half of cycle " + c + ": before the next rising edge");
        }
        // 기본 열: 마지막 60열까지
        JsonObject last = call("record.table");
        assertEquals(0, last.get("from").getAsInt());
        assertEquals(12, last.get("to").getAsInt());
        assertTrue(call("record.removeRow", "id", clk.get("id").getAsString()).get("removed").getAsBoolean());
        assertEquals(1, call("record.table").getAsJsonArray("rows").size());
        assertTrue(call("record.rowBits", "id", row.get("id").getAsString(), "bits", true).get("changed").getAsBoolean());
        assertTrue(call("record.table").getAsJsonArray("rows").get(0).getAsJsonObject().get("bits").getAsBoolean());
    }

    /** 느린 엔진과 바쁜 CPU에서도 N Cycles의 모든 사이클이 빈틈없이 기록된다(D-123 TickLoadTest와 같은 부하). */
    @Test
    void theRecordingKeepsEveryCycleUnderLoad() throws Exception {
        open(Fixtures.counter(tmp));
        String q = counterNet();
        call("record.addRow", "circuitId", main, "netId", q);
        SimulatorListener slow = new SimulatorListener() {
            public void propagationCompleted(SimulatorEvent ev) {
            }

            public void tickCompleted(SimulatorEvent ev) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            public void simulatorStateChanged(SimulatorEvent ev) {
            }
        };
        e.onEngine(() -> {
            e.engine.files().get(fileId).project().getSimulator().addSimulatorListener(slow);
            return null;
        });
        AtomicBoolean on = new AtomicBoolean(true);
        List<Thread> burners = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread b = new Thread(() -> {
                long x = 0;
                while (on.get()) {
                    x += System.nanoTime() % 7;
                }
                assertTrue(x >= 0);
            });
            b.setDaemon(true);
            b.start();
            burners.add(b);
        }
        try {
            cycles(300);
        } finally {
            on.set(false);
            for (Thread b : burners) {
                b.join();
            }
        }
        JsonObject st = awaitRecorded(300);
        assertEquals(300, st.get("cycle").getAsInt());
        int[] steps = e.onEngine(() -> {
            Recording r = e.engine.record(fileId).recording();
            return new int[] {r.first(), r.last()};
        });
        assertEquals(0, steps[0]);
        assertEquals(600, steps[1], "one step per tick the engine ran");
        JsonObject t = call("record.table", "from", 0, "to", 300);
        List<String> got = strings(t.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonArray("values"));
        for (int c = 0; c <= 300; c++) {
            assertEquals(bits8(c), got.get(c), "cycle " + c);
        }
    }

    // ---- 지난 사이클(C-03) ----

    @Test
    void aPastCycleIsShownAndTheRunGoesOnFromThere() throws Exception {
        open(Fixtures.counter(tmp));
        String q = counterNet();
        call("sim.watch", "circuitId", main);
        cycles(20);
        awaitRecorded(20);
        assertEquals(bits8(3), call("record.values", "cycle", 3, "circuitId", main).getAsJsonObject("nets")
                .get(q).getAsString(), "a past value from the recording, without running again");
        int mark = e.client.mark();
        JsonObject v = call("record.view", "cycle", 7);
        assertEquals(7, v.get("cycle").getAsInt());
        assertTrue(v.get("past").getAsBoolean());
        JsonObject st = call("record.state");
        assertEquals(7, st.get("cycle").getAsInt());
        assertEquals(20, st.get("last").getAsInt());
        assertTrue(st.get("past").getAsBoolean());
        assertEquals(7, call("sim.state").get("cycle").getAsLong(), "the clock's count is the shown cycle's");
        e.client.awaitNotificationAfter(mark, "sim.values", n -> n.getAsJsonObject("nets").has(q)
                && n.getAsJsonObject("nets").get(q).getAsString().equals(bits8(7)));
        // 지금으로 돌아오기
        JsonObject back = call("record.view", "latest", true);
        assertEquals(20, back.get("cycle").getAsInt());
        assertFalse(back.get("past").getAsBoolean());
        assertEquals(20, call("sim.state").get("cycle").getAsLong());
        // 지난 사이클에서 진행하면 뒤 기록을 버리고 거기서 이어 적는다
        call("record.view", "cycle", 7);
        cycles(2);
        JsonObject after = awaitRecorded(9);
        assertEquals(9, after.get("cycle").getAsInt());
        assertFalse(after.get("past").getAsBoolean());
        assertEquals(bits8(9), call("record.values", "cycle", 9, "circuitId", main).getAsJsonObject("nets").get(q)
                .getAsString());
        // N Cycles가 도는 동안은 볼 수 없다
        call("sim.cycles", "n", 2000);
        Client.Failure busy = e.client.fail("record.view", params("fileId", fileId, "cycle", 1));
        assertEquals(4, busy.code);
        assertEquals("busy", busy.reason());
        reset();
    }

    @Test
    void resetStartsTheRecordingAgainAndTakesThePinnedRowsAway() throws Exception {
        open(Fixtures.counter(tmp));
        JsonObject s = snapshot();
        String counter = Fixtures.byName(s.getAsJsonArray("components"), "Counter").get(0).get("id").getAsString();
        JsonArray loc = Fixtures.byId(s.getAsJsonArray("components"), counter).getAsJsonArray("ports").get(0)
                .getAsJsonObject().getAsJsonArray("loc");
        cycles(5);
        JsonObject before = awaitRecorded(5);
        Map<String, Object> spot = new HashMap<>();
        spot.put("circuitId", main);
        spot.put("at", new int[] {loc.get(0).getAsInt(), loc.get(1).getAsInt()});
        Map<String, Object> nowhere = new HashMap<>();
        nowhere.put("circuitId", main);
        nowhere.put("at", new int[] {-990, -990});
        JsonObject pin = call("record.pin", "cycle", 3, "rows", List.of(spot, nowhere, spot));
        assertEquals(1, pin.getAsJsonArray("ids").size(), "a place with no net is left out, the same net once");
        assertEquals(3, pin.getAsJsonObject("view").get("cycle").getAsInt(), "the message's cycle is shown");
        JsonObject t = call("record.table");
        assertEquals(3, t.get("pinnedCycle").getAsInt());
        JsonObject pinned = t.getAsJsonArray("rows").get(0).getAsJsonObject();
        assertTrue(pinned.get("temp").getAsBoolean());
        assertEquals(bits8(3), strings(pinned.getAsJsonArray("values")).get(3));
        call("record.view", "latest", true);
        reset();
        JsonObject st = awaitRecorded(0);
        assertTrue(st.get("generation").getAsInt() > before.get("generation").getAsInt(), "a new recording");
        long end = System.currentTimeMillis() + Client.TIMEOUT_MS;
        while (call("record.state").get("pinned").getAsInt() != 0) {
            assertTrue(System.currentTimeMillis() < end, "the pinned rows go with the recording (D-114)");
            Thread.sleep(10);
        }
    }

    // ---- MIPS: 실행 이미지를 ref-mips에 ----

    static ExecutableImage image(String name) throws Exception {
        HmxParser.Result r = HmxParser.read(GOLDENS.resolve(name + ".hmx").toFile());
        assertEquals(List.of(), r.errors, name);
        return r.image;
    }

    /** ref-mips를 열고 이미지를 불러오기와 같은 모양으로 넣는다(.text → Instruction Memory, .data와 reg $sp → Data Memory). */
    void refMipsWith(ExecutableImage img, String hmxName) throws Exception {
        refMipsWith(img, hmxName, GOLDENS.resolve(hmxName + ".hmx"));
    }

    void refMipsWith(ExecutableImage img, String hmxName, Path hmx) throws Exception {
        // .hmx를 .circ 옆에 두고 source 속성이 가리키게 한다(기호: 디스어셈블의 [main], Memory의 라벨)
        File dir = tmp.resolve(hmxName).toFile();
        dir.mkdirs();
        File circ = new File(dir, "ref-mips.circ");
        Files.copy(Fixtures.REF_MIPS.toPath(), circ.toPath());
        Files.copy(hmx, dir.toPath().resolve(hmxName + ".hmx"));
        open(circ);
        JsonArray comps = snapshot().getAsJsonArray("components");
        JsonObject imem = Fixtures.byName(comps, "Instruction Memory").get(0);
        JsonObject dm = Fixtures.byName(comps, "Data Memory").get(0);
        setContents(imem, img.textWords(), null);
        Long sp = img.reg("$sp");
        setContents(dm, img.dataWords(), sp);
        for (JsonObject c : List.of(imem, dm)) {
            call("edit.setAttr", "circuitId", main, "ids", List.of(c.get("id").getAsString()), "attr", "source",
                    "value", hmxName + ".hmx");
        }
        reset();
        awaitRecorded(0);
    }

    void setContents(JsonObject comp, Map<Long, Integer> words, Long depthBase) {
        StringBuilder sb = new StringBuilder("hcs-words 1\n");
        if (depthBase != null) {
            sb.append(String.format("%08x%n", depthBase));
        }
        for (Map.Entry<Long, Integer> w : words.entrySet()) {
            sb.append(String.format("%08x %08x%n", w.getKey(), w.getValue() & 0xffffffffL));
        }
        call("edit.setAttr", "circuitId", main, "ids", List.of(comp.get("id").getAsString()), "attr", "contents",
                "value", sb.toString());
    }

    @Test
    void runUntilStopsAtTheCycleOfItsCondition() throws Exception {
        ExecutableImage img = image("data");
        refMipsWith(img, "data");
        long next = img.symbols().get("next");
        // PC가 라벨 next가 되는 첫 사이클
        JsonObject done = runUntil("kind", "pc", "value", "next");
        assertEquals("met", done.get("result").getAsString());
        int c = done.get("cycle").getAsInt();
        JsonObject t = call("record.table", "from", 0, "to", c);
        JsonArray cols = t.getAsJsonArray("columns");
        for (int i = 1; i < c; i++) {
            assertFalse(String.format("0x%08x", next).equals(cols.get(i).getAsJsonObject().get("pc").getAsString()),
                    "not met before cycle " + c);
        }
        JsonObject hit = cols.get(c).getAsJsonObject();
        assertEquals(String.format("0x%08x", next), hit.get("pc").getAsString());
        assertEquals("lw $9, 0($16)", hit.get("text").getAsString(), "the Java disassembler's text");
        assertEquals(c, call("record.state").get("cycle").getAsInt());
        // 이미 조건인 곳에서 누르면 한 번은 나아간다(D-075): 반복문을 한 바퀴 돈 뒤의 next
        JsonObject again = runUntil("kind", "pc", "value", String.format("0x%08x", next));
        assertEquals("met", again.get("result").getAsString());
        assertTrue(again.get("cycle").getAsInt() > c);
        // 명령어 이름: 디스어셈블러의 이름
        JsonObject sys = runUntil("kind", "instruction", "value", "SYSCALL");
        assertEquals("met", sys.get("result").getAsString());
        JsonObject col = call("record.table", "from", sys.get("cycle").getAsInt(), "to", sys.get("cycle").getAsInt())
                .getAsJsonArray("columns").get(0).getAsJsonObject();
        assertEquals("syscall", col.get("text").getAsString());
        // halt·exit: Console의 Exit
        JsonObject halt = runUntil("kind", "halt");
        assertEquals("met", halt.get("result").getAsString());
        // 최대 사이클 수
        JsonObject limit = runUntil("kind", "pc", "value", "0x00000004", "maxCycles", 5);
        assertEquals("limit", limit.get("result").getAsString());
        assertEquals(halt.get("cycle").getAsInt() + 5, limit.get("cycle").getAsInt());
        // 읽을 수 없는 조건
        Client.Failure bad = e.client.fail("record.runUntil", params("fileId", fileId, "kind", "pc", "value", "nope"));
        assertEquals(-32602, bad.code);
        assertEquals("badPc", bad.reason());
        assertEquals("noRow", e.client.fail("record.runUntil", params("fileId", fileId, "kind", "row", "value", "r99"))
                .reason());
    }

    /** 부하에서도 PC 조건으로 멈춘 사이클이 같다(D-123: 한 사이클씩, 기록한 뒤 다음을 요청). */
    @Test
    void runUntilStopsAtTheSameCycleUnderLoad() throws Exception {
        ExecutableImage img = image("branches");
        refMipsWith(img, "branches");
        JsonObject quiet = runUntil("kind", "pc", "value", "done");
        assertEquals("met", quiet.get("result").getAsString());
        reset();
        awaitRecorded(0);
        SimulatorListener slow = new SimulatorListener() {
            public void propagationCompleted(SimulatorEvent ev) {
            }

            public void tickCompleted(SimulatorEvent ev) {
                try {
                    Thread.sleep(3);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            public void simulatorStateChanged(SimulatorEvent ev) {
            }
        };
        e.onEngine(() -> {
            e.engine.files().get(fileId).project().getSimulator().addSimulatorListener(slow);
            return null;
        });
        AtomicBoolean on = new AtomicBoolean(true);
        List<Thread> burners = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread b = new Thread(() -> {
                long x = 0;
                while (on.get()) {
                    x += System.nanoTime() % 7;
                }
                assertTrue(x >= 0);
            });
            b.setDaemon(true);
            b.start();
            burners.add(b);
        }
        try {
            JsonObject loaded = runUntil("kind", "pc", "value", "done");
            assertEquals("met", loaded.get("result").getAsString());
            assertEquals(quiet.get("cycle").getAsInt(), loaded.get("cycle").getAsInt());
            assertEquals(loaded.get("cycle").getAsInt(), call("record.state").get("last").getAsInt(),
                    "it stopped right at that cycle");
        } finally {
            on.set(false);
            for (Thread b : burners) {
                b.join();
            }
        }
    }

    @Test
    void runUntilARowChangesAndStop() throws Exception {
        open(Fixtures.counter(tmp));
        JsonObject row = call("record.addRow", "circuitId", main, "netId", counterNet());
        cycles(3);
        awaitRecorded(3);
        JsonObject d = runUntil("kind", "row", "value", row.get("id").getAsString());
        assertEquals("met", d.get("result").getAsString());
        assertEquals(4, d.get("cycle").getAsInt(), "the counter changes every cycle");
        assertEquals("row", d.get("kind").getAsString());
        // 만나지 않는 조건을 멈추기
        call("record.runUntil", "kind", "pc", "value", "0x00400000", "maxCycles", 100000);
        int mark = e.client.mark();
        assertTrue(call("record.stop").get("stopped").getAsBoolean());
        JsonObject stopped = e.client.awaitNotificationAfter(mark, "record.runUntil", n -> true);
        assertEquals("stopped", stopped.get("result").getAsString());
        assertFalse(call("record.stop").get("stopped").getAsBoolean());
    }

    // ---- Registers(C-05): Hallym MIPS 골든의 끝 레지스터 = SPIM 오라클 ----

    static final List<String> REF_MIPS = List.of("add", "addu", "sub", "subu", "and", "or", "xor", "nor", "slt",
            "sltu", "sll", "srl", "sra", "sllv", "srlv", "srav", "jr", "syscall", "mul", "addi", "addiu", "slti", "sltiu",
            "andi", "ori", "xori", "lui", "lw", "sw", "beq", "bne", "bgez", "bltz", "j", "jal", "nop");

    /** 워드가 값을 쓰는 레지스터(HallymMipsGoldenTest.dest와 같다). */
    static int dest(int word) {
        int op = word >>> 26;
        int rt = (word >>> 16) & 31;
        int rd = (word >>> 11) & 31;
        int funct = word & 63;
        if (op == 0) {
            return funct == 8 || funct == 12 || funct == 13 || funct >= 0x18 && funct <= 0x1b ? 0 : rd;
        } else if (op == 0x1c) {
            return rd;
        } else if (op == 3) {
            return 31;
        } else if (op >= 8 && op <= 15 || op >= 0x20 && op <= 0x26) {
            return rt;
        }
        return 0;
    }

    static Map<String, Long> oracle(String name) throws Exception {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(GOLDENS.resolve(name + ".regs"))) {
            if (line.startsWith("$")) {
                String[] p = line.trim().split("\\s+");
                out.put(p[0], Long.parseLong(p[1], 16));
            }
        }
        return out;
    }

    /** 레지스터 패널이 보이는 값: 이름 → 값(32비트 글자). */
    Map<String, JsonObject> registers() {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (JsonElement r : call("record.registers").getAsJsonArray("rows")) {
            out.put(r.getAsJsonObject().get("name").getAsString(), r.getAsJsonObject());
        }
        return out;
    }

    @Test
    void theRegistersAtExitAreTheOracles() throws Exception {
        int compared = 0;
        for (String name : List.of("data", "branches", "main-later", "no-data", "space-gap", "pseudo", "no-handler")) {
            ExecutableImage img = image(name);
            if (img.entry() != 0x00400024L) {
                // 커밋한 ref-mips.circ는 PC가 0x00400024에서 시작한다(D-126). entry가 다른 이미지는 lib-mips
                // HallymMipsGoldenTest가 entry를 받는 생성기로 대조한다. 건너뛴 것은 통과가 아니다
                System.out.println("record: " + name + ": registers NOT compared here (entry "
                        + ExecutableImage.hex(img.entry()) + ", ref-mips.circ starts at 0x00400024; not a pass)");
                continue;
            }
            compared++;
            if (fileId != null) {
                call("file.close");
            }
            refMipsWith(img, name);
            JsonObject done = runUntil("kind", "halt", "maxCycles", 5000);
            assertEquals("met", done.get("result").getAsString(), name);
            Map<String, JsonObject> regs = registers();
            JsonObject st = call("record.registers");
            assertEquals("all", st.get("mode").getAsString(), "ref-mips marks no register file: every register");
            assertFalse(st.get("unmapped").getAsBoolean(), "labels $1..$31 give the numbers");
            Map<String, Long> want = oracle(name);
            TreeSet<Integer> written = new TreeSet<>();
            for (int w : img.textWords().tailMap(0x00400024L).values()) {
                if (REF_MIPS.contains(Disassembler.mnemonic(w))) {
                    written.add(dest(w));
                }
            }
            written.remove(0);
            assertFalse(written.isEmpty());
            for (int n : written) {
                String reg = kr.ac.hallym.hcs.engine.record.InstructionFields.REG[n];
                JsonObject r = regs.get(reg);
                assertNotNull(r, name + " " + reg + " in " + regs.keySet());
                assertEquals(String.format("%32s", Long.toBinaryString(want.get(reg))).replace(' ', '0'),
                        r.get("value").getAsString(), name + " " + reg);
                assertEquals("$" + n, r.get("alias").getAsString(), "the circuit's own label beside");
                assertEquals(n, r.get("number").getAsInt());
            }
            // Hallym MIPS 레지스터 창의 묶음 순서, PC는 Special
            List<String> groups = new ArrayList<>();
            for (JsonObject r : regs.values()) {
                String g = r.get("group").getAsString();
                if (groups.isEmpty() || !groups.get(groups.size() - 1).equals(g)) {
                    groups.add(g);
                }
            }
            assertEquals(List.of("Special", "Return values", "Arguments", "Temporaries", "Saved", "Pointers",
                    "Return address", "Reserved"), groups, name);
            JsonObject pc = regs.get("PC");
            assertNotNull(pc, "ref-mips' PC register (driving the pc tunnel through the entry XOR, D-108)");
            System.out.println("record: " + name + ": registers at exit (cycle " + done.get("cycle").getAsInt()
                    + ") = oracle for " + written.size() + " registers");
        }
        assertTrue(compared >= 4, "most goldens start at 0x00400024: " + compared);
    }

    // ---- Memory(C-06, D-140): 골든을 올린 Data Memory ----

    @Test
    void theMemoryTableShowsTheLoadedDataAndTheStack() throws Exception {
        ExecutableImage img = image("data");
        refMipsWith(img, "data");
        JsonObject m = call("record.memory");
        assertEquals(1, m.get("parts").getAsInt(), "one Data Memory holds data and stack");
        JsonArray rows = m.getAsJsonArray("rows");
        JsonObject head = rows.get(0).getAsJsonObject();
        assertEquals("section", head.get("kind").getAsString());
        assertEquals("data", head.get("section").getAsString());
        assertEquals("0x10010000", head.get("addr").getAsString());
        // .data: msg "sum = ", nums 3 5 7 -1, count 4
        JsonObject first = rows.get(1).getAsJsonObject();
        assertEquals("0x10010000", first.get("addr").getAsString());
        List<String> w = strings(first.getAsJsonArray("words"));
        assertEquals(String.format("%08x", img.dataWords().get(0x10010000L)), w.get(0));
        assertEquals(String.format("%08x", img.dataWords().get(0x10010008L)), w.get(2));
        assertEquals("00000003", w.get(2));
        JsonArray labels = first.getAsJsonArray("labels");
        assertEquals("msg", labels.get(0).getAsJsonObject().getAsJsonArray("names").get(0).getAsString());
        assertEquals("nums", labels.get(1).getAsJsonObject().getAsJsonArray("names").get(0).getAsString());
        boolean zeros = false;
        boolean stack = false;
        for (JsonElement r : rows) {
            JsonObject o = r.getAsJsonObject();
            zeros |= o.get("kind").getAsString().equals("zeros") && o.get("section").getAsString().equals("data");
            if (o.get("kind").getAsString().equals("section") && o.get("section").getAsString().equals("stack")) {
                stack = true;
                // 깊이 기준(lib-mips D-126·D-140): 스택에 아직 접근이 없으면 영역 맨 위, 접근이 모두 reg $sp 아래면 그 값
                assertEquals("0x80000000", o.get("base").getAsString(), "no stack access yet: the region's top");
            }
        }
        assertTrue(zeros, "the rest of the data region is one row");
        assertTrue(stack);
    }

    /** 재귀 factorial이 도는 중: 스택 구간의 깊이 기준 = reg $sp, $sp 표시가 가장 낮은 줄에(D-140 MemoryTable). */
    @Test
    void theStackSectionFollowsSp() throws Exception {
        Path hmx = TESTS.toPath().resolve("hmx/mips/factorial.hmx");
        HmxParser.Result r = HmxParser.read(hmx.toFile());
        refMipsWith(r.image, "factorial", hmx);
        cycles(30);
        awaitRecorded(30);
        JsonArray rows = call("record.memory").getAsJsonArray("rows");
        JsonObject stack = null;
        JsonObject lowest = null;
        for (JsonElement x : rows) {
            JsonObject o = x.getAsJsonObject();
            if (o.get("section").getAsString().equals("stack")) {
                if (o.get("kind").getAsString().equals("section")) {
                    stack = o;
                } else {
                    lowest = o;
                }
            }
        }
        assertNotNull(stack);
        String sp = null;
        for (JsonElement x : call("record.registers").getAsJsonArray("rows")) {
            if (x.getAsJsonObject().get("name").getAsString().equals("$sp")) {
                sp = String.format("0x%08x", Long.parseLong(x.getAsJsonObject().get("value").getAsString(), 2));
            }
        }
        assertNotNull(sp);
        assertTrue(stack.get("depth").getAsLong() > 0, "the recursion is under way: " + stack);
        assertEquals(Long.decode(stack.get("base").getAsString()) - Long.decode(sp), stack.get("depth").getAsLong());
        assertEquals(sp, lowest.getAsJsonObject("pointers").get("$sp").getAsString(), "the $sp mark on the lowest row");
    }

    /**
     * 재귀 factorial(tests/hmx/mips/factorial.hmx)을 exit까지: 레지스터 패널이 SPIM이 끝에 두는 값을 보인다(6! = 720을
     * $s0·$a0에, $v0 = 10(exit), $sp는 main이 둔 0x7fffeffc로 돌아옴, $ra = main의 jal 다음 워드, li $sp가 쓴 $at). lib-mips
     * RefMipsTest가 같은 프로그램을 SPIM과 대조한다.
     */
    @Test
    void theRegistersAfterFactorialAreSpims() throws Exception {
        Path hmx = TESTS.toPath().resolve("hmx/mips/factorial.hmx");
        ExecutableImage img = HmxParser.read(hmx.toFile()).image;
        refMipsWith(img, "factorial", hmx);
        JsonObject done = runUntil("kind", "halt", "maxCycles", 5000);
        assertEquals("met", done.get("result").getAsString());
        Map<String, JsonObject> regs = registers();
        long main = img.symbols().get("main");
        Map<String, Long> want = new LinkedHashMap<>();
        want.put("$s0", 720L);
        want.put("$a0", 720L);
        want.put("$v0", 10L);
        want.put("$sp", 0x7fffeffcL);
        want.put("$ra", main + 16);
        want.put("$at", 0x7fff0000L); // li $sp, 0x7fffeffc = lui $at, 0x7fff; ori $sp, $at, 0xeffc
        for (Map.Entry<String, Long> w : want.entrySet()) {
            assertEquals(String.format("%32s", Long.toBinaryString(w.getValue())).replace(' ', '0'),
                    regs.get(w.getKey()).get("value").getAsString(), w.getKey());
        }
        // Exit가 1이 된 사이클의 바로 앞 사이클이 exit의 syscall이다(그 상승 에지에 Console이 exit)
        assertEquals("syscall", call("record.instruction", "cycle", done.get("cycle").getAsInt() - 1).get("mnemonic")
                .getAsString());
    }

    @Test
    void aSpaceGapIsOneZeroRow() throws Exception {
        ExecutableImage img = image("space-gap");
        refMipsWith(img, "space-gap");
        JsonArray rows = call("record.memory").getAsJsonArray("rows");
        int zeroRuns = 0;
        long lastData = img.symbols().get("last");
        boolean lastSeen = false;
        for (JsonElement r : rows) {
            JsonObject o = r.getAsJsonObject();
            if (!o.get("section").getAsString().equals("data")) {
                continue;
            }
            if (o.get("kind").getAsString().equals("zeros")) {
                zeroRuns++;
                assertTrue(o.get("count").getAsLong() >= 4);
            }
            if (o.get("kind").getAsString().equals("words")) {
                long a = Long.decode(o.get("addr").getAsString());
                lastSeen |= a <= lastData && lastData < a + 16;
            }
        }
        assertTrue(zeroRuns >= 2, "the .space gap and the rest of the region: " + rows);
        assertTrue(lastSeen, "the word after the gap has its own row");
    }

    // ---- Instruction(C-07) ----

    @Test
    void theInstructionIsSplitIntoHallymMipsFields() throws Exception {
        ExecutableImage img = image("data");
        refMipsWith(img, "data");
        runUntil("kind", "pc", "value", "next");
        JsonObject in = call("record.instruction");
        assertEquals("lw $9, 0($16)", in.get("text").getAsString());
        assertEquals("lw", in.get("mnemonic").getAsString());
        assertEquals("I", in.get("format").getAsString());
        List<String> names = new ArrayList<>();
        int bit = 31;
        for (JsonElement f : in.getAsJsonArray("fields")) {
            JsonObject o = f.getAsJsonObject();
            names.add(o.get("name").getAsString());
            assertEquals(bit, o.get("hi").getAsInt(), "the fields cover the word without a gap");
            bit = o.get("lo").getAsInt() - 1;
            assertEquals(o.get("hi").getAsInt() - o.get("lo").getAsInt() + 1, o.get("bits").getAsString().length());
        }
        assertEquals(-1, bit);
        assertEquals(List.of("opcode", "rs", "rt", "immediate"), names);
        JsonArray f = in.getAsJsonArray("fields");
        assertEquals("100011", f.get(0).getAsJsonObject().get("bits").getAsString());
        assertEquals("lw", f.get(0).getAsJsonObject().get("meaning").getAsString());
        assertEquals("$s0", f.get(1).getAsJsonObject().get("meaning").getAsString());
        assertEquals("$t1", f.get(2).getAsJsonObject().get("meaning").getAsString());
        assertEquals("0", f.get(3).getAsJsonObject().get("value").getAsString());
        // 분기: 목적지 주소와 라벨(bnez = bne $s1, $0, next)
        runUntil("kind", "instruction", "value", "bne");
        JsonObject bne = call("record.instruction");
        JsonObject imm = bne.getAsJsonArray("fields").get(3).getAsJsonObject();
        assertEquals("immediate", imm.get("name").getAsString());
        assertEquals(String.format("0x%08x [next]", img.symbols().get("next")), imm.get("meaning").getAsString());
        assertTrue(Integer.parseInt(imm.get("value").getAsString()) < 0, "a backward branch: a negative offset");
    }

    // ---- 레지스터 파일, Mark as PC, Register Mapping(hcs:ext, v1 그대로) ----

    @Test
    void markAsPcRegisterFileAndMappingAreSavedAsV1Did() throws Exception {
        File f = tmp.resolve("demo.circ").toFile();
        Files.copy(DATAPATH.toPath(), f.toPath());
        open(f);
        cycles(2);
        awaitRecorded(2);
        JsonObject regs = call("record.registers");
        // 표시 전의 저장(돌린 뒤라 원조 도구 몇 개가 불러와져 있을 수 있다): 표시 뒤의 저장과 hcs:ext 앞까지 같아야 한다
        File base = tmp.resolve("base.circ").toFile();
        call("file.save", "path", base.getPath());
        assertEquals("all", regs.get("mode").getAsString());
        JsonArray cand = regs.getAsJsonArray("candidates");
        assertEquals(1, cand.size());
        assertEquals("regfile", cand.get(0).getAsJsonObject().get("name").getAsString());
        JsonObject pcRow = regs.getAsJsonArray("rows").get(0).getAsJsonObject();
        assertEquals("PC", pcRow.get("name").getAsString());
        assertEquals("Special", pcRow.get("group").getAsString());
        assertTrue(pcRow.get("markable").getAsBoolean());
        assertFalse(pcRow.has("markedPc"));

        // Mark as PC(D-103): 회로마다 hcs:pc
        String pcId = pcRow.get("componentId").getAsString();
        JsonObject marked = call("record.markPc", "circuitId", main, "componentId", pcId, "on", true);
        assertTrue(marked.get("changed").getAsBoolean());
        assertTrue(marked.get("dirty").getAsBoolean());
        assertTrue(call("record.registers").getAsJsonArray("rows").get(0).getAsJsonObject().get("markedPc")
                .getAsBoolean());
        assertFalse(call("record.markPc", "circuitId", main, "componentId", pcId, "on", true).get("changed")
                .getAsBoolean());

        // Mark as Register File(D-076) → $0..$31
        String rf = cand.get(0).getAsJsonObject().get("circuitId").getAsString();
        assertTrue(call("record.markRegisterFile", "circuitId", rf, "on", true).get("changed").getAsBoolean());
        JsonObject fileMode = call("record.registers");
        assertEquals("file", fileMode.get("mode").getAsString());
        assertEquals("regfile", fileMode.getAsJsonObject("registerFile").get("name").getAsString());
        JsonObject map = call("record.registerMapping");
        JsonArray inside = map.getAsJsonArray("registers");
        assertTrue(inside.size() >= 2);
        // 대응은 v1 regmap처럼 부품 자리로 말한다(엔진이 다시 시작해도 되살리기가 그대로 보낸다, D-142)
        JsonArray r1 = null;
        JsonArray r3 = null;
        for (JsonElement x : inside) {
            String n = x.getAsJsonObject().get("name").getAsString();
            if (n.equals("$1")) {
                r1 = x.getAsJsonObject().getAsJsonArray("loc");
            } else if (n.equals("$3")) {
                r3 = x.getAsJsonObject().getAsJsonArray("loc");
            }
        }
        assertNotNull(r1);
        assertNotNull(r3);
        assertEquals(r1, map.getAsJsonObject("map").get("1"), "label numbers first");
        assertEquals(r3, map.getAsJsonObject("map").get("3"));
        // 1과 3을 바꾼다
        Map<String, Object> m = new HashMap<>();
        m.put("1", r3);
        m.put("3", r1);
        assertEquals(-32602, e.client.fail("record.setRegisterMapping", params("fileId", fileId, "circuitId", main,
                "map", m)).code, "the circuit must be the marked register file");
        Map<String, Object> nowhere = new HashMap<>();
        nowhere.put("2", new int[] {5, 5});
        assertEquals(1, e.client.fail("record.setRegisterMapping", params("fileId", fileId, "circuitId", rf,
                "map", nowhere)).code, "no Register there");
        assertTrue(call("record.setRegisterMapping", "circuitId", rf, "map", m).get("changed").getAsBoolean());
        JsonObject now = call("record.registerMapping");
        assertEquals(r3, now.getAsJsonObject("map").get("1"));
        assertEquals(r1, now.getAsJsonObject("map").get("3"));
        assertEquals(r1, now.getAsJsonObject("guess").get("1"), "the guess is unchanged");
        Map<String, JsonObject> rows = registers();
        assertEquals("$3", rows.get("$at").get("alias").getAsString(), "$1 is now the part labelled $3");

        // 저장: v1과 같은 hcs:ext 항목(pc, regfile, regmap), 원조 부분은 그대로
        File marked2 = tmp.resolve("marked.circ").toFile();
        call("file.save", "path", marked2.getPath());
        String xml = new String(Files.readAllBytes(marked2.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.contains("<hcs:pc at=\"(300,200)\"/>"), xml);
        assertTrue(xml.contains("<hcs:regfile/>"), xml);
        assertTrue(xml.contains("<hcs:regmap "), xml);
        String orig = new String(Files.readAllBytes(base.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        // 회로 부분(첫 <circuit부터 확장 블록 앞까지)은 바이트까지 같다: 표시는 hcs:ext에만 들어간다
        assertEquals(orig.substring(orig.indexOf("<circuit "), orig.indexOf("<hcs:ext")),
                xml.substring(xml.indexOf("<circuit "), xml.indexOf("<hcs:ext")),
                "the circuits are byte for byte the same; the marks are in the extension block only");

        // 되돌리기(Logisim 기록 그대로): 대응, 레지스터 파일 표시, PC 표시 순
        call("edit.undo");
        assertEquals(r1, call("record.registerMapping").getAsJsonObject("map").get("1"));
        call("edit.undo");
        assertEquals("all", call("record.registers").get("mode").getAsString());
        call("edit.undo");
        assertFalse(call("record.registers").getAsJsonArray("rows").get(0).getAsJsonObject().has("markedPc"));
        // 레지스터·카운터가 아닌 부품은 PC로 표시하지 않는다
        String imem = Fixtures.byName(snapshot().getAsJsonArray("components"), "Instruction Memory").get(0).get("id")
                .getAsString();
        assertEquals(-32602, e.client.fail("record.markPc", params("fileId", fileId, "circuitId", main, "componentId",
                imem, "on", true)).code);
    }

    /** 레지스터 패널의 값은 v1 MachineState.registers와 같다(표시 없음·레지스터 파일 표시 두 경우, 앞 사이클과의 바뀜 포함). */
    @Test
    void theRegisterRowsAgreeWithV1MachineState() throws Exception {
        File f = tmp.resolve("demo.circ").toFile();
        Files.copy(DATAPATH.toPath(), f.toPath());
        open(f);
        cycles(3);
        awaitRecorded(3);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                String rf = call("record.registers").getAsJsonArray("candidates").get(0).getAsJsonObject()
                        .get("circuitId").getAsString();
                call("record.markRegisterFile", "circuitId", rf, "on", true);
            }
            for (int cycle = 1; cycle <= 3; cycle++) {
                final int c = cycle;
                List<kr.ac.hallym.hcs.app.cycle.MachineState.Reg> v1 = e.onEngine(() -> {
                    kr.ac.hallym.hcs.engine.doc.Doc d = e.engine.files().get(fileId);
                    Recording r = e.engine.record(fileId).recording();
                    kr.ac.hallym.hcs.app.cycle.CycleModel m = new kr.ac.hallym.hcs.app.cycle.CycleModel(r.circuit(), r,
                            kr.ac.hallym.hcs.app.cycle.CycleModel.findCpu(r.circuit()), null);
                    return new kr.ac.hallym.hcs.app.cycle.MachineState(m, d.file()).registers(c);
                });
                JsonArray ours = call("record.registers", "cycle", c).getAsJsonArray("rows");
                int matched = 0;
                int valued = 0;
                for (kr.ac.hallym.hcs.app.cycle.MachineState.Reg g : v1) {
                    JsonObject row = null;
                    for (JsonElement x : ours) {
                        JsonObject o = x.getAsJsonObject();
                        boolean same = g.name.equals("PC") ? o.get("name").getAsString().equals("PC")
                                : g.number >= 0 && o.get("number").getAsInt() == g.number;
                        if (same) {
                            row = o;
                        }
                    }
                    assertNotNull(row, "pass " + pass + " cycle " + c + ": " + g.name);
                    String want = g.value == null ? null : kr.ac.hallym.hcs.engine.record.RecordSessionAccess.text(g.value);
                    assertEquals(want, row.get("value").isJsonNull() ? null : row.get("value").getAsString(), g.name);
                    assertEquals(g.changed, row.get("changed").getAsBoolean(), g.name + " changed");
                    matched++;
                    if (want != null) {
                        valued++;
                    }
                }
                assertTrue(matched >= 3, "pass " + pass + ": " + matched);
                // 같은 null끼리 맞는 것이 아니다: 지난 사이클의 값이 남아 있다(표시해도 기록을 새로 시작하지 않는다)
                assertTrue(valued >= 3, "pass " + pass + " cycle " + c + ": " + valued + " rows with a value");
            }
        }
    }

    /**
     * Mark as Register File·Mark as PC(파일의 첫 동작이면 고침 표시가 켜진다)와 저장(꺼진다)은 넷을 바꾸지 않는다: 지난
     * 사이클이 그대로 남는다. 전에는 원조 라이브러리 사건 DIRTY_STATE를 편집으로 보아 다음 전파에서 기록을 새로 시작해,
     * theRegisterRowsAgreeWithV1MachineState가 CI에서 가끔 {@code $at} null로 실패했다(D-144).
     */
    @Test
    void markingAndSavingKeepThePastCycles() throws Exception {
        File f = tmp.resolve("demo.circ").toFile();
        Files.copy(DATAPATH.toPath(), f.toPath());
        open(f);
        cycles(3);
        JsonObject before = awaitRecorded(3);
        assertEquals(0, before.get("first").getAsInt());
        String rf = call("record.registers").getAsJsonArray("candidates").get(0).getAsJsonObject()
                .get("circuitId").getAsString();
        assertTrue(call("record.markRegisterFile", "circuitId", rf, "on", true).get("dirty").getAsBoolean());
        cycles(1); // 표시 뒤 첫 전파·틱: 옛 코드는 여기서 스텝 7부터 새로 적었다
        awaitRecorded(4);
        e.client.call("file.save", params("fileId", fileId, "path", f.getPath()));
        assertFalse(call("file.dirty").get("dirty").getAsBoolean(), "saved");
        cycles(1);
        JsonObject after = awaitRecorded(5);
        assertEquals(0, after.get("first").getAsInt(), "the past cycles are kept: " + after);
        assertEquals(before.get("generation").getAsInt(), after.get("generation").getAsInt(), "not started again");
        JsonArray rows = call("record.registers", "cycle", 1).getAsJsonArray("rows");
        int valued = 0;
        for (JsonElement x : rows) {
            if (!x.getAsJsonObject().get("value").isJsonNull()) {
                valued++;
            }
        }
        assertTrue(valued >= 3, "cycle 1 still has its values: " + rows);
    }

    @Test
    void theHeadOfTheTableAndTheStatusBarReadTheDatapath() throws Exception {
        open(DATAPATH);
        cycles(3);
        awaitRecorded(3);
        JsonObject t = call("record.table");
        assertTrue(t.get("cpu").getAsBoolean());
        JsonObject c0 = t.getAsJsonArray("columns").get(0).getAsJsonObject();
        assertEquals("0x00000000", c0.get("pc").getAsString());
        assertNotNull(c0.get("word").getAsString());
        assertFalse(c0.get("text").getAsString().isEmpty());
        JsonObject st = call("record.state");
        assertEquals(3, st.get("cycle").getAsInt());
        assertEquals(t.getAsJsonArray("columns").get(3).getAsJsonObject().get("pc").getAsString(),
                st.get("pc").getAsString(), "the status bar's PC is the shown cycle's (the register labelled PC)");
    }

    /**
     * 지난 사이클과 지금을 빠르게 오가도(record.view가 시뮬레이터에 상태를 바꿔 끼움) 시뮬레이션이 온전하다: 끼울 상태의
     * 부품은 시뮬레이터 스레드에 넘기기 전에 더럽다고 표시한다(D-144 결정 9, 원조 SmallSet은 두 스레드에서 깨짐).
     */
    @Test
    void viewingBackAndForthQuicklyKeepsTheSimulationWhole() throws Exception {
        open(DATAPATH);
        call("sim.watch", "circuitId", main);
        cycles(4);
        awaitRecorded(4);
        for (int i = 0; i < 150; i++) {
            call("record.view", "cycle", i % 4);
            call("record.view", "latest", true);
        }
        JsonObject st = call("record.state");
        assertEquals(4, st.get("cycle").getAsInt());
        assertFalse(st.get("past").getAsBoolean());
        cycles(2);
        assertEquals(6, awaitRecorded(6).get("cycle").getAsInt(), "the clock goes on from the latest cycle");
    }

    /**
     * 위 테스트가 CI에서 가끔 실패한 경합(D-171)을 늘 일으킨다: 지금으로 돌아올 때 끼우는 떼어 둔 상태를 시뮬레이터
     * 스레드가 아직 전파하고 있을 때 record.view가 그 상태의 더러운 부품 집합(원조 SmallSet)을 고쳤다. 시험용 부품이
     * 지금 상태의 전파 한가운데에서 시뮬레이터 스레드를 붙잡아 두는 동안, record.view는 전파가 끝날 때까지 기다려야
     * 한다(SimGate). 세우지 않으면 곧바로 돌아와 실패한다.
     */
    @Test
    void viewingWaitsUntilTheSimulatorIsOutsideAPropagation() throws Exception {
        File dir = tmp.resolve("gate").toFile();
        dir.mkdirs();
        open(Fixtures.counter(dir.toPath()));
        Blocker blocker = new Blocker();
        com.cburch.logisim.proj.Project proj = e.onEngine(() -> e.engine.files().get(fileId).project());
        com.cburch.logisim.comp.Component comp = blocker.createComponent(
                com.cburch.logisim.data.Location.create(600, 400), blocker.createAttributeSet());
        e.onEngine(() -> e.engine.sim(fileId).quiet(() -> {
            com.cburch.logisim.circuit.CircuitMutation m =
                    new com.cburch.logisim.circuit.CircuitMutation(proj.getCurrentCircuit());
            m.add(comp);
            m.execute();
            return null;
        }));
        reset(); // 부품을 더한 편집 뒤 기록을 스텝 0부터 다시
        cycles(3);
        awaitRecorded(3);
        // 지금 상태에만 표시를 둔다: 지난 사이클을 보고(떼어 둠) 지금으로 돌아오면 그 상태의 다시 전파가 붙잡힌다
        e.onEngine(() -> e.engine.sim(fileId).quiet(() -> {
            proj.getCircuitState().setData(comp, Blocker.LIVE);
            return null;
        }));
        blocker.arm();
        call("record.view", "cycle", 1);
        call("record.view", "latest", true);
        try {
            assertTrue(blocker.entered.await(Client.TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS),
                    "the simulator thread propagates the live state again");
            java.util.concurrent.CompletableFuture<JsonObject> back = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> call("record.view", "cycle", 1));
            Thread.sleep(300);
            assertFalse(back.isDone(), "record.view swapped states while the simulator thread was inside a propagation");
            blocker.release();
            assertTrue(back.get(Client.TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS).get("past").getAsBoolean());
        } finally {
            blocker.release();
        }
        call("record.view", "latest", true);
        cycles(2);
        assertEquals(5, awaitRecorded(5).get("cycle").getAsInt(), "the clock goes on from the latest cycle");
    }

    /** 시험용 부품: 켜 두면 표시가 있는 상태(떼어 둔 지금 상태)의 전파 한가운데에서 시뮬레이터 스레드를 한 번 붙잡는다. */
    static final class Blocker extends com.cburch.logisim.instance.InstanceFactory {
        /** 지금 상태에만 둔다. 다시 만든 지난 상태는 기록의 사본이라 이것을 갖지 않는다. */
        static final com.cburch.logisim.instance.InstanceData LIVE = new com.cburch.logisim.instance.InstanceData() {
            @Override
            public Object clone() {
                return null;
            }
        };
        final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch released = new java.util.concurrent.CountDownLatch(1);
        private volatile boolean armed;

        Blocker() {
            super("HcsTestBlocker");
            setOffsetBounds(com.cburch.logisim.data.Bounds.create(-10, -10, 20, 20));
        }

        void arm() {
            armed = true;
        }

        void release() {
            released.countDown();
        }

        @Override
        public void paintInstance(com.cburch.logisim.instance.InstancePainter painter) {
        }

        @Override
        public void propagate(com.cburch.logisim.instance.InstanceState state) {
            if (armed && state.getData() == LIVE) {
                armed = false;
                entered.countDown();
                try {
                    released.await(Client.TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** 필드 경로(C-07 데이터): demo-datapath의 스플리터 팔 op rs rt rd shamt funct → Hallym MIPS 이름으로 선 id들. */
    @Test
    void fieldPathsFollowTheNamedSplitterArms() throws Exception {
        open(DATAPATH);
        cycles(1);
        awaitRecorded(1);
        JsonObject f = call("record.fieldPaths", "circuitId", main);
        assertEquals("R", f.get("format").getAsString(), "add: an R-format word");
        JsonObject fields = f.getAsJsonObject("fields");
        assertEquals(List.of("opcode", "rs", "rt", "rd", "shamt", "funct"), new ArrayList<>(fields.keySet()));
        assertTrue(fields.getAsJsonArray("rs").size() > 0, "the rs arm's wires: " + fields);
        java.util.Set<String> wires = new java.util.HashSet<>();
        for (JsonElement w : snapshot().getAsJsonArray("wires")) {
            wires.add(w.getAsJsonObject().get("id").getAsString());
        }
        for (Map.Entry<String, JsonElement> e : fields.entrySet()) {
            for (JsonElement id : e.getValue().getAsJsonArray()) {
                assertTrue(wires.contains(id.getAsString()), e.getKey() + " " + id + " is a wire of the circuit");
            }
        }
    }

    /**
     * 파일을 열자마자 사이클을 돌려도, Reset 바로 뒤에 Run Until을 해도 기록은 스텝 0부터다(D-144: 원조 시뮬레이터가 첫
     * 전파·재설정 전파와 먼저 온 틱을 한 번에 처리하면 틱 뒤 상태가 스텝 0으로 적혔다. CI에서 한 번 드러남).
     */
    @Test
    void cyclesRightAfterOpeningOrResetAreRecordedFromStepZero() throws Exception {
        for (int i = 0; i < 5; i++) {
            File f = tmp.resolve("race" + i).toFile();
            f.mkdirs();
            JsonObject r = e.client.callObject("file.open", params("path", Fixtures.counter(f.toPath()).getPath()));
            fileId = r.get("fileId").getAsString();
            main = r.get("main").getAsString();
            JsonObject first = call("record.state");
            assertFalse(first.get("empty").getAsBoolean(), "step 0 is recorded when file.open answers: " + first);
            String q = counterNet();
            int mark = e.client.mark();
            e.client.call("sim.cycles", params("fileId", fileId, "n", 2)); // no wait after opening
            e.client.awaitNotificationAfter(mark, "sim.state", st -> st.get("fileId").getAsString().equals(fileId)
                    && st.get("cycle").getAsLong() == 2);
            awaitRecorded(2);
            assertEquals(List.of(bits8(0), bits8(1), bits8(2)), strings(rowValues(q)), "file " + i
                    + ": step 0 is the state before the first tick");
            // Reset and at once Run Until (no wait): the new recording is the start
            e.client.call("sim.reset", params("fileId", fileId));
            JsonObject d = runUntil("kind", "pc", "value", "0x00400000", "maxCycles", 3);
            assertEquals("limit", d.get("result").getAsString(), "not stopped by the reset it came after: " + d);
            assertEquals(3, d.get("cycle").getAsInt());
            call("file.close");
        }
    }

    /** The counter's net q in cycles 0..2, as the table shows it (a row added for it). */
    JsonArray rowValues(String q) {
        call("record.addRow", "circuitId", main, "netId", q);
        return call("record.table", "from", 0, "to", 2).getAsJsonArray("rows").get(0).getAsJsonObject()
                .getAsJsonArray("values");
    }

    @Test
    void recordStateIsSentOncePerChange() throws Exception {
        open(Fixtures.counter(tmp));
        int mark = e.client.mark();
        cycles(40);
        JsonObject last = e.client.awaitNotificationAfter(mark, "record.state", n -> n.get("last").getAsInt() == 40);
        assertEquals(40, last.get("cycle").getAsInt());
        List<JsonObject> sent = e.client.notificationsAfter(mark, "record.state");
        assertTrue(sent.size() <= 41, "at most one per frame and per change: " + sent.size());
        for (int i = 1; i < sent.size(); i++) {
            assertFalse(sent.get(i).equals(sent.get(i - 1)), "only changes are sent");
        }
    }
}
