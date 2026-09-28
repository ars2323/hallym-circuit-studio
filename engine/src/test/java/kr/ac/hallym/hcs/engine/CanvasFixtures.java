/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 화면 캔버스의 시험 자료(N-05, N-06, D-137). 모두 엔진 API(docs/engine-api.md)로만 만든다: 화면이 받는 것과 같은
 * 글자다.
 * <ul>
 * <li>{@code geometry.json}: 부품 종류 × 대표 속성 조합(방향, 크기, 입력 수, 비트 수, 선택 비트 …)마다 엔진이 준
 * 부품(경계·포트 위치·속성). 기하 동등성 검사(electron/tests/unit/canvas-geometry.test.ts)가 렌더러 정의의 포트와
 * 몸체를 여기에 맞춘다. 서브회로(기본 모양·사용자 모양)의 인스턴스도 든다.</li>
 * <li>{@code circuits/<이름>.json}: ref-mips·demo-datapath의 회로별 스냅숏과 값 프레임(주 회로를 본 첫 값과 1
 * Cycle씩의 바뀐 값, 몸체 상태). 가짜 엔진이 그대로 돌려줘 e2e·스크린숏이 실제 회로를 같은 픽셀로 그린다.</li>
 * </ul>
 * 인자: 출력 폴더, 저장소 뿌리. 같은 JVM에서 정해진 순서로 만들어 id가 늘 같다. CI가 다시 만들어 비교한다.
 */
public final class CanvasFixtures {
    private static final Gson GSON = kr.ac.hallym.hcs.engine.rpc.Server.GSON; // 엔진이 보내는 글자와 같게(null 포함)
    /** 값 프레임 수(주 회로, 사이클 0 다음). */
    static final int CYCLES = 4;

