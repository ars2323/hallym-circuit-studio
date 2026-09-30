/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 튜토리얼 예제(N-18, D-161)를 진짜 엔진으로: 화면의 [건너뛰기]가 보내는 것과 같은 의도(electron/src/renderer/app/
 * tutorial/actions.json)로 단계를 밟으면 판정에 쓰는 사실이 그대로 나온다.
 *
 * <ul>
 * <li>논리설계: 처음 Messages는 count의 클럭 한 줄뿐, AND를 놓고 이으면 A·B·Y가 AND에 닿고, A·B를 1로 찌르면 Y = 1,
 * Label g1, half_adder를 자리에 놓으면 입력이 이어지고, 클럭을 이으면 0건, 1 Cycle 뒤 count = 1.
 * <li>컴퓨터구조: 처음 Messages는 RegWirte 한 줄("혹시 RegWrite?"), 이름을 고치면 0건, tutorial.hmx를 불러 PC 시작 =
 * entry, 끝까지 돌리면 Console "sum = 14"와 exit, 그동안 메시지 0건, 레지스터가 SPIM 오라클(data.regs)과 같다.
 * </ul>
 * 예제 파일은 복사본을 연다(튜토리얼도 그렇다). 원본 바이트가 그대로인지도 본다.
 */
class TutorialExamplesTest {
    static final File TESTS = Fixtures.REF_MIPS.getParentFile().getParentFile();
    static final File TUTORIAL = new File(TESTS, "tutorial");
    static final File ACTIONS = new File(TESTS.getParentFile(), "electron/src/renderer/app/tutorial/actions.json");
    /** 화면의 판정 함수(tutorial/facts.ts)를 시험하는 진짜 엔진의 스냅숏(-Phcs.update=true로 다시 씀). */
    static final File FACTS = new File(System.getProperty("hcs.electronFixtures"), "tutorial");

    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;
    JsonObject actions;
    final Map<String, JsonObject> checkpoints = new java.util.LinkedHashMap<>();

    /** 지금 main의 스냅숏과 메시지를 이름으로 남긴다. */
    void checkpoint(String name) {
        JsonObject o = new JsonObject();
        o.add("snapshot", snapshot(main));
        o.add("messages", diag());
        checkpoints.put(name, o);
    }

