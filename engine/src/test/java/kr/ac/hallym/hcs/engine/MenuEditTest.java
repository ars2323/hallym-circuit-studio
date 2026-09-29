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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 속성 표·우클릭 메뉴·RAM·ROM 내용(N-10, D-157): model.attributes는 원조 속성 표가 보이는 것(고른 것, 공통 속성, 회로,
 * 도구), 틀린 값은 원조 parse가 거절하고 badValue, model.menu는 v1 메뉴의 사실(요약 줄, 포트, 고른 차례), 메뉴의 편집
 * 의도는 v1 동작 그대로 되돌리기 한 단계, RAM 내용은 시뮬레이션 상태, ROM 내용은 원조 되돌리기.
 */
class MenuEditTest {
    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;
    File base;

    // 부품 자리(기본 라이브러리, 원조 좌표)
    static final int AND_X = 300;
    static final int AND_Y = 200;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    /**
     * main: 입력 핀 A·B → AND(입력 2) → 출력 핀 Y, 레지스터 R1(8비트)과 그 입력 D의 8비트 선, 스플리터(8 → 2), 같은 이름
     * 터널 t 둘(따로), 상수(값 5), RAM(주소 4비트·데이터 8비트), ROM(같음, 내용 1 2 3), 프로브 하나.
     */
    void open() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", AND_X, AND_Y, "inputs", "2", "size", "50");
        Location in0 = CircuitBuilder.port(and, 1);
        Location in1 = CircuitBuilder.port(and, 2);
        b.add("Wiring", "Pin", 100, in0.getY(), "label", "A");
        b.add("Wiring", "Pin", 100, in1.getY(), "label", "B");
        b.wire(Location.create(100, in0.getY()), in0);
        b.wire(Location.create(100, in1.getY()), in1);
        b.add("Wiring", "Pin", 450, AND_Y, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(AND_X, AND_Y), Location.create(450, AND_Y));
        Component reg = b.add("Memory", "Register", 300, 400, "width", "8", "label", "R1");
        Location q = CircuitBuilder.port(reg, 1);
        b.wire(q, Location.create(q.getX() + 100, q.getY()));
        b.add("Wiring", "Tunnel", 600, 500, "label", "t");
        b.add("Wiring", "Tunnel", 700, 560, "label", "t", "facing", "west");
        b.add("Wiring", "Constant", 100, 600, "width", "8", "value", "0x5");
        b.add("Wiring", "Splitter", 300, 700, "incoming", "8", "fanout", "2");
        b.add("Memory", "RAM", 900, 200, "addrWidth", "4", "dataWidth", "8");
        b.add("Memory", "ROM", 900, 600, "addrWidth", "4", "dataWidth", "8", "contents",
                "addr/data: 4 8\n1 2 3\n");
        b.add("Wiring", "Probe", 700, 800);
        b.commit();
        base = Files.createTempFile(tmp, "base", ".circ").toFile();
        CircuitBuilder.save(f, base);
        JsonObject r = e.client.callObject("file.open", params("path", base.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
    }

    /** AND의 입력 0 자리의 y(원조 게이트 모양이 정한다). */
    int in0Y() {
        return port(id("AND Gate"), 1)[1];
    }

    int[] port(String id, int i) {
        for (JsonElement c : snap().getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (o.get("id").getAsString().equals(id)) {
                JsonArray loc = o.getAsJsonArray("ports").get(i).getAsJsonObject().getAsJsonArray("loc");
                return new int[] {loc.get(0).getAsInt(), loc.get(1).getAsInt()};
            }
        }
        throw new AssertionError("no " + id);
    }

    JsonObject snap() {
        return e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
    }

    JsonObject part(String name) {
        return part(name, null);
    }

    JsonObject part(String name, String label) {
        for (JsonElement c : snap().getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (o.get("name").getAsString().equals(name) && (label == null
                    || label.equals(o.getAsJsonObject("attrs").has("label") ? o.getAsJsonObject("attrs").get("label").getAsString() : null))) {
                return o;
            }
        }
        throw new AssertionError("no " + name + " " + label);
    }

    String id(String name) {
        return part(name).get("id").getAsString();
    }

    String id(String name, String label) {
        return part(name, label).get("id").getAsString();
    }

    List<JsonObject> parts(String name) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement c : snap().getAsJsonArray("components")) {
            if (c.getAsJsonObject().get("name").getAsString().equals(name)) {
                out.add(c.getAsJsonObject());
            }
        }
        return out;
    }

    /** 점 (x, y)를 지나는 선. */
    String wireAt(int x, int y) {
        for (JsonElement w : snap().getAsJsonArray("wires")) {
            JsonObject o = w.getAsJsonObject();
            JsonArray a = o.getAsJsonArray("a");
            JsonArray b = o.getAsJsonArray("b");
            int x0 = Math.min(a.get(0).getAsInt(), b.get(0).getAsInt()), x1 = Math.max(a.get(0).getAsInt(), b.get(0).getAsInt());
            int y0 = Math.min(a.get(1).getAsInt(), b.get(1).getAsInt()), y1 = Math.max(a.get(1).getAsInt(), b.get(1).getAsInt());
            if (x >= x0 && x <= x1 && y >= y0 && y <= y1) {
                return o.get("id").getAsString();
            }
        }
        throw new AssertionError("no wire at " + x + "," + y);
    }

    JsonObject call(String method, Object... kv) {
        Object[] all = new Object[kv.length + 4];
        all[0] = "fileId";
        all[1] = fileId;
        all[2] = "circuitId";
        all[3] = main;
        System.arraycopy(kv, 0, all, 4, kv.length);
        return e.client.callObject(method, params(all));
    }

    Client.Failure fail(String method, Object... kv) {
        Object[] all = new Object[kv.length + 4];
        all[0] = "fileId";
        all[1] = fileId;
        all[2] = "circuitId";
        all[3] = main;
        System.arraycopy(kv, 0, all, 4, kv.length);
        return e.client.fail(method, params(all));
    }

    static JsonArray ids(String... ids) {
        JsonArray a = new JsonArray();
        for (String s : ids) {
            a.add(s);
        }
        return a;
    }

    static JsonObject row(JsonObject table, String attr) {
        for (JsonElement r : table.getAsJsonArray("rows")) {
            if (r.getAsJsonObject().get("attr").getAsString().equals(attr)) {
                return r.getAsJsonObject();
            }
        }
        return null;
    }

    static List<String> attrs(JsonObject table) {
        List<String> out = new ArrayList<>();
        for (JsonElement r : table.getAsJsonArray("rows")) {
            out.add(r.getAsJsonObject().get("attr").getAsString());
        }
        return out;
    }

    static List<String> values(JsonObject row, String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement o : row.getAsJsonArray("options")) {
            out.add(o.getAsJsonObject().get(key).getAsString());
        }
        return out;
    }

    static JsonObject wrap(JsonArray options) {
        JsonObject o = new JsonObject();
        o.add("options", options);
        return o;
    }

    void undo() {
        e.client.call("edit.undo", params("fileId", fileId));
    }

    // ---- 속성 표 ----

    @Test
    void theAttributeTableIsTheOriginals() throws Exception {
        open();
        String and = id("AND Gate");
        call("edit.select", "ids", ids(and));
        JsonObject t = call("model.attributes");
        assertEquals("selection", t.get("target").getAsString());
        assertEquals("Selection: AND Gate", t.get("title").getAsString());
        assertTrue(t.get("editable").getAsBoolean());
        // 원조 GateAttributes의 차례와 편집기
        assertEquals(List.of("facing", "width", "size", "inputs", "out", "label", "labelfont", "negate0", "negate1"),
                attrs(t));
        JsonObject facing = row(t, "facing");
        assertEquals("option", facing.get("type").getAsString());
        assertEquals("east", facing.get("value").getAsString());
        assertEquals("East", facing.get("text").getAsString());
        assertEquals(List.of("north", "south", "east", "west"), values(facing, "value"));
        JsonObject width = row(t, "width");
        assertEquals("option", width.get("type").getAsString());
        assertEquals(32, width.getAsJsonArray("options").size());
        assertEquals("1", width.get("value").getAsString());
        assertEquals("text", row(t, "label").get("type").getAsString());
        assertEquals("font", row(t, "labelfont").get("type").getAsString());
        assertEquals("SansSerif plain 12", row(t, "labelfont").get("value").getAsString());
        assertEquals(List.of("Yes", "No"), values(row(t, "negate0"), "display"));
        // 빠른 속성 창(v1 등록표): 게이트 = 입력 수·폭·크기·방향·라벨, 원조 숨은 키
        JsonObject q = t.getAsJsonObject("quick");
        assertNotNull(q);
        assertEquals("[\"inputs\",\"width\",\"size\",\"facing\",\"label\"]", q.getAsJsonArray("attrs").toString());
        assertTrue(q.get("rotate").getAsBoolean());
        assertTrue(q.get("label").getAsBoolean());
        assertEquals("0–9", q.getAsJsonArray("hints").get(0).getAsJsonObject().get("keys").getAsString());
        assertEquals("inputs", q.getAsJsonArray("hints").get(0).getAsJsonObject().get("attr").getAsString());

        // 같은 종류 둘: "× 2", 값이 다르면 빈칸(mixed)
        call("edit.addComponent", "lib", "Gates", "name", "AND Gate", "loc", new int[] {300, 900}, "attrs",
                params("inputs", "3"));
        String and2 = parts("AND Gate").stream().filter(o -> !o.get("id").getAsString().equals(and)).findFirst()
                .get().get("id").getAsString();
        call("edit.select", "ids", ids(and, and2));
        t = call("model.attributes");
        assertEquals("Selection: AND Gate × 2", t.get("title").getAsString());
        assertTrue(row(t, "inputs").get("value").isJsonNull());
        assertTrue(row(t, "inputs").get("mixed").getAsBoolean());
        assertEquals("", row(t, "inputs").get("text").getAsString());
        assertEquals("east", row(t, "facing").get("value").getAsString());
        assertEquals(2, t.getAsJsonObject("quick").get("count").getAsInt());
        // 여러 종류: 모두가 가진 것만, 선은 빼고, 빠른 속성 창 없음
        String pin = id("Pin", "A");
        call("edit.select", "ids", ids(and, pin, wireAt(150, in0Y())));
        t = call("model.attributes");
        assertEquals("Selection: Various items × 2", t.get("title").getAsString());
        assertEquals(List.of("facing", "width", "label", "labelfont"), attrs(t));
        assertFalse(t.has("quick"));
        // 선만: 선의 속성(읽기 전용)
        call("edit.select", "ids", ids(wireAt(150, in0Y())));
        t = call("model.attributes");
        assertEquals("Selection: Wire", t.get("title").getAsString());
        for (JsonElement r : t.getAsJsonArray("rows")) {
            assertTrue(r.getAsJsonObject().get("readOnly").getAsBoolean(), r.toString());
        }
        // 아무것도: 회로 속성
        call("edit.select", "ids", new JsonArray());
        t = call("model.attributes");
        assertEquals("circuit", t.get("target").getAsString());
        assertEquals("Circuit: main", t.get("title").getAsString());
        assertEquals(List.of("circuit", "clabel", "clabelup", "clabelfont"), attrs(t));
        assertEquals("main", row(t, "circuit").get("value").getAsString());
        assertEquals(t.toString(), call("model.attributes", "circuit", true).toString());
        // 상수 값: 16진 수
        JsonObject k = call("model.attributes", "ids", ids(id("Constant")));
        assertEquals("number", row(k, "value").get("type").getAsString());
        assertEquals(16, row(k, "value").get("radix").getAsInt());
        assertEquals("0x5", row(k, "value").get("text").getAsString());
        // 스플리터 bitN: 원조 목록의 항목은 보기 객체, 값은 그 차례(원조 표와 같다)
        JsonObject sp = call("model.attributes", "ids", ids(id("Splitter")));
        JsonObject bit0 = row(sp, "bit0");
        assertEquals("option", bit0.get("type").getAsString());
        assertEquals(List.of("none", "0", "1"), values(bit0, "value"));
        assertEquals("0", bit0.get("value").getAsString());
        // ROM 내용: 16진 편집기(표에는 내용을 싣지 않는다)
        JsonObject rom = call("model.attributes", "ids", ids(id("ROM")));
        assertEquals("contents", row(rom, "contents").get("type").getAsString());
        assertTrue(row(rom, "contents").get("value").isJsonNull());
        // 든 도구: 원조 AttrTableToolModel
        JsonObject tool = e.client.callObject("model.attributes", params("fileId", fileId, "lib", "Gates", "name",
                "OR Gate"));
        assertEquals("tool", tool.get("target").getAsString());
        assertEquals("Tool: OR Gate", tool.get("title").getAsString());
        assertEquals("5", row(tool, "inputs").get("value").getAsString());
        JsonObject text = e.client.callObject("model.attributes", params("fileId", fileId, "lib", "Base", "name",
                "Text Tool"));
        assertEquals(List.of("text", "font", "halign", "valign"), attrs(text));
    }


    /**
     * 든 부품만의 값(N-17, D-158 18): model.attributes의 attrs는 그 값을 얹은 표를 읽기만으로 준다. 줄의 목록도 그 값을 따르고
     * (원조 GateAttributeList: 입력 3이면 Negate 1 (Top), Negate 2, Negate 3 (Bottom)), 도구와 파일은 그대로다.
     */
    @Test
    void aHeldPartsOwnValuesMakeItsTableAndChangeNothing() throws Exception {
        open();
        JsonObject attrs = new JsonObject();
        attrs.addProperty("inputs", "3");
        JsonObject held = e.client.callObject("model.attributes", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate", "attrs", attrs));
        assertEquals("3", row(held, "inputs").get("value").getAsString());
        List<String> negates = new java.util.ArrayList<>();
        for (JsonElement x : held.getAsJsonArray("rows")) {
            JsonObject o = x.getAsJsonObject();
            if (o.get("attr").getAsString().startsWith("negate")) {
                negates.add(o.get("display").getAsString());
            }
        }
        assertEquals(List.of("Negate 1 (Top)", "Negate 2", "Negate 3 (Bottom)"), negates);
        JsonObject tool = e.client.callObject("model.attributes", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate"));
        assertEquals("5", row(tool, "inputs").get("value").getAsString());
        assertFalse(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean());
        // a value the attribute does not take: refused as edit.setToolAttr refuses it
        attrs.addProperty("inputs", "99");
        Client.Failure f = fail("model.attributes", "lib", "Gates", "name", "AND Gate", "attrs", attrs);
        assertEquals(-32602, f.code);
    }

    @Test
    void aBadValueIsRefusedByTheOriginalParse() throws Exception {
        open();
        String k = id("Constant");
        Client.Failure f = fail("edit.setAttr", "ids", ids(k), "attr", "value", "value", "0xZZ");
        assertEquals(-32602, f.code);
        assertEquals("badValue", f.reason());
        assertEquals("value", f.error.getAsJsonObject("data").get("attr").getAsString());
        assertEquals("0xZZ", f.error.getAsJsonObject("data").get("value").getAsString());
        f = fail("edit.setAttr", "ids", ids(id("Register")), "attr", "width", "value", "wide");
        assertEquals("badValue", f.reason());
        // 원조 IntegerRange(게이트 입력 수 2..32)도 원조 parse가 거절한다
        f = fail("edit.setAttr", "ids", ids(id("AND Gate")), "attr", "inputs", "value", "99");
        assertEquals("badValue", f.reason());
        // 거절된 뒤 모델은 그대로(되돌리기 기록에도 없다)
        assertEquals("0x5", part("Constant").getAsJsonObject("attrs").get("value").getAsString());
        assertEquals("nothing", e.client.callObject("edit.undo", params("fileId", fileId)).get("outcome").getAsString(),
                "nothing to undo: the refused edit left no step");
    }

    /**
     * 레지스터의 Data Bits를 표에서 바꾸면(edit.setAttr) model.changed가 오고, 저장한 .circ는 같은 파일에 원조 속성 표의
     * 동작(원조 SetAttributeAction이 하는 CircuitMutation.set)을 한 뒤 원조 저장 코드로 저장한 것과 같다.
     */
    @Test
    void registerDataBitsSaveAsTheOriginalTableDoes() throws Exception {
        open();
        String reg = id("Register");
        int mark = e.client.mark();
        assertTrue(call("edit.setAttr", "ids", ids(reg), "attr", "width", "value", "16").get("changed").getAsBoolean());
        JsonObject ch = e.client.awaitNotificationAfter(mark, "model.changed",
                c -> c.get("circuitId").getAsString().equals(main));
        boolean seen = false;
        for (JsonElement a : ch.getAsJsonArray("added")) {
            if (a.getAsJsonObject().get("id").getAsString().equals(reg)) {
                assertEquals("16", a.getAsJsonObject().getAsJsonObject("attrs").get("width").getAsString());
                seen = true;
            }
        }
        assertTrue(seen, ch.toString());
        File mine = new File(tmp.toFile(), "engine.circ");
        e.client.call("file.save", params("fileId", fileId, "path", mine.getPath()));

        LogisimFile f = new Loader(null).openLogisimFile(base);
        Circuit c = f.getMainCircuit();
        Component r = null;
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals("Register")) {
                r = x;
            }
        }
        @SuppressWarnings("unchecked")
        Attribute<Object> width = (Attribute<Object>) r.getAttributeSet().getAttribute("width");
        CircuitMutation m = new CircuitMutation(c);
        m.set(r, width, width.parse("16"));
        m.execute();
        File theirs = new File(tmp.toFile(), "original.circ");
        CircuitBuilder.save(f, theirs);
        assertEquals(CircNormalizer.normalize(Files.readString(theirs.toPath(), StandardCharsets.UTF_8)),
                CircNormalizer.normalize(Files.readString(mine.toPath(), StandardCharsets.UTF_8)));
    }

    // ---- 우클릭 메뉴의 사실 ----

    @Test
    void menuFactsSaySummaryPortSelectionAndOrder() throws Exception {
        open();
        String and = id("AND Gate");
        // 게이트 몸체: 이름 · 입력 수 · 비트
        JsonObject m = call("model.menu", "at", new int[] {AND_X - 20, AND_Y});
        assertEquals("part", m.get("kind").getAsString());
        assertEquals(and, m.get("id").getAsString());
        assertEquals("AND #1 · 2 inputs · 1 bit", m.get("summary").getAsString());
        JsonObject p = m.getAsJsonObject("part");
        assertTrue(p.get("gate").getAsBoolean());
        assertEquals(5, p.getAsJsonArray("swaps").size());
        assertEquals(List.of("30", "50", "70"), values(wrap(p.getAsJsonObject("options").getAsJsonArray("size")), "value"));
        // 입력 포트 5px 안
        int[] in0 = port(and, 1);
        m = call("model.menu", "at", new int[] {in0[0] + 3, in0[1]}, "id", and);
        JsonObject port = m.getAsJsonObject("part").getAsJsonObject("port");
        assertEquals(1, port.get("i").getAsInt());
        assertEquals("in", port.get("dir").getAsString());
        assertEquals("negate0", port.get("negate").getAsString());
        assertFalse(port.get("negated").getAsBoolean());
        assertTrue(m.get("summary").getAsString().startsWith("AND #1 · input "), m.get("summary").getAsString());
        // 선: 넷 이름 · 폭
        m = call("model.menu", "at", new int[] {150, in0Y()});
        assertEquals("wire", m.get("kind").getAsString());
        assertEquals("Net A · 1 bit", m.get("summary").getAsString());
        assertEquals("A", m.getAsJsonObject("wire").get("net").getAsString());
        // 빈 곳: 회로 이름, 프로브 목록
        m = call("model.menu", "at", new int[] {1500, 1500});
        assertEquals("empty", m.get("kind").getAsString());
        assertEquals("Empty spot · main", m.get("summary").getAsString());
        assertEquals(1, m.getAsJsonArray("probes").size());
        // 터널: 같은 이름 둘, 위→아래
        m = call("model.menu", "at", new int[] {700, 560}, "id", id("Tunnel"));
        assertEquals(2, m.getAsJsonObject("part").getAsJsonObject("tunnel").getAsJsonArray("same").size());
        // 레지스터: PC 표시 가능
        m = call("model.menu", "at", new int[] {290, 400}, "id", id("Register"));
        assertFalse(m.getAsJsonObject("part").get("pcMarked").getAsBoolean());
        // 스플리터: 원조 Distribute 항목
        m = call("model.menu", "at", new int[] {300, 700}, "id", id("Splitter"));
        JsonArray orig = m.getAsJsonObject("part").getAsJsonArray("original");
        assertEquals("Distribute Ascending", orig.get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("Distribute Descending", orig.get(1).getAsJsonObject().get("text").getAsString());
        // 물음은 모델을 바꾸지 않는다
        assertFalse(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean());
        // 여러 개: "2 components", 고른 차례
        String pinA = id("Pin", "A");
        String pinB = id("Pin", "B");
        call("edit.select", "ids", ids(pinB));
        call("edit.select", "ids", ids(pinA), "add", true);
        m = call("model.menu", "at", new int[] {1500, 1500});
        assertEquals("many", m.get("kind").getAsString());
        assertEquals("2 components", m.get("summary").getAsString());
        assertEquals("[\"" + pinB + "\",\"" + pinA + "\"]", m.getAsJsonObject("selection").getAsJsonArray("ids").toString());
        assertTrue(m.getAsJsonObject("selection").get("ordered").getAsBoolean());
        assertTrue(m.getAsJsonObject("common").get("label").getAsBoolean());
        // 사각형으로 한꺼번에: 차례를 모른다
        call("edit.select", "ids", new JsonArray());
        call("edit.select", "rect", new int[] {0, 0, 2000, 2000});
        m = call("model.menu", "at", new int[] {1500, 1500});
        assertFalse(m.getAsJsonObject("selection").get("ordered").getAsBoolean());
    }

    // ---- 메뉴의 편집 의도: 되돌리기 한 단계씩 ----

    @Test
    void labelsAttachAndSwapAreOneStepEach() throws Exception {
        open();
        String a = id("Pin", "A");
        String b = id("Pin", "B");
        assertTrue(call("edit.labels", "ids", ids(a, b), "labels", ids("x0", "x1")).get("changed").getAsBoolean());
        assertEquals("x0", part("Pin", "x0").getAsJsonObject("attrs").get("label").getAsString());
        assertEquals("same", call("edit.labels", "ids", ids(a), "labels", ids("x0")).get("outcome").getAsString());
        assertEquals(-32602, fail("edit.labels", "ids", ids(a, b), "labels", ids("x0")).code);
        undo();
        assertNotNull(part("Pin", "A"));
        assertNotNull(part("Pin", "B"));

        // Attach to <port> ▸ Tunnel: 포트 자리에 바깥을 보는 터널, 포트 이름 라벨
        String and = id("AND Gate");
        int tunnels = parts("Tunnel").size();
        JsonObject r = call("edit.attach", "id", and, "port", 0, "what", "tunnel");
        assertTrue(r.get("changed").getAsBoolean(), "a tunnel on the output's point: the wire there may meet it (v1)");
        undo();
        String reg = id("Register");
        r = call("edit.attach", "id", reg, "port", 3, "what", "pin");
        assertTrue(r.get("changed").getAsBoolean(), r.toString());
        assertEquals(parts("Pin").size(), 4);
        undo();
        assertEquals(parts("Pin").size(), 3);
        assertEquals(tunnels, parts("Tunnel").size());
        assertEquals(-32602, fail("edit.attach", "id", reg, "port", 0, "what", "constant").code); // 출력(Q)에 상수는 없다

        // Change Gate To ▸ NOR: 입력 자리를 지키고 출력 선을 늘인다
        r = call("edit.swapGate", "id", and, "to", "NOR Gate");
        assertTrue(r.get("changed").getAsBoolean(), r.toString());
        assertEquals(1, parts("NOR Gate").size());
        assertEquals(0, parts("AND Gate").size());
        undo();
        assertEquals(1, parts("AND Gate").size());
        assertEquals("same", call("edit.swapGate", "id", id("AND Gate"), "to", "AND Gate").get("outcome").getAsString());
    }

    @Test
    void wireItems() throws Exception {
        open();
        String w = wireAt(150, in0Y());
        int wires = snap().getAsJsonArray("wires").size();
        // Replace Wire with Tunnels…: 선 대신 같은 이름 터널 둘
        assertTrue(call("edit.wireToTunnels", "wire", w, "label", "a").get("changed").getAsBoolean());
        assertEquals(2, parts("Tunnel").stream().filter(o -> o.getAsJsonObject("attrs").get("label").getAsString()
                .equals("a")).count());
        assertEquals(-32602, fail("edit.wireToTunnels", "wire", wireAt(350, AND_Y), "label", "  ").code);
        undo();
        assertEquals(wires, snap().getAsJsonArray("wires").size());
        // Delete Net Wires
        assertTrue(call("edit.deleteNet", "wire", wireAt(350, AND_Y)).get("changed").getAsBoolean());
        assertEquals(wires - 1, snap().getAsJsonArray("wires").size());
        undo();
        // Attach Probe ▸ Binary: 선 옆 빈 자리에 프로브와 짧은 선, 라벨은 넷 이름
        JsonObject r = call("edit.probe", "wire", wireAt(150, in0Y()), "at",
                new int[] {150, in0Y()}, "radix", "2");
        assertTrue(r.get("changed").getAsBoolean(), r.toString());
        JsonObject probe = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        String added = r.get("id").getAsString();
        boolean found = false;
        for (JsonElement c : probe.getAsJsonArray("components")) {
            if (c.getAsJsonObject().get("id").getAsString().equals(added)) {
                assertEquals("A", c.getAsJsonObject().getAsJsonObject("attrs").get("label").getAsString());
                assertEquals("2", c.getAsJsonObject().getAsJsonObject("attrs").get("radix").getAsString());
                found = true;
            }
        }
        assertTrue(found);
        assertEquals(2, parts("Probe").size());
        // P 키(진법 없이): 1비트는 2진, 여러 비트는 16진
        int[] q = port(id("Register"), 1);
        r = call("edit.probe", "wire", wireAt(q[0] + 50, q[1]), "at", new int[] {q[0] + 50, q[1]});
        assertTrue(r.get("changed").getAsBoolean(), r.toString());
        assertEquals(3, parts("Probe").size());
        // Delete All Probes (n)
        assertTrue(call("edit.deleteProbes").get("changed").getAsBoolean());
        assertEquals(0, parts("Probe").size());
        undo();
        assertEquals(3, parts("Probe").size());
    }

    @Test
    void combiningWiresFollowsTheOrderChosen() throws Exception {
        open();
        String w1 = wireAt(150, in0Y());
        int[] q = port(id("Register"), 1);
        String w8 = wireAt(q[0] + 50, q[1]);
        JsonObject r = call("edit.combineBus", "ids", ids(w8, w1));
        assertTrue(r.get("changed").getAsBoolean(), r.toString());
        JsonObject s = null;
        for (JsonObject o : parts("Splitter")) {
            if (o.get("id").getAsString().equals(r.get("id").getAsString())) {
                s = o;
            }
        }
        assertNotNull(s);
        assertEquals("9", s.getAsJsonObject("attrs").get("incoming").getAsString());
        assertEquals("west", s.getAsJsonObject("attrs").get("facing").getAsString());
        undo();
        assertEquals(1, parts("Splitter").size());
    }

    @Test
    void splitterDistributeIsTheOriginalItem() throws Exception {
        open();
        String sp = id("Splitter");
        JsonObject before = part("Splitter").getAsJsonObject("attrs");
        assertEquals("disabled", call("edit.originalItem", "id", sp, "index", 0).get("outcome").getAsString());
        assertTrue(call("edit.originalItem", "id", sp, "index", 1).get("changed").getAsBoolean());
        JsonObject after = part("Splitter").getAsJsonObject("attrs");
        assertFalse(before.equals(after));
        assertEquals("1", after.get("bit0").getAsString());
        undo();
        assertEquals(before, part("Splitter").getAsJsonObject("attrs"));
        assertEquals(-32602, fail("edit.originalItem", "id", id("Register"), "index", 0).code);
    }

    // ---- RAM·ROM 내용 ----

    @Test
    void ramContentsAreTheSimulationsAndRomContentsAreUndoable() throws Exception {
        open();
        String ram = id("RAM");
        String rom = id("ROM");
        JsonObject r = call("mem.read", "componentId", rom, "from", 0, "count", 8);
        assertEquals("rom", r.get("kind").getAsString());
        assertEquals(16, r.get("total").getAsInt());
        assertEquals("[1,2,3,0,0,0,0,0]", r.getAsJsonArray("words").toString());
        // ROM: 원조 되돌리기(Edit ROM Contents), .circ에 저장
        assertTrue(call("edit.memContents", "id", rom, "addr", 4, "values", new int[] {255, 7}).get("changed")
                .getAsBoolean());
        assertEquals("[1,2,3,0,255,7,0,0]", call("mem.read", "componentId", rom, "count", 8).getAsJsonArray("words")
                .toString());
        assertTrue(part("ROM").getAsJsonObject("attrs").get("contents").getAsString().contains("ff 7"));
        assertTrue(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean());
        undo();
        assertEquals("[1,2,3,0,0,0,0,0]", call("mem.read", "componentId", rom, "count", 8).getAsJsonArray("words")
                .toString());
        assertEquals("badValue", fail("edit.memContents", "id", rom, "addr", 0, "values", new int[] {256}).reason());
        assertTrue(call("edit.memContents", "id", rom, "clear", true).get("changed").getAsBoolean());
        assertEquals("[0,0,0,0]", call("mem.read", "componentId", rom, "count", 4).getAsJsonArray("words").toString());
        undo();
        assertEquals("[1,2,3,0]", call("mem.read", "componentId", rom, "count", 4).getAsJsonArray("words").toString());
        // Save Image…, Load Image…(원조 HexFile)
        File img = new File(tmp.toFile(), "rom.txt");
        call("mem.saveImage", "componentId", rom, "file", img.getPath());
        assertTrue(Files.readString(img.toPath()).startsWith("v2.0 raw"), Files.readString(img.toPath()));
        Files.writeString(img.toPath(), "v2.0 raw\n9 8 7\n");
        assertTrue(call("edit.memContents", "id", rom, "file", img.getPath()).get("changed").getAsBoolean());
        assertEquals("[9,8,7,0]", call("mem.read", "componentId", rom, "count", 4).getAsJsonArray("words").toString());
        undo();
        // RAM: 시뮬레이션 상태(되돌리기·저장에 없음)
        boolean dirtyBefore = e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean();
        assertTrue(call("mem.write", "componentId", ram, "addr", 2, "values", new int[] {42}).get("changed")
                .getAsBoolean());
        assertEquals("[0,0,42,0]", call("mem.read", "componentId", ram, "count", 4).getAsJsonArray("words").toString());
        assertEquals(dirtyBefore, e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean());
        assertEquals(-32602, fail("mem.write", "componentId", rom, "addr", 0, "values", new int[] {1}).code);
        call("mem.loadImage", "componentId", ram, "file", img.getPath());
        assertEquals("[9,8,7,0]", call("mem.read", "componentId", ram, "count", 4).getAsJsonArray("words").toString());
        assertTrue(call("mem.clear", "componentId", ram).get("changed").getAsBoolean());
        assertFalse(call("mem.clear", "componentId", ram).get("changed").getAsBoolean());
        assertEquals(-32602, fail("mem.read", "componentId", ram, "count", 0).code);
    }
}