    private CanvasFixtures() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Path repo = Path.of(args[1]);
        Files.createDirectories(out.resolve("circuits"));
        int cases;
        try (InProcess e = new InProcess()) {
            cases = geometry(e, repo, out.resolve("geometry.json"));
        }
        for (String[] f : new String[][] {{"tests/mips/ref-mips.circ", "ref-mips"},
            {"tests/circ/demo-datapath.circ", "demo-datapath"},
            {"electron/tests/fixtures/broken-datapath.circ", "broken-datapath"}}) {
            try (InProcess e = new InProcess()) {
                circuit(e, repo.resolve(f[0]).toFile(), out.resolve("circuits/" + f[1] + ".json"));
            }
        }
        System.out.println("canvas fixtures: " + cases + " geometry cases, 3 circuits -> " + out);
        System.exit(0);
    }

    // ---- 기하 ----

    /** 조합 하나: 라이브러리(null이면 이 파일의 회로), 이름, 속성들. */
    record Case(String lib, String name, Map<String, String> attrs) {
    }

    static final String[] FACINGS = {"east", "west", "north", "south"};

    static List<Case> cases() {
        List<Case> c = new ArrayList<>();
        // 게이트: 방향 × 크기 × 입력 수, 부정 입력, 긴 입력 줄(날개), 버스 폭
        for (String g : new String[] {"AND Gate", "OR Gate", "NAND Gate", "NOR Gate", "XOR Gate", "XNOR Gate",
            "Odd Parity", "Even Parity"}) {
            for (String f : FACINGS) {
                for (String size : new String[] {"30", "50", "70"}) {
                    for (String inputs : new String[] {"2", "3", "5"}) {
                        c.add(k("Gates", g, "facing", f, "size", size, "inputs", inputs));
                    }
                    c.add(k("Gates", g, "facing", f, "size", size, "inputs", "3", "negate0", "true", "negate2",
                            "true"));
                }
                c.add(k("Gates", g, "facing", f, "size", "50", "inputs", "9"));
            }
            c.add(k("Gates", g, "size", "30", "inputs", "12"));
            c.add(k("Gates", g, "size", "70", "inputs", "4"));
            c.add(k("Gates", g, "width", "8", "inputs", "2"));
        }
        for (String f : FACINGS) {
            for (String size : new String[] {"20", "30"}) {
                c.add(k("Gates", "NOT Gate", "facing", f, "size", size));
                c.add(k("Gates", "Controlled Inverter", "facing", f, "size", size, "control", "right"));
                c.add(k("Gates", "Controlled Inverter", "facing", f, "size", size, "control", "left"));
            }
            c.add(k("Gates", "Buffer", "facing", f));
            c.add(k("Gates", "Controlled Buffer", "facing", f, "control", "right"));
            c.add(k("Gates", "Controlled Buffer", "facing", f, "control", "left"));
        }
        // Plexers: 방향 × 선택 비트 × 선택 자리 × Enable
        for (String f : FACINGS) {
            for (String sel : new String[] {"1", "2", "3"}) {
                for (String loc : new String[] {"bl", "tr"}) {
                    for (String en : new String[] {"true", "false"}) {
                        c.add(k("Plexers", "Multiplexer", "facing", f, "select", sel, "selloc", loc, "enable", en));
                        c.add(k("Plexers", "Demultiplexer", "facing", f, "select", sel, "selloc", loc, "enable",
                                en));
                        c.add(k("Plexers", "Decoder", "facing", f, "select", sel, "selloc", loc, "enable", en));
                    }
                }
            }
            c.add(k("Plexers", "Multiplexer", "facing", f, "select", "4", "width", "32"));
            c.add(k("Plexers", "Priority Encoder", "facing", f, "select", "1"));
            c.add(k("Plexers", "Priority Encoder", "facing", f, "select", "3"));
            c.add(k("Plexers", "BitSelector", "facing", f, "width", "8", "group", "1"));
            c.add(k("Plexers", "BitSelector", "facing", f, "width", "32", "group", "4"));
        }
        // Wiring
        for (String f : FACINGS) {
            for (String fan : new String[] {"1", "2", "4"}) {
                for (String ap : new String[] {"left", "right", "center", "legacy"}) {
                    c.add(k("Wiring", "Splitter", "facing", f, "fanout", fan, "incoming", "8", "appear", ap));
                }
            }
            c.add(k("Wiring", "Pin", "facing", f));
            c.add(k("Wiring", "Pin", "facing", f, "output", "true"));
            c.add(k("Wiring", "Pin", "facing", f, "width", "8"));
            c.add(k("Wiring", "Pin", "facing", f, "width", "32", "output", "true"));
            c.add(k("Wiring", "Probe", "facing", f));
            c.add(k("Wiring", "Probe", "facing", f, "radix", "16"));
            c.add(k("Wiring", "Tunnel", "facing", f));
            c.add(k("Wiring", "Tunnel", "facing", f, "label", "RegWrite"));
            c.add(k("Wiring", "Tunnel", "facing", f, "label", "a", "width", "32"));
            c.add(k("Wiring", "Clock", "facing", f));
            c.add(k("Wiring", "Constant", "facing", f));
            c.add(k("Wiring", "Constant", "facing", f, "width", "32", "value", "0x400000"));
            c.add(k("Wiring", "Pull Resistor", "facing", f));
            c.add(k("Wiring", "Pull Resistor", "facing", f, "pull", "1"));
            c.add(k("Wiring", "Power", "facing", f));
            c.add(k("Wiring", "Ground", "facing", f));
            c.add(k("Wiring", "Transistor", "facing", f));
            c.add(k("Wiring", "Transmission Gate", "facing", f));
        }
        c.add(k("Wiring", "Splitter", "fanout", "3", "incoming", "32", "bit0", "0", "bit1", "1"));
        c.add(k("Wiring", "Bit Extender"));
        c.add(k("Wiring", "Bit Extender", "in_width", "16", "out_width", "32", "type", "sign"));
        c.add(k("Wiring", "Bit Extender", "in_width", "1", "out_width", "32", "type", "input"));
        // Arithmetic
        for (String a : new String[] {"Adder", "Subtractor", "Multiplier", "Divider", "Negator"}) {
            for (String w : new String[] {"1", "8", "32"}) {
                c.add(k("Arithmetic", a, "width", w));
            }
        }
        c.add(k("Arithmetic", "Comparator"));
        c.add(k("Arithmetic", "Comparator", "width", "32", "mode", "unsigned"));
        for (String s : new String[] {"ll", "lr", "ar", "rl", "rr"}) {
            c.add(k("Arithmetic", "Shifter", "shift", s));
        }
        c.add(k("Arithmetic", "Shifter", "width", "32"));
        c.add(k("Arithmetic", "BitAdder"));
        c.add(k("Arithmetic", "BitAdder", "inputs", "3", "width", "32"));
        c.add(k("Arithmetic", "BitFinder"));
        c.add(k("Arithmetic", "BitFinder", "type", "high0", "width", "32"));
        // Memory
        for (String ff : new String[] {"D Flip-Flop", "T Flip-Flop", "J-K Flip-Flop", "S-R Flip-Flop"}) {
            // T·J-K는 에지만(원조 AbstractFlipFlop allowLevelTriggers)
            boolean level = ff.startsWith("D") || ff.startsWith("S");
            for (String t : level ? new String[] {"rising", "falling", "high", "low"}
                    : new String[] {"rising", "falling"}) {
                c.add(k("Memory", ff, "trigger", t));
            }
        }
        for (String w : new String[] {"1", "8", "32"}) {
            c.add(k("Memory", "Register", "width", w));
            c.add(k("Memory", "Counter", "width", w, "max", w.equals("1") ? "0x1" : "0xff"));
            c.add(k("Memory", "Random", "width", w));
        }
        c.add(k("Memory", "Register", "width", "32", "trigger", "falling"));
        c.add(k("Memory", "Register", "width", "8", "trigger", "high"));
        for (String p : new String[] {"true", "false"}) {
            for (String len : new String[] {"1", "4", "8"}) {
                c.add(k("Memory", "Shift Register", "parallel", p, "length", len));
            }
            c.add(k("Memory", "Shift Register", "parallel", p, "length", "4", "width", "8"));
        }
        for (String aw : new String[] {"4", "8", "16", "24"}) {
            for (String dw : new String[] {"8", "32"}) {
                c.add(k("Memory", "RAM", "addrWidth", aw, "dataWidth", dw));
                c.add(k("Memory", "ROM", "addrWidth", aw, "dataWidth", dw));
            }
        }
        for (String bus : new String[] {"asynch", "separate"}) {
            c.add(k("Memory", "RAM", "bus", bus));
            c.add(k("Memory", "RAM", "bus", bus, "addrWidth", "16", "dataWidth", "32"));
        }
        // I/O
        for (String f : FACINGS) {
            c.add(k("I/O", "Button", "facing", f));
            c.add(k("I/O", "LED", "facing", f));
        }
        c.add(k("I/O", "7-Segment Display"));
        c.add(k("I/O", "Hex Digit Display"));
        c.add(k("I/O", "Joystick"));
        c.add(k("I/O", "Keyboard"));
        c.add(k("I/O", "DotMatrix"));
        c.add(k("I/O", "DotMatrix", "inputtype", "row"));
        c.add(k("I/O", "DotMatrix", "inputtype", "select"));
        c.add(k("I/O", "TTY"));
        // Base
        for (String h : new String[] {"left", "center", "right"}) {
            c.add(k("Base", "Text", "text", "ALU control", "halign", h));
        }
        c.add(k("Base", "Text", "text", "top", "valign", "top"));
        c.add(k("Base", "Text", "text", "bottom", "valign", "bottom"));
        // Hallym MIPS(옛 Stack은 목록에 없지만 옛 파일에 있다)
        String mips = "kr.ac.hallym.hcs.mips.MipsLibrary";
        c.add(k(mips, "Instruction Memory"));
        c.add(k(mips, "Data Memory"));
        c.add(k(mips, "Data Memory", "base", "0x10010000", "stacksize", "0x0"));
        c.add(k(mips, "Stack"));
        c.add(k(mips, "Console"));
        c.add(k(mips, "Radix Probe"));
        c.add(k(mips, "Radix Probe", "width", "8", "radix", "dec"));
        c.add(k(mips, "Radix Probe", "width", "1"));
        return c;
    }

    static Case k(String lib, String name, String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return new Case(lib, name, m);
    }

    static int geometry(InProcess e, Path repo, Path target) throws IOException {
        List<String> lines = new ArrayList<>();
        // 부품: 한 파일에 한 종류씩(모델 비교가 커지지 않게), 격자 400 간격으로 겹치지 않게
        Map<String, List<Case>> byKind = new LinkedHashMap<>();
        for (Case c : cases()) {
            byKind.computeIfAbsent(c.lib + "\u0000" + c.name, x -> new ArrayList<>()).add(c);
        }
        for (List<Case> group : byKind.values()) {
            JsonObject f = e.client.callObject("file.new", params());
            String fileId = f.get("fileId").getAsString();
            String main = f.get("main").getAsString();
            List<String> ids = new ArrayList<>();
            int i = 0;
            for (Case c : group) {
                int x = 400 + 400 * (i % 20);
                int y = 400 + 400 * (i / 20);
                i++;
                JsonObject attrs = new JsonObject();
                c.attrs.forEach(attrs::addProperty);
                JsonObject r = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main,
                        "lib", c.lib, "name", c.name, "loc", new int[] {x, y}, "attrs", attrs));
                ids.add(r.get("id").getAsString());
            }
            Map<String, JsonObject> comps = components(e, fileId, main);
            for (int j = 0; j < group.size(); j++) {
                lines.add(caseLine(group.get(j), comps.get(ids.get(j))));
            }
            e.client.callObject("file.close", params("fileId", fileId));
        }
        // 서브회로: demo-datapath의 사용자 모양(regfile, alu)과 subcircuit.circ의 기본 모양을 방향마다 놓는다
        for (String[] s : new String[][] {{"tests/circ/demo-datapath.circ", "regfile", "alu"},
            {"tests/circ/subcircuit.circ", null, null}}) {
            JsonObject f = e.client.callObject("file.open", params("path",
                    repo.resolve(s[0]).toAbsolutePath().toString()));
            String fileId = f.get("fileId").getAsString();
            String main = f.get("main").getAsString();
            List<String> names = new ArrayList<>();
            for (JsonElement c : f.getAsJsonArray("circuits")) {
                String n = c.getAsJsonObject().get("name").getAsString();
                if (!c.getAsJsonObject().get("circuitId").getAsString().equals(main)) {
                    names.add(n);
                }
            }
            List<Case> placed = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            int i = 0;
            for (String n : names) {
                for (String facing : FACINGS) {
                    Case c = k(null, n, "facing", facing);
                    JsonObject attrs = new JsonObject();
                    attrs.addProperty("facing", facing);
                    JsonObject r = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId",
                            main, "name", n, "loc", new int[] {3000 + 400 * i, 3000}, "attrs", attrs));
                    placed.add(c);
                    ids.add(r.get("id").getAsString());
                    i++;
                }
            }
            Map<String, JsonObject> comps = components(e, fileId, main);
            for (int j = 0; j < placed.size(); j++) {
                lines.add(caseLine(placed.get(j), comps.get(ids.get(j))));
            }
            e.client.callObject("file.close", params("fileId", fileId));
        }
        write(target, "{\"source\":\"./gradlew :engine:canvasFixtures (D-137): engine API edit.addComponent + "
                + "model.circuit\",\n\"cases\":[\n" + String.join(",\n", lines) + "\n]}\n");
        return lines.size();
    }

    private static Map<String, JsonObject> components(InProcess e, String fileId, String circuitId) {
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", circuitId));
        Map<String, JsonObject> m = new LinkedHashMap<>();
        for (JsonElement x : snap.getAsJsonArray("components")) {
            m.put(x.getAsJsonObject().get("id").getAsString(), x.getAsJsonObject());
        }
        return m;
    }

    private static String caseLine(Case c, JsonObject component) {
        JsonObject o = new JsonObject();
        StringBuilder name = new StringBuilder((c.lib == null ? "circuit" : c.lib) + "/" + c.name);
        c.attrs.forEach((k, v) -> name.append(' ').append(k).append('=').append(v));
        o.addProperty("case", name.toString());
        // id는 파일마다 새로 매기므로 기하 자료에서는 뺀다(값이 id에 기대지 않게)
        component.remove("id");
        if (component.has("subcircuit")) { // 회로 id도 엔진 일련번호: 이름으로 둔다
            component.addProperty("subcircuit", "circuit:" + c.name);
        }
        o.add("component", component);
        return GSON.toJson(o);
    }

    // ---- 회로와 값 ----

    static void circuit(InProcess e, File file, Path target) throws Exception {
        JsonObject f = e.client.callObject("file.open", params("path", file.getAbsolutePath()));
        String fileId = f.get("fileId").getAsString();
        String main = f.get("main").getAsString();
        List<String> circuitIds = new ArrayList<>();
        List<JsonObject> snapshots = new ArrayList<>();
        for (JsonElement c : f.getAsJsonArray("circuits")) {
            String id = c.getAsJsonObject().get("circuitId").getAsString();
            circuitIds.add(id);
            snapshots.add(e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", id)));
        }
        // 값: 서브회로를 따로 연 탭은 처음 값만(그 회로 자신의 맨 위 상태), 주 회로 안의 서브회로 인스턴스(캔버스가
        // 더블클릭으로 들어가 보는 것, sim.watch path)와 주 회로는 처음 값과 1 Cycle씩. 인스턴스와 주 회로는 Reset에서
        // 시작하므로 차례와 상관없이 같은 값이다.
        List<Watch> watch = new ArrayList<>();
        for (String id : circuitIds) {
            if (!id.equals(main)) {
                watch.add(new Watch(id, List.of(), id, List.of(firstFrame(e, fileId, id, List.of()))));
            }
        }
        JsonObject mainSnap = snapshots.get(circuitIds.indexOf(main));
        for (JsonElement ce : mainSnap.getAsJsonArray("components")) {
            JsonObject c = ce.getAsJsonObject();
            if (c.has("subcircuit") && c.get("lib").isJsonNull()) {
                List<String> path = List.of(c.get("id").getAsString());
                String inner = c.get("subcircuit").getAsString();
                watch.add(new Watch(main, path, inner, cycleFrames(e, fileId, main, path, inner)));
            }
        }
        watch.add(new Watch(main, List.of(), main, cycleFrames(e, fileId, main, List.of(), main)));

        // 엔진 id는 엔진 전체 일련번호이고 해시 순서에 따라 매겨지는 차례가 달라질 수 있다. 자료가 늘 같은 글자가
        // 되도록 회로는 목록 순서, 부품·선은 스냅숏 순서(위→아래, 왼쪽→오른쪽), 넷은 가장 앞 선·포트 순서로 다시 매긴다.
        Canon canon = new Canon();
        for (int i = 0; i < circuitIds.size(); i++) {
            canon.circuits.put(circuitIds.get(i), "c" + (i + 1));
        }
        for (JsonObject snap : snapshots) {
            canon.snapshot(snap);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"source\":\"./gradlew :engine:canvasFixtures (D-137): file.open, model.circuit, sim.watch, "
                + "sim.cycles; ids renumbered in order\",\n");
        sb.append("\"file\":").append(GSON.toJson(file.getName())).append(",\n");
        sb.append("\"main\":").append(GSON.toJson(canon.circuits.get(main))).append(",\n");
        sb.append("\"circuits\":[\n");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < snapshots.size(); i++) {
            out.add(GSON.toJson(canon.renamed(snapshots.get(i))));
        }
        sb.append(String.join(",\n", out)).append("\n],\n");
        sb.append("\"watch\":{\n");
        List<String> ws = new ArrayList<>();
        for (Watch w : watch) {
            List<String> fs = new ArrayList<>();
            for (JsonObject frame : w.frames()) {
                fs.add(GSON.toJson(canon.frame(w.circuit(), frame)));
            }
            // 열쇠: 회로 id, 인스턴스 안이면 "주 회로/인스턴스 id…"(sim.watch의 circuitId와 path)
            StringBuilder key = new StringBuilder(canon.circuits.get(w.root()));
            for (String id : w.path()) {
                key.append('/').append(canon.parts.get(id));
            }
            ws.add(GSON.toJson(key.toString()) + ":[\n" + String.join(",\n", fs) + "\n]");
        }
        sb.append(String.join(",\n", ws)).append("\n}}\n");
        write(target, sb.toString());
    }

    /** 엔진 id → 자료의 id(차례대로). */
    static final class Canon {
        final Map<String, String> circuits = new LinkedHashMap<>();
        final Map<String, String> parts = new LinkedHashMap<>();
        final Map<String, Map<String, String>> nets = new LinkedHashMap<>();
        int nextComponent = 1;
        int nextWire = 1;

        void snapshot(JsonObject snap) {
            for (JsonElement c : snap.getAsJsonArray("components")) {
                parts.put(c.getAsJsonObject().get("id").getAsString(), "k" + nextComponent++);
            }
            for (JsonElement w : snap.getAsJsonArray("wires")) {
                parts.put(w.getAsJsonObject().get("id").getAsString(), "w" + nextWire++);
            }
            // 넷: 든 선·포트 가운데 가장 앞의 것(새 번호) 순
            List<String[]> keyed = new ArrayList<>();
            for (JsonElement n : snap.getAsJsonArray("nets")) {
                JsonObject o = n.getAsJsonObject();
                String key = null;
                for (JsonElement w : o.getAsJsonArray("wires")) {
                    key = min(key, sortKey(parts.get(w.getAsString()), -1));
                }
                for (JsonElement p : o.getAsJsonArray("ports")) {
                    JsonArray pa = p.getAsJsonArray();
                    key = min(key, sortKey(parts.get(pa.get(0).getAsString()), pa.get(1).getAsInt()));
                }
                keyed.add(new String[] {key == null ? "" : key, o.get("id").getAsString()});
            }
            keyed.sort((a, b) -> a[0].compareTo(b[0]));
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i < keyed.size(); i++) {
                m.put(keyed.get(i)[1], "n" + i);
            }
            nets.put(snap.get("circuitId").getAsString(), m);
        }

        private static String sortKey(String id, int port) {
            // 부품(k)이 선(w)보다 앞, 번호는 자릿수를 맞춘다
            String kind = id.substring(0, 1);
            return (kind.equals("k") ? "0" : "1") + String.format("%08d", Integer.parseInt(id.substring(1)))
                    + String.format("%04d", port + 1);
        }

        private static String min(String a, String b) {
            return a == null || b.compareTo(a) < 0 ? b : a;
        }

        JsonObject renamed(JsonObject snap) {
            String circuit = snap.get("circuitId").getAsString();
            Map<String, String> netMap = nets.get(circuit);
            JsonObject o = snap.deepCopy();
            o.addProperty("circuitId", circuits.get(circuit));
            for (JsonElement c : o.getAsJsonArray("components")) {
                JsonObject co = c.getAsJsonObject();
                co.addProperty("id", parts.get(co.get("id").getAsString()));
                if (co.has("subcircuit")) {
                    co.addProperty("subcircuit", circuits.get(co.get("subcircuit").getAsString()));
                }
            }
            for (JsonElement w : o.getAsJsonArray("wires")) {
                JsonObject wo = w.getAsJsonObject();
                wo.addProperty("id", parts.get(wo.get("id").getAsString()));
            }
            List<JsonObject> ns = new ArrayList<>();
            for (JsonElement n : o.getAsJsonArray("nets")) {
                JsonObject no = n.getAsJsonObject();
                no.addProperty("id", netMap.get(no.get("id").getAsString()));
                // 넷 안의 선·포트도 새 번호 순(엔진의 차례는 해시 순서를 따를 수 있다)
                List<String> wl = new ArrayList<>();
                for (JsonElement w : no.getAsJsonArray("wires")) {
                    wl.add(parts.get(w.getAsString()));
                }
                wl.sort((a, b) -> sortKey(a, -1).compareTo(sortKey(b, -1)));
                JsonArray ws = new JsonArray();
                wl.forEach(ws::add);
                no.add("wires", ws);
                List<JsonArray> pl = new ArrayList<>();
                for (JsonElement p : no.getAsJsonArray("ports")) {
                    JsonArray pa = new JsonArray();
                    pa.add(parts.get(p.getAsJsonArray().get(0).getAsString()));
                    pa.add(p.getAsJsonArray().get(1));
                    pl.add(pa);
                }
                pl.sort((a, b) -> sortKey(a.get(0).getAsString(), a.get(1).getAsInt())
                        .compareTo(sortKey(b.get(0).getAsString(), b.get(1).getAsInt())));
                JsonArray ps = new JsonArray();
                pl.forEach(ps::add);
                no.add("ports", ps);
                ns.add(no);
            }
            ns.sort((a, b) -> Integer.compare(Integer.parseInt(a.get("id").getAsString().substring(1)),
                    Integer.parseInt(b.get("id").getAsString().substring(1))));
            JsonArray na = new JsonArray();
            ns.forEach(na::add);
            o.add("nets", na);
            return o;
        }

        JsonObject frame(String circuit, JsonObject frame) {
            Map<String, String> netMap = nets.get(circuit);
            JsonObject n = new JsonObject();
            for (Map.Entry<String, JsonElement> x : frame.getAsJsonObject("nets").entrySet()) {
                n.add(netMap.get(x.getKey()), x.getValue());
            }
            JsonObject b = new JsonObject();
            for (Map.Entry<String, JsonElement> x : frame.getAsJsonObject("bodies").entrySet()) {
                b.add(parts.get(x.getKey()), x.getValue());
            }
            JsonObject o = new JsonObject();
            o.add("nets", sorted(n));
            o.add("bodies", sorted(b));
            return o;
        }
    }

    /** 한 보기의 값 기록: 회로(root)와 인스턴스 경로(path), 값이 속한 회로(circuit), 프레임들. */
    record Watch(String root, List<String> path, String circuit, List<JsonObject> frames) {
    }

    /** sim.watch 뒤 처음 값(모든 넷과 몸체). 이어지는 전파가 끝나도록 잠깐 더 모은다. path가 있으면 인스턴스 안. */
    private static JsonObject firstFrame(InProcess e, String fileId, String root, List<String> path)
            throws InterruptedException {
        int mark = e.client.mark();
        JsonArray p = new JsonArray();
        path.forEach(p::add);
        e.client.callObject("sim.watch", path.isEmpty() ? params("fileId", fileId, "circuitId", root)
                : params("fileId", fileId, "circuitId", root, "path", p));
        e.client.awaitNotificationAfter(mark, "sim.values", v -> path.isEmpty() != v.has("path"));
        Thread.sleep(300);
        return merged(e, mark, null);
    }

    /** Reset에서 시작해 처음 값과 1 Cycle마다의 바뀐 값(CYCLES번). */
    private static List<JsonObject> cycleFrames(InProcess e, String fileId, String root, List<String> path,
            String circuit) throws InterruptedException {
        int reset = e.client.mark();
        e.client.callObject("sim.reset", params("fileId", fileId));
        e.client.awaitNotificationAfter(reset, "sim.state", st -> st.get("cycle").getAsLong() == 0
                && !st.get("ticking").getAsBoolean());
        List<JsonObject> frames = new ArrayList<>();
        frames.add(firstFrame(e, fileId, root, path));
        for (int k = 1; k <= CYCLES; k++) {
            int mark = e.client.mark();
            e.client.callObject("sim.cycles", params("fileId", fileId, "n", 1));
            final long want = k;
            e.client.awaitNotificationAfter(mark, "sim.state", st -> st.get("cycle").getAsLong() >= want
                    && !st.get("ticking").getAsBoolean());
            frames.add(merged(e, mark, circuit));
        }
        return frames;
    }

    /** mark 뒤에 온 sim.values를 겹친 것: {nets, bodies}. */
    private static JsonObject merged(InProcess e, int mark, String circuitId) {
        JsonObject nets = new JsonObject();
        JsonObject bodies = new JsonObject();
        for (JsonObject v : e.client.notificationsAfter(mark, "sim.values")) {
            if (circuitId != null && !v.get("circuitId").getAsString().equals(circuitId)) {
                continue;
            }
            for (Map.Entry<String, JsonElement> n : v.getAsJsonObject("nets").entrySet()) {
                nets.add(n.getKey(), n.getValue());
            }
            if (v.has("bodies")) {
                for (Map.Entry<String, JsonElement> b : v.getAsJsonObject("bodies").entrySet()) {
                    bodies.add(b.getKey(), b.getValue());
                }
            }
        }
        JsonObject o = new JsonObject();
        o.add("nets", sorted(nets));
        o.add("bodies", sorted(bodies));
        return o;
    }

    private static JsonObject sorted(JsonObject o) {
        JsonObject s = new JsonObject();
        o.keySet().stream().sorted((a, b) -> {
            int c = Integer.compare(a.length(), b.length());
            return c != 0 ? c : a.compareTo(b);
        }).forEach(key -> s.add(key, o.get(key)));
        return s;
    }

    private static void write(Path target, String text) throws IOException {
        Files.write(target, text.getBytes(StandardCharsets.UTF_8));
    }
}