    /** 남긴 것을 electron/tests/fixtures/tutorial/<file>에 쓴다(갱신 때), 아니면 같은 이름들이 있는지 본다. */
    void writeCheckpoints(String file) throws Exception {
        File out = new File(FACTS, file);
        if (Boolean.getBoolean("hcs.update")) {
            Files.createDirectories(FACTS.toPath());
            JsonObject all = new JsonObject();
            all.addProperty("about", "The real engine's snapshots and messages at the tutorial's steps (engine TutorialExamplesTest; ./gradlew :engine:test --tests '*TutorialExamplesTest' -Phcs.update=true)");
            for (Map.Entry<String, JsonObject> c : checkpoints.entrySet()) {
                all.add(c.getKey(), c.getValue());
            }
            Files.writeString(out.toPath(), new com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(all) + "\n");
        }
        JsonObject saved = JsonParser.parseString(Files.readString(out.toPath())).getAsJsonObject();
        for (String name : checkpoints.keySet()) {
            assertTrue(saved.has(name), out + ": " + name + " (-Phcs.update=true)");
            assertEquals(codes(checkpoints.get(name).getAsJsonArray("messages")), codes(saved.getAsJsonObject(name).getAsJsonArray("messages")), name);
        }
    }

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
        actions = JsonParser.parseString(new String(Files.readAllBytes(ACTIONS.toPath()), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    File copy(String... names) throws Exception {
        for (String n : names) {
            Files.copy(new File(TUTORIAL, n).toPath(), tmp.resolve(n));
        }
        return tmp.resolve(names[0]).toFile();
    }

    void open(File f) {
        JsonObject r = e.client.callObject("file.open", params("path", f.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
    }

    JsonObject snapshot(String circuitId) {
        return e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", circuitId));
    }

    JsonArray diag() {
        return e.client.callObject("diag.list", params("fileId", fileId)).getAsJsonArray("messages");
    }

    /** 목록이 조건을 채울 때까지 다시 묻는다(진단은 편집 뒤 다시 본다). */
    JsonArray diagUntil(java.util.function.Predicate<JsonArray> ok) throws InterruptedException {
        long end = System.currentTimeMillis() + Client.TIMEOUT_MS;
        JsonArray l = diag();
        while (!ok.test(l) && System.currentTimeMillis() < end) {
            Thread.sleep(20);
            l = diag();
        }
        return l;
    }

    static List<String> codes(JsonArray messages) {
        List<String> out = new ArrayList<>();
        for (JsonElement m : messages) {
            out.add(m.getAsJsonObject().get("code").getAsString());
        }
        return out;
    }

    static Object[] point(JsonElement p) {
        JsonArray a = p.getAsJsonArray();
        return new Object[] {a.get(0).getAsInt(), a.get(1).getAsInt()};
    }

    void wires(JsonArray list) {
        for (JsonElement w : list) {
            List<int[]> pts = new ArrayList<>();
            for (JsonElement p : w.getAsJsonArray()) {
                pts.add(new int[] {p.getAsJsonArray().get(0).getAsInt(), p.getAsJsonArray().get(1).getAsInt()});
            }
            e.client.call("edit.addWire", params("fileId", fileId, "circuitId", main, "points", pts.toArray()));
        }
    }

    String idOf(JsonObject snap, String name, String label) {
        for (JsonElement c : snap.getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            JsonObject attrs = o.getAsJsonObject("attrs");
            if (o.get("name").getAsString().equals(name)
                    && (label == null || attrs.has("label") && attrs.get("label").getAsString().equals(label))) {
                return o.get("id").getAsString();
            }
        }
        return null;
    }

    /** 부품 포트가 든 넷의 포트들 "이름#포트". */
    static List<String> netPorts(JsonObject snap, String compId, int port) {
        Map<String, String> names = new HashMap<>();
        for (JsonElement c : snap.getAsJsonArray("components")) {
            names.put(c.getAsJsonObject().get("id").getAsString(), c.getAsJsonObject().get("name").getAsString());
        }
        for (JsonElement n : snap.getAsJsonArray("nets")) {
            JsonArray ports = n.getAsJsonObject().getAsJsonArray("ports");
            boolean has = false;
            for (JsonElement p : ports) {
                has |= p.getAsJsonArray().get(0).getAsString().equals(compId) && p.getAsJsonArray().get(1).getAsInt() == port;
            }
            if (has) {
                List<String> out = new ArrayList<>();
                for (JsonElement p : ports) {
                    out.add(names.get(p.getAsJsonArray().get(0).getAsString()) + "#" + p.getAsJsonArray().get(1).getAsInt());
                }
                out.sort(null);
                return out;
            }
        }
        return List.of();
    }

    Map<String, String> values(int from) {
        Map<String, String> ret = new HashMap<>();
        for (JsonObject v : e.client.notificationsAfter(from, "sim.values")) {
            if (v.get("fileId").getAsString().equals(fileId) && v.get("circuitId").getAsString().equals(main)) {
                for (Map.Entry<String, JsonElement> n : v.getAsJsonObject("nets").entrySet()) {
                    ret.put(n.getKey(), n.getValue().getAsString());
                }
            }
        }
        return ret;
    }

    void awaitValue(int from, String net, String want) throws InterruptedException {
        long end = System.currentTimeMillis() + Client.TIMEOUT_MS;
        while (!want.equals(values(from).get(net))) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError(net + " never became " + want + ": " + values(from).get(net));
            }
            Thread.sleep(10);
        }
    }

    static String netId(JsonObject snap, String compId, int port) {
        return Fixtures.netOf(snap.getAsJsonArray("nets"), compId, port);
    }

    void cycles(int n) {
        int mark = e.client.mark();
        e.client.call("sim.cycles", params("fileId", fileId, "n", n));
        e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("fileId").getAsString().equals(fileId)
                && (s.get("cycle").getAsLong() >= n || !s.get("running").getAsBoolean()));
    }

    // ---- 논리설계 및 실험 ----

    @Test
    void theLogicTrackStepsGiveTheFactsTheTutorialWaitsFor() throws Exception {
        byte[] original = Files.readAllBytes(new File(TUTORIAL, "tutorial-logic.circ").toPath());
        open(copy("tutorial-logic.circ"));
        JsonObject logic = actions.getAsJsonObject("logic");
        // 처음: count의 클럭 한 줄
        JsonArray first = diagUntil(l -> l.size() == 1);
        assertEquals(List.of("CLOCK_UNCONNECTED"), codes(first));
        assertTrue(first.get(0).getAsJsonObject().getAsJsonObject("text").get("en").getAsString().contains("count"));
        checkpoint("initial");

        // L3: AND를 놓는다. L4: A·B → AND, AND → Y
        JsonObject and = logic.getAsJsonObject("and");
        String andId = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main,
                "lib", and.get("lib").getAsString(), "name", and.get("name").getAsString(),
                "loc", new int[] {and.getAsJsonArray("loc").get(0).getAsInt(), and.getAsJsonArray("loc").get(1).getAsInt()}))
                .get("id").getAsString();
        checkpoint("andPlaced");
        wires(logic.getAsJsonArray("andWires"));
        checkpoint("andWired");
        JsonObject s = snapshot(main);
        String a = idOf(s, "Pin", "A");
        String b = idOf(s, "Pin", "B");
        String y = idOf(s, "Pin", "Y");
        andId = idOf(s, "AND Gate", null);
        assertEquals(List.of("AND Gate#1", "Pin#0"), netPorts(s, a, 0), "A → the AND's first input");
        assertEquals(List.of("AND Gate#5", "Pin#0"), netPorts(s, b, 0), "B → the AND's last input");
        assertEquals(List.of("AND Gate#0", "Pin#0"), netPorts(s, y, 0), "the AND → Y");

