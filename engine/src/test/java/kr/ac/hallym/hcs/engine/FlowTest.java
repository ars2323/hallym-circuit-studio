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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.model.Influence;
import kr.ac.hallym.hcs.engine.doc.Doc;

/**
 * 캔버스 덧그림의 자료(N-15, D-151): trace.influence·trace.net·flow.path·flow.activePath. 결과가 v1의 GUI 없는 코드와
 * 같은지(v1 고정 기대값 tests/circ/flow/*.flow, 활성 경로의 demo-datapath 고정 선분, 같은 객체로 계산한 v1 Influence)와
 * 모델·시뮬레이션 상태를 바꾸지 않는지 본다.
 */
class FlowTest {
    static final File FLOW = new File(Fixtures.CIRC_DIR, "flow");
    static final File DEMO = new File(Fixtures.CIRC_DIR, "demo-datapath.circ");

    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;
    /** 연 파일의 모든 회로의 부품(id → JSON). */
    final Map<String, JsonObject> parts = new HashMap<>();
    final Map<String, JsonObject> snaps = new HashMap<>();

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    JsonObject open(File f) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "o");
        File copy = dir.resolve(f.getName()).toFile();
        Files.copy(f.toPath(), copy.toPath());
        JsonObject r = e.client.callObject("file.open", params("path", copy.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        parts.clear();
        snaps.clear();
        for (JsonElement c : r.getAsJsonArray("circuits")) {
            String id = c.getAsJsonObject().get("circuitId").getAsString();
            JsonObject s = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", id));
            snaps.put(id, s);
            for (JsonElement x : s.getAsJsonArray("components")) {
                parts.put(x.getAsJsonObject().get("id").getAsString(), x.getAsJsonObject());
            }
        }
        return r;
    }

    String comp(String circuitId, String name, String label) {
        String found = null;
        for (JsonElement x : snaps.get(circuitId).getAsJsonArray("components")) {
            JsonObject o = x.getAsJsonObject();
            if (o.get("name").getAsString().equals(name)
                    && (label == null || label.equals(o.getAsJsonObject("attrs").has("label")
                            ? o.getAsJsonObject("attrs").get("label").getAsString() : null))) {
                found = o.get("id").getAsString();
            }
        }
        assertNotNull(found, name + " " + label);
        return found;
    }

    static int portNamed(JsonObject comp, String name) {
        for (JsonElement p : comp.getAsJsonArray("ports")) {
            JsonObject o = p.getAsJsonObject();
            if (o.has("name") && o.get("name").getAsString().equals(name)) {
                return o.get("i").getAsInt();
            }
        }
        throw new AssertionError("no port " + name);
    }

    static int portDir(JsonObject comp, String dir) {
        for (JsonElement p : comp.getAsJsonArray("ports")) {
            if (p.getAsJsonObject().get("dir").getAsString().equals(dir)) {
                return p.getAsJsonObject().get("i").getAsInt();
            }
        }
        throw new AssertionError("no " + dir + " port");
    }

    String label(String id) {
        JsonObject o = parts.get(id);
        JsonObject a = o.getAsJsonObject("attrs");
        String l = a.has("label") ? a.get("label").getAsString().trim() : "";
        return l.isEmpty() ? null : l;
    }

    JsonObject flow(Object... kv) {
        Object[] all = new Object[kv.length + 4];
        all[0] = "fileId";
        all[1] = fileId;
        all[2] = "circuitId";
        all[3] = main;
        System.arraycopy(kv, 0, all, 4, kv.length);
        return e.client.callObject("flow.path", params(all));
    }

    /** v1 SignalFlowPathTest.summary와 같은 글(tests/circ/flow/*.flow). */
    String summary(JsonObject p) {
        StringBuilder b = new StringBuilder();
        b.append("direction: ").append(p.get("backward").getAsBoolean() ? "backward" : "forward").append('\n');
        b.append("endpoints:\n");
        for (JsonElement x : p.getAsJsonArray("endpoints")) {
            JsonObject o = x.getAsJsonObject();
            b.append("  ").append(o.get("kind").getAsString().toUpperCase()).append(' ')
                    .append(o.get("label").getAsString()).append(" @").append(Math.round(o.get("time").getAsDouble()))
                    .append('\n');
        }
        b.append("passes:\n");
        for (JsonElement x : p.getAsJsonArray("passes")) {
            JsonObject o = x.getAsJsonObject();
            String l = label(o.get("componentId").getAsString());
            b.append("  ").append(l != null ? l : o.get("name").getAsString())
                    .append(o.get("boundary").getAsBoolean() ? " (boundary)" : "").append(" @")
                    .append(Math.round(o.get("time").getAsDouble())).append('\n');
        }
        b.append("jumps: ").append(p.getAsJsonArray("jumps").size()).append('\n');
        b.append("segments: ").append(p.getAsJsonArray("segments").size()).append('\n');
        b.append("next-cycle segments: ").append(count(p.getAsJsonArray("segments"))).append('\n');
        b.append("next-cycle passes: ").append(count(p.getAsJsonArray("passes"))).append('\n');
        b.append("loops: ").append(names(p.getAsJsonArray("loops"))).append('\n');
        b.append("undetermined: ").append(names(p.getAsJsonArray("undetermined"))).append('\n');
        b.append("total: ").append(Math.round(p.get("total").getAsDouble())).append('\n');
        return b.toString();
    }

    static long count(JsonArray a) {
        long n = 0;
        for (JsonElement x : a) {
            n += x.getAsJsonObject().get("cycle").getAsInt() > 0 ? 1 : 0;
        }
        return n;
    }

    List<String> names(JsonArray ids) {
        List<String> out = new ArrayList<>();
        for (JsonElement x : ids) {
            String l = label(x.getAsString());
            out.add(l != null ? l : parts.get(x.getAsString()).get("name").getAsString());
        }
        return out;
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    void expect(String name, JsonObject path) throws Exception {
        assertEquals(read(new File(FLOW, name + ".flow")), summary(path), name);
    }

    /** 엔진 스레드에서 이 파일의 문서. */
    Doc doc() throws Exception {
        return e.onEngine(() -> e.engine.files().get(fileId));
    }

    // ---- flow.path: v1과 같은 경로 ----

    @Test
    void signalFlowIsV1sPathOnTheFlowCircuits() throws Exception {
        open(new File(FLOW, "gate-chain.circ"));
        expect("gate-chain-forward", flow("componentId", comp(main, "Pin", "A")));
        expect("gate-chain-backward", flow("componentId", comp(main, "Pin", "Y"), "backward", true));
        open(new File(FLOW, "register.circ"));
        expect("register-stop", flow("componentId", comp(main, "Pin", "A")));
        expect("register-through", flow("componentId", comp(main, "Pin", "A"), "throughRegisters", true));
        open(new File(FLOW, "tunnels.circ"));
        expect("tunnels", flow("componentId", comp(main, "Pin", "A")));
        open(new File(FLOW, "subcircuit.circ"));
        JsonObject sub = flow("componentId", comp(main, "Pin", "A"));
        expect("subcircuit", sub);
        // 서브회로 안 걸음은 인스턴스 경로(맨 위부터의 부품 id)와 그 회로를 단다
        boolean inside = false;
        for (JsonElement x : sub.getAsJsonArray("passes")) {
            JsonObject o = x.getAsJsonObject();
            if (o.getAsJsonArray("path").size() > 0) {
                inside = true;
                assertFalse(o.get("circuitId").getAsString().equals(main));
                assertTrue(parts.containsKey(o.getAsJsonArray("path").get(0).getAsString()));
            }
        }
        assertTrue(inside, "a pass inside the subcircuit");
        open(new File(FLOW, "loop.circ"));
        JsonObject loop = flow("componentId", comp(main, "Pin", "A"));
        expect("loop", loop);
        assertTrue(loop.getAsJsonArray("loops").size() > 0);
    }

    @Test
    void signalFlowIsV1sPathOnTheDemo() throws Exception {
        open(DEMO);
        String pc = comp(main, "Register", "PC");
        expect("demo-pc", flow("componentId", pc, "port", 0));
        String alu = comp(main, "alu", null);
        expect("demo-alu-result", flow("componentId", alu, "port", portNamed(parts.get(alu), "Result")));
        String mux = comp(main, "Multiplexer", null);
        expect("demo-mux-input", flow("componentId", mux, "port", 1, "backward", true));
        String im = comp(main, "Instruction Memory", null);
        expect("demo-im-instr", flow("componentId", im, "port", portDir(parts.get(im), "out")));
        // 같은 누름은 늘 같은 글자(결정성, v1 sameClickGivesTheSamePathEveryTime)
        JsonObject first = flow("componentId", pc, "port", 0, "throughRegisters", true);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, flow("componentId", pc, "port", 0, "throughRegisters", true));
        }
        // 선 누름: 그 넷의 드라이버에서 퍼지고 누른 점을 단다
        JsonObject s = snaps.get(main);
        JsonObject w = s.getAsJsonArray("wires").get(0).getAsJsonObject();
        JsonObject fromWire = flow("wire", w.get("id").getAsString(), "at", w.getAsJsonArray("b"));
        assertEquals(w.getAsJsonArray("b"), fromWire.getAsJsonArray("click"));
        assertTrue(fromWire.getAsJsonArray("segments").size() > 0);
    }

    @Test
    void activePathOnlyReadsTheWatchedValues() throws Exception {
        open(new File(FLOW, "mux.circ"));
        String a = comp(main, "Pin", "A");
        // 보지 않는 회로: 값을 모르면 모든 가지와 "?"(v1 선택 미확정)
        JsonObject unknown = flow("componentId", a, "activePathOnly", true);
        assertEquals(1, unknown.getAsJsonArray("undetermined").size(), unknown.toString());
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        e.client.awaitNotificationAfter(mark, "sim.values", v -> true);
        // 선택 핀 S는 3상태 입력 핀이라 처음에는 떠 있다(x): 모든 가지, 선택 미확정
        expect("mux-active-selx", flow("componentId", a, "activePathOnly", true));
        // 원조 Poke가 x → 0 → 1로 바꾼다. S = 0: A를 고른다. S = 1: A는 어디에도 닿지 않는다
        String sel = comp(main, "Pin", "S");
        poke(sel);
        expect("mux-active-sel0", flow("componentId", a, "activePathOnly", true));
        poke(sel);
        expect("mux-active-sel1", flow("componentId", a, "activePathOnly", true));
        assertEquals(0, flow("componentId", comp(main, "Pin", "B"), "activePathOnly", true).getAsJsonArray("undetermined").size());
        // Active Path Only를 끄면 값과 상관없이 모든 가지(v1 기본)
        assertEquals(List.of("OUTPUT Y"), endpoints(flow("componentId", a)));
    }

    void poke(String id) {
        int mark = e.client.mark();
        e.client.callObject("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", id));
        e.client.awaitNotificationAfter(mark, "sim.values", v -> true);
    }

    static List<String> endpoints(JsonObject p) {
        List<String> out = new ArrayList<>();
        for (JsonElement x : p.getAsJsonArray("endpoints")) {
            out.add(x.getAsJsonObject().get("kind").getAsString().toUpperCase() + " " + x.getAsJsonObject().get("label").getAsString());
        }
        return out;
    }

    @Test
    void flowErrors() throws Exception {
        open(new File(FLOW, "gate-chain.circ"));
        assertEquals(1, e.client.fail("flow.path", params("fileId", fileId, "circuitId", main, "componentId", "k999999")).code);
        String a = comp(main, "Pin", "A");
        assertEquals(-32602, e.client.fail("flow.path", params("fileId", fileId, "circuitId", main, "componentId", a, "port", 9)).code);
        assertEquals(-32602, e.client.fail("flow.path", params("fileId", fileId, "circuitId", main, "wire", a)).code);
        String w = snaps.get(main).getAsJsonArray("wires").size() > 0
                ? snaps.get(main).getAsJsonArray("wires").get(0).getAsJsonObject().get("id").getAsString() : null;
        if (w != null) {
            assertEquals(-32602, e.client.fail("flow.path", params("fileId", fileId, "circuitId", main, "componentId", w)).code);
        }
    }

    // ---- trace.influence: v1 Influence 그대로 ----

    JsonObject influence(Object... kv) {
        Object[] all = new Object[kv.length + 4];
        all[0] = "fileId";
        all[1] = fileId;
        all[2] = "circuitId";
        all[3] = main;
        System.arraycopy(kv, 0, all, 4, kv.length);
        return e.client.callObject("trace.influence", params(all));
    }

    static Set<String> set(JsonArray a) {
        Set<String> s = new TreeSet<>();
        for (JsonElement x : a) {
            s.add(x.getAsString());
        }
        return s;
    }

    /** 같은 객체로 v1 Influence를 계산해 id 집합으로. */
    Map<String, Set<String>> v1Influence(List<String> from, Influence.Mode mode, boolean through, int depth,
            boolean between) throws Exception {
        return e.onEngine(() -> {
            Doc d = e.engine.files().get(fileId);
            Circuit c = d.circuit(main);
            List<Component> start = d.components(c, from);
            Influence inf = between ? Influence.between(c, start.get(0), start.get(1), through)
                    : Influence.of(c, start, mode, through, depth);
            Influence.View v = inf.view(c);
            Map<String, Set<String>> m = new HashMap<>();
            m.put("forwardWires", ids(d, v.forwardWires));
            m.put("forwardParts", ids(d, v.forwardParts));
            m.put("backwardWires", ids(d, v.backwardWires));
            m.put("backwardParts", ids(d, v.backwardParts));
            m.put("stops", ids(d, v.stops));
            m.put("tunnels", ids(d, v.tunnels));
            Set<String> inside = new TreeSet<>();
            for (Map.Entry<Component, Integer> x : v.inside.entrySet()) {
                inside.add(d.ids().of(x.getKey()) + "=" + x.getValue());
            }
            m.put("inside", inside);
            Set<String> max = new TreeSet<>();
            max.add(Integer.toString((between ? inf : Influence.of(c, start, mode, through, -1)).maxDepth()));
            m.put("max", max);
            return m;
        });
    }

    static Set<String> ids(Doc d, java.util.Collection<? extends Component> cs) {
        Set<String> s = new TreeSet<>();
        for (Component c : cs) {
            s.add(d.ids().of(c));
        }
        return s;
    }

    Map<String, Set<String>> engineSets(JsonObject r) {
        Map<String, Set<String>> m = new HashMap<>();
        m.put("forwardWires", set(r.getAsJsonObject("forward").getAsJsonArray("wires")));
        m.put("forwardParts", set(r.getAsJsonObject("forward").getAsJsonArray("parts")));
        m.put("backwardWires", set(r.getAsJsonObject("backward").getAsJsonArray("wires")));
        m.put("backwardParts", set(r.getAsJsonObject("backward").getAsJsonArray("parts")));
        m.put("stops", set(r.getAsJsonArray("stops")));
        m.put("tunnels", set(r.getAsJsonArray("tunnels")));
        Set<String> inside = new TreeSet<>();
        for (JsonElement x : r.getAsJsonArray("inside")) {
            inside.add(x.getAsJsonObject().get("componentId").getAsString() + "=" + x.getAsJsonObject().get("places").getAsInt());
        }
        m.put("inside", inside);
        Set<String> max = new TreeSet<>();
        max.add(Integer.toString(r.get("maxDepth").getAsInt()));
        m.put("max", max);
        return m;
    }

    @Test
    void influenceIsV1sInEveryMode() throws Exception {
        open(DEMO);
        String pc = comp(main, "Register", "PC");
        String alu = comp(main, "alu", null);
        String mux = comp(main, "Multiplexer", null);
        for (String mode : new String[] {"forward", "backward", "both"}) {
            for (boolean through : new boolean[] {false, true}) {
                for (List<String> from : List.of(List.of(pc), List.of(alu), List.of(pc, mux))) {
                    JsonObject r = influence("from", from, "mode", mode, "throughRegisters", through);
                    Influence.Mode m = Influence.Mode.valueOf(mode.toUpperCase());
                    assertEquals(v1Influence(from, m, through, -1, false), engineSets(r), mode + " " + through + " " + from);
                    assertEquals(-1, r.get("depth").getAsInt());
                }
            }
        }
        // 두 부품 사이
        JsonObject between = influence("from", List.of(pc, mux), "mode", "between");
        assertEquals(v1Influence(List.of(pc, mux), Influence.Mode.FORWARD, false, -1, true), engineSets(between));
        // PC 앞으로: 멈춘 곳과 안쪽에 닿은 서브회로("alu: N places"), 시작 부품
        JsonObject f = influence("from", List.of(pc), "mode", "forward");
        assertTrue(set(f.getAsJsonArray("origin")).contains(pc));
        assertTrue(f.getAsJsonArray("stops").size() > 0, f.toString());
        assertTrue(f.getAsJsonObject("forward").getAsJsonArray("wires").size() > 0);
        assertEquals(0, f.getAsJsonObject("backward").getAsJsonArray("wires").size());
    }

    @Test
    void influenceDepthNarrowsAndWidensToTheEnd() throws Exception {
        open(DEMO);
        String pc = comp(main, "Register", "PC");
        JsonObject all = influence("from", List.of(pc), "mode", "forward", "throughRegisters", true);
        int max = all.get("maxDepth").getAsInt();
        assertTrue(max >= 2, "a few steps: " + max);
        Set<String> previous = new TreeSet<>();
        for (int depth = 1; depth < max; depth++) {
            JsonObject r = influence("from", List.of(pc), "mode", "forward", "throughRegisters", true, "depth", depth);
            assertEquals(depth, r.get("depth").getAsInt());
            assertEquals(max, r.get("maxDepth").getAsInt());
            Set<String> wires = set(r.getAsJsonObject("forward").getAsJsonArray("wires"));
            assertTrue(wires.containsAll(previous), "a step more reaches at least as far");
            assertEquals(v1Influence(List.of(pc), Influence.Mode.FORWARD, true, depth, false).get("forwardWires"), wires);
            previous = wires;
        }
        // 끝 이상은 끝까지(-1)
        JsonObject end = influence("from", List.of(pc), "mode", "forward", "throughRegisters", true, "depth", max);
        assertEquals(-1, end.get("depth").getAsInt());
        assertEquals(engineSets(all), engineSets(end));
    }

    @Test
    void influenceLinksTheTwoTunnelsOfANet() throws Exception {
        open(new File(FLOW, "tunnels.circ"));
        JsonObject r = influence("from", List.of(comp(main, "Pin", "A")), "mode", "forward");
        // A의 터널 하나가 NOT 앞 터널 둘로: 셋이면 점선 없이 터널만 강조(v1)
        assertTrue(r.getAsJsonArray("tunnels").size() > 0, r.toString());
        for (JsonElement l : r.getAsJsonArray("links")) {
            assertEquals(2, l.getAsJsonArray().size());
        }
    }

    @Test
    void influenceErrors() throws Exception {
        open(DEMO);
        String pc = comp(main, "Register", "PC");
        assertEquals(-32602, e.client.fail("trace.influence", params("fileId", fileId, "circuitId", main, "from", List.of(pc), "mode", "sideways")).code);
        assertEquals(-32602, e.client.fail("trace.influence", params("fileId", fileId, "circuitId", main, "from", List.of(), "mode", "forward")).code);
        assertEquals(-32602, e.client.fail("trace.influence", params("fileId", fileId, "circuitId", main, "from", List.of(pc), "mode", "between")).code);
        assertEquals(-32602, e.client.fail("trace.influence", params("fileId", fileId, "circuitId", main, "from", List.of(pc), "mode", "forward", "depth", 0)).code);
        assertEquals(1, e.client.fail("trace.influence", params("fileId", fileId, "circuitId", main, "from", List.of("k999999"), "mode", "forward")).code);
    }

    // ---- flow.activePath: v1 고정 선분(D-099) ----

    static String text(JsonArray muxes) {
        List<String> segs = new ArrayList<>();
        for (JsonElement m : muxes) {
            for (JsonElement s : m.getAsJsonObject().getAsJsonArray("segments")) {
                JsonArray a = s.getAsJsonArray().get(0).getAsJsonArray();
                JsonArray b = s.getAsJsonArray().get(1).getAsJsonArray();
                segs.add("(" + a.get(0) + "," + a.get(1) + ")-(" + b.get(0) + "," + b.get(1) + ")");
            }
        }
        return String.join(" ", segs);
    }

    /** v1 ActivePathOverlayTest의 고정 기대값(demo-datapath의 MemtoReg MUX). */
    static final String SELECT_0 = "(1100,240)-(1140,240) (1140,240)-(1140,210) (1140,210)-(1490,210) (1490,210)-(1490,420) (1490,420)-(1510,420)";
    static final String SELECT_1 = "(1440,440)-(1510,440)";

    @Test
    void activePathIsTheSelectedMuxBranchOnly() throws Exception {
        open(DEMO);
        JsonObject none = e.client.callObject("flow.activePath", params("fileId", fileId, "circuitId", main));
        assertFalse(none.get("watched").getAsBoolean(), "not watched: no values to read");
        assertEquals(0, none.getAsJsonArray("muxes").size());
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        e.client.awaitNotificationAfter(mark, "sim.values", v -> true);
        JsonObject a = e.client.callObject("flow.activePath", params("fileId", fileId, "circuitId", main));
        assertTrue(a.get("watched").getAsBoolean());
        assertEquals(1, a.getAsJsonArray("muxes").size());
        assertEquals(0, a.getAsJsonArray("muxes").get(0).getAsJsonObject().get("input").getAsInt());
        assertEquals(comp(main, "Multiplexer", null), a.getAsJsonArray("muxes").get(0).getAsJsonObject().get("componentId").getAsString());
        assertEquals(SELECT_0, text(a.getAsJsonArray("muxes")));
        mark = e.client.mark();
        e.client.callObject("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", comp(main, "Pin", "MemtoReg")));
        e.client.awaitNotificationAfter(mark, "sim.values", v -> true);
        JsonObject b = e.client.callObject("flow.activePath", params("fileId", fileId, "circuitId", main));
        assertEquals(SELECT_1, text(b.getAsJsonArray("muxes")));
    }

    // ---- trace.net: v1 넷 정보 ----

    @Test
    void netInformationListsDriversReadersAndOthers() throws Exception {
        open(DEMO);
        String alu = comp(main, "alu", null);
        int result = portNamed(parts.get(alu), "Result");
        String netId = null;
        String wire = null;
        for (JsonElement n : snaps.get(main).getAsJsonArray("nets")) {
            JsonObject o = n.getAsJsonObject();
            for (JsonElement p : o.getAsJsonArray("ports")) {
                if (p.getAsJsonArray().get(0).getAsString().equals(alu) && p.getAsJsonArray().get(1).getAsInt() == result) {
                    netId = o.get("id").getAsString();
                    wire = o.getAsJsonArray("wires").get(0).getAsString();
                }
            }
        }
        assertNotNull(netId);
        JsonObject byNet = e.client.callObject("trace.net", params("fileId", fileId, "circuitId", main, "netId", netId));
        JsonObject byWire = e.client.callObject("trace.net", params("fileId", fileId, "circuitId", main, "wire", wire));
        assertEquals(byNet, byWire);
        assertEquals(32, byNet.get("width").getAsInt());
        assertEquals(1, byNet.getAsJsonArray("drivers").size(), byNet.toString());
        JsonObject d = byNet.getAsJsonArray("drivers").get(0).getAsJsonObject();
        assertEquals(alu, d.get("componentId").getAsString());
        assertEquals(result, d.get("port").getAsInt());
        assertEquals("alu #1 (Result)", d.get("text").getAsString()); // v1 넷 정보와 같은 공용 식별자
        assertTrue(byNet.getAsJsonArray("readers").size() >= 2, "the MUX input and Data Memory Addr: " + byNet);
        assertEquals(1, e.client.fail("trace.net", params("fileId", fileId, "circuitId", main, "netId", "n99999")).code);
    }

    // ---- 모델·상태를 바꾸지 않는다 ----

    @Test
    void readingTheOverlaysChangesNothing() throws Exception {
        open(DEMO);
        int mark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        e.client.awaitNotificationAfter(mark, "sim.values", v -> true);
        // 서브회로 안 상태의 객체들(원조가 붙인 하위 상태): 읽은 뒤에도 같은 객체이고 새로 생기지 않는다
        Map<Component, Object> before = e.onEngine(() -> substates(e.engine.files().get(fileId)));
        int changedBefore = e.client.notifications("model.changed").size();
        String pc = comp(main, "Register", "PC");
        String alu = comp(main, "alu", null);
        for (String id : new String[] {pc, alu, comp(main, "Multiplexer", null)}) {
            for (boolean back : new boolean[] {false, true}) {
                flow("componentId", id, "backward", back, "activePathOnly", true, "throughRegisters", true);
                influence("from", List.of(id), "mode", "both", "throughRegisters", true);
            }
        }
        e.client.callObject("flow.activePath", params("fileId", fileId, "circuitId", main));
        e.client.callObject("trace.net", params("fileId", fileId, "circuitId", main,
                "netId", snaps.get(main).getAsJsonArray("nets").get(0).getAsJsonObject().get("id").getAsString()));
        // 보지 않는 서브회로(alu)를 맨 위로 한 흐름도 상태를 만들지 않는다
        String aluCircuit = parts.get(alu).get("subcircuit").getAsString();
        JsonObject inner = snaps.get(aluCircuit);
        String someInner = inner.getAsJsonArray("components").get(0).getAsJsonObject().get("id").getAsString();
        e.client.callObject("flow.path", params("fileId", fileId, "circuitId", aluCircuit, "componentId", someInner, "activePathOnly", true));
        Map<Component, Object> after = e.onEngine(() -> substates(e.engine.files().get(fileId)));
        assertEquals(before.keySet(), after.keySet());
        for (Map.Entry<Component, Object> x : before.entrySet()) {
            assertSame(x.getValue(), after.get(x.getKey()));
        }
        assertFalse(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean());
        assertEquals(changedBefore, e.client.notifications("model.changed").size(), "no model.changed");
    }

    /** 파일의 모든 회로 상태에서 서브회로 인스턴스 → 하위 상태 객체(원조 getData, 읽기만). */
    static Map<Component, Object> substates(Doc d) {
        Map<Component, Object> m = new IdentityHashMap<>();
        CircuitState top = d.project().getCircuitState();
        walk(top, m, new LinkedHashSet<>());
        return m;
    }

    private static void walk(CircuitState s, Map<Component, Object> m, Set<CircuitState> seen) {
        if (s == null || !seen.add(s)) {
            return;
        }
        for (Component c : s.getCircuit().getNonWires()) {
            if (c.getFactory() instanceof SubcircuitFactory) {
                Object data = s.getData(c);
                m.put(c, data);
                if (data instanceof CircuitState) {
                    walk((CircuitState) data, m, seen);
                }
            }
        }
    }

    @Test
    void wiresInTheResultsAreTheSnapshotsWires() throws Exception {
        open(DEMO);
        Set<String> wires = new TreeSet<>();
        for (JsonElement w : snaps.get(main).getAsJsonArray("wires")) {
            wires.add(w.getAsJsonObject().get("id").getAsString());
        }
        JsonObject r = influence("from", List.of(comp(main, "Register", "PC")), "mode", "both", "throughRegisters", true);
        assertTrue(wires.containsAll(set(r.getAsJsonObject("forward").getAsJsonArray("wires"))));
        assertTrue(wires.containsAll(set(r.getAsJsonObject("backward").getAsJsonArray("wires"))));
        for (String k : new String[] {"stops", "origin", "tunnels"}) {
            assertTrue(parts.keySet().containsAll(set(r.getAsJsonArray(k))), k);
        }
        Wire any = e.onEngine(() -> e.engine.files().get(fileId).circuit(main).getWires().iterator().next());
        assertNotNull(any);
    }
}