        // L5: A·B를 1로 → Y = 1
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        String yNet = netId(s, y, 0);
        awaitValue(mark, yNet, "0");
        e.client.call("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", a));
        e.client.call("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", b));
        awaitValue(mark, yNet, "1");

        // L6: Label
        e.client.call("edit.setAttr", params("fileId", fileId, "circuitId", main, "ids", new String[] {andId},
                "attr", "label", "value", logic.get("label").getAsString()));
        assertNotNull(idOf(snapshot(main), "AND Gate", "g1"));
        checkpoint("labelled");

        // L8: half_adder를 자리에 → 입력이 이어지고 메시지는 클럭 한 줄 그대로
        JsonObject ha = logic.getAsJsonObject("halfAdder");
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", null,
                "name", ha.get("name").getAsString(), "loc", new int[] {ha.getAsJsonArray("loc").get(0).getAsInt(),
                    ha.getAsJsonArray("loc").get(1).getAsInt()}));
        s = snapshot(main);
        String inst = idOf(s, "half_adder", null);
        assertNotNull(inst);
        int met = 0;
        for (int i = 0; i < 4; i++) {
            met += netPorts(s, inst, i).size() == 2 ? 1 : 0;
        }
        assertEquals(2, met, "both half_adder inputs meet their wires");
        assertEquals(List.of("CLOCK_UNCONNECTED"), codes(diagUntil(l -> l.size() == 1)));
        checkpoint("halfAdder");

        // L12: Clock → count의 클럭 입력 → 0건
        wires(logic.getAsJsonArray("clockWires"));
        assertEquals(List.of(), codes(diagUntil(l -> l.size() == 0)));
        checkpoint("clockWired");

        // L14: 1 Cycle → count = 1
        s = snapshot(main);
        String count = idOf(s, "Register", "count");
        String q = netId(s, count, 0);
        int m2 = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        cycles(1);
        awaitValue(m2, q, "0001");
        assertEquals(List.of(), codes(diag()), "still nothing after a cycle");

        assertArrayEquals(original, Files.readAllBytes(new File(TUTORIAL, "tutorial-logic.circ").toPath()));
        writeCheckpoints("logic.json");
    }

    // ---- 컴퓨터구조 ----

    @Test
    void theMipsTrackStepsGiveTheFactsTheTutorialWaitsFor() throws Exception {
        byte[] original = Files.readAllBytes(new File(TUTORIAL, "tutorial-mips.circ").toPath());
        JsonObject mips = actions.getAsJsonObject("mips");
        File circ = copy("tutorial-mips.circ", "tutorial.hmx", "tutorial.s");
        open(circ);
        // C4: 짝 없는 터널 한 줄, 가까운 이름 RegWrite
        JsonArray first = diagUntil(l -> l.size() == 1);
        assertEquals(List.of("TUNNEL_UNPAIRED"), codes(first));
        JsonObject m = first.get(0).getAsJsonObject();
        assertEquals(mips.get("fixed").getAsString(), m.get("near").getAsString());
        assertTrue(m.getAsJsonObject("text").get("en").getAsString().contains(mips.get("typo").getAsString()));
        checkpoint("initial");

        // C5: 터널 이름을 고치면 0건
        JsonObject s = snapshot(main);
        String typo = idOf(s, "Tunnel", mips.get("typo").getAsString());
        e.client.call("edit.setAttr", params("fileId", fileId, "circuitId", main, "ids", new String[] {typo},
                "attr", "label", "value", mips.get("fixed").getAsString()));
        assertEquals(List.of(), codes(diagUntil(l -> l.size() == 0)));
        checkpoint("fixed");

        // C6: tutorial.hmx → PC 시작 = entry(사실 줄 없음)
        JsonObject load = e.client.callObject("mips.load", params("fileId", fileId,
                "path", tmp.resolve(mips.get("program").getAsString()).toString()));
        assertTrue(load.toString().contains("0x00400000"), load.toString());   // entry = main (no start-up code)
        JsonObject facts = e.client.callObject("mips.facts", params("fileId", fileId));
        assertTrue(!facts.toString().contains("entry") || facts.getAsJsonObject("program") != null, facts.toString());

        // C13: 끝까지 → Console "sum = 14", exit, 메시지 0건
        cycles(60);
        JsonObject console = e.client.callObject("mips.console", params("fileId", fileId));
        JsonObject c0 = console.getAsJsonArray("consoles").get(0).getAsJsonObject();
        assertEquals("sum = 14", c0.get("text").getAsString());
        assertTrue(c0.get("exited").getAsBoolean());
        assertEquals(List.of(), codes(diag()), "a working datapath: no messages after the run");

        // Registers: 프로그램이 쓴 레지스터가 SPIM 오라클(tests/hmx/hallym-mips-v2.4.0/data.regs)과 같다
        Map<String, Long> want = Map.of("$at", 0x10010000L, "$v0", 0xaL, "$a0", 0xeL, "$t0", 0xeL, "$t1", 0xffffffffL,
                "$s0", 0x10010018L, "$s1", 0L);
        Map<String, Long> got = new HashMap<>();
        for (JsonElement row : e.client.callObject("record.registers", params("fileId", fileId)).getAsJsonArray("rows")) {
            JsonObject o = row.getAsJsonObject();
            if (want.containsKey(o.get("key").getAsString()) && !o.get("value").isJsonNull()) {
                got.put(o.get("key").getAsString(), Long.parseLong(o.get("value").getAsString(), 2));
            }
        }
        assertEquals(want, got, "registers at exit");

        assertArrayEquals(original, Files.readAllBytes(new File(TUTORIAL, "tutorial-mips.circ").toPath()));
        writeCheckpoints("mips.json");
    }
}
