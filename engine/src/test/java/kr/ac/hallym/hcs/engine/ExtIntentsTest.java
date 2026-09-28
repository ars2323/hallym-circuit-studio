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
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * edit.signalGroup·edit.areaMemo(N-15, D-151): v1 E-04·E-08의 동작 그대로 hcs:ext만 바꾸고, 되돌리기 한 단계, 스냅숏과
 * model.changed에 그룹·메모가 실리고, 저장하면 회로 부분은 그대로다.
 */
class ExtIntentsTest {
    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;
    File base;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    /** A → NOT → Y(선 셋), 그리고 control 서브회로(출력 핀 RegWrite)의 인스턴스와 그 출력 선. */
    void openBuilt(boolean readOnly) throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit ctl = new Circuit("control");
        f.addCircuit(ctl);
        CircuitBuilder cb = new CircuitBuilder(f, ctl);
        cb.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "RegWrite");
        cb.add("Wiring", "Constant", 200, 100);
        cb.wire(Location.create(200, 100), Location.create(300, 100));
        cb.commit();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.add("Wiring", "Pin", 100, 100, "label", "A");
        Component not = b.add("Gates", "NOT Gate", 200, 100);
        b.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "Y");
        b.wire(Location.create(100, 100), Location.create(170, 100));
        b.wire(Location.create(200, 100), Location.create(250, 100));
        b.wire(Location.create(250, 100), Location.create(300, 100));
        Component inst = b.addSubcircuit(ctl, 100, 300);
        Location out = CircuitBuilder.port(inst, 0);
        b.wire(out, Location.create(out.getX() + 60, out.getY()));
        b.commit();
        assertNotNull(not);
        base = Files.createTempFile(tmp, "base", ".circ").toFile();
        CircuitBuilder.save(f, base);
        JsonObject r = e.client.callObject("file.open", params("path", base.getPath(), "readOnly", readOnly));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
    }

    JsonObject snap() {
        return e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
    }

    /** 점 (x, y)를 지나는 선(가로·세로). */
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
        throw new AssertionError("no wire at " + x + "," + y + " in " + snap().getAsJsonArray("wires"));
    }

    String netOfWire(JsonObject snapOrChange, String wire) {
        for (JsonElement n : snapOrChange.getAsJsonArray("nets")) {
            for (JsonElement w : n.getAsJsonObject().getAsJsonArray("wires")) {
                if (w.getAsString().equals(wire)) {
                    return n.getAsJsonObject().get("id").getAsString();
                }
            }
        }
        return null;
    }

    static String group(JsonArray groups, String net) {
        if (groups == null) {
            return null;
        }
        for (JsonElement g : groups) {
            if (g.getAsJsonObject().get("net").getAsString().equals(net)) {
                return g.getAsJsonObject().get("group").getAsString()
                        + (g.getAsJsonObject().get("assigned").getAsBoolean() ? "" : " (auto)");
            }
        }
        return null;
    }

    JsonObject edit(String method, Object... kv) {
        Object[] all = new Object[kv.length + 4];
        all[0] = "fileId";
        all[1] = fileId;
        all[2] = "circuitId";
        all[3] = main;
        System.arraycopy(kv, 0, all, 4, kv.length);
        return e.client.callObject(method, params(all));
    }

    JsonObject changedAfter(int mark) {
        return e.client.awaitNotificationAfter(mark, "model.changed", c -> c.get("circuitId").getAsString().equals(main));
    }

    // ---- 신호 그룹 ----

    @Test
    void signalGroupIsOneUndoableStepAndRidesOnTheModel() throws Exception {
        openBuilt(false);
        String w = wireAt(225, 100);
        JsonObject s0 = snap();
        String net = netOfWire(s0, w);
        // control 서브회로의 출력은 정하지 않아도 Control(v1 E-04)
        String ctlWire = null;
        for (JsonElement x : s0.getAsJsonArray("wires")) {
            JsonObject o = x.getAsJsonObject();
            if (o.getAsJsonArray("a").get(1).getAsInt() != 100) {
                ctlWire = o.get("id").getAsString();
            }
        }
        assertEquals("control (auto)", group(s0.getAsJsonArray("groups"), netOfWire(s0, ctlWire)));
        assertEquals(null, group(s0.getAsJsonArray("groups"), net));

        int mark = e.client.mark();
        JsonObject r = edit("edit.signalGroup", "wire", w, "group", "data");
        assertTrue(r.get("changed").getAsBoolean());
        JsonObject ch = changedAfter(mark);
        assertEquals(0, ch.getAsJsonArray("added").size(), "no part changed");
        assertEquals(0, ch.getAsJsonArray("removed").size());
        assertEquals("data", group(ch.getAsJsonArray("groups"), netOfWire(ch, w)));
        assertTrue(ch.get("dirty").getAsBoolean());
        assertEquals("data", group(snap().getAsJsonArray("groups"), netOfWire(snap(), w)));
        // 같은 넷의 다른 선으로 물어도 같은 그룹이다(넷에 정한다)
        String same = wireAt(275, 100);
        assertEquals("same", edit("edit.signalGroup", "wire", same, "group", "data").get("outcome").getAsString());
        // 다른 그룹, 되돌리기 한 단계씩
        edit("edit.signalGroup", "wire", same, "group", "address");
        assertEquals("address", group(snap().getAsJsonArray("groups"), netOfWire(snap(), w)));
        mark = e.client.mark();
        e.client.call("edit.undo", params("fileId", fileId));
        assertEquals("data", group(changedAfter(mark).getAsJsonArray("groups"), net));
        e.client.call("edit.undo", params("fileId", fileId));
        assertEquals(null, group(snap().getAsJsonArray("groups"), net));
        e.client.call("edit.redo", params("fileId", fileId));
        assertEquals("data", group(snap().getAsJsonArray("groups"), net));
        // None: 학생이 정한 것을 뗀다(없으면 바뀐 것 없음)
        assertTrue(edit("edit.signalGroup", "wire", w).get("changed").getAsBoolean());
        assertEquals(null, group(snap().getAsJsonArray("groups"), net));
        assertFalse(edit("edit.signalGroup", "wire", w).get("changed").getAsBoolean());
        // control 출력도 학생이 바꿀 수 있다(정한 것이 먼저)
        edit("edit.signalGroup", "wire", ctlWire, "group", "address");
        assertEquals("address", group(snap().getAsJsonArray("groups"), netOfWire(snap(), ctlWire)));
    }

    @Test
    void signalGroupErrors() throws Exception {
        openBuilt(false);
        String w = wireAt(225, 100);
        assertEquals(-32602, e.client.fail("edit.signalGroup", params("fileId", fileId, "circuitId", main, "wire", w, "group", "clock")).code);
        String pin = snap().getAsJsonArray("components").get(0).getAsJsonObject().get("id").getAsString();
        assertEquals(-32602, e.client.fail("edit.signalGroup", params("fileId", fileId, "circuitId", main, "wire", pin, "group", "data")).code);
        assertEquals(1, e.client.fail("edit.signalGroup", params("fileId", fileId, "circuitId", main, "wire", "w999999", "group", "data")).code);
        e.client.call("file.close", params("fileId", fileId));
        openBuilt(true);
        Client.Failure ro = e.client.fail("edit.signalGroup", params("fileId", fileId, "circuitId", main, "wire", wireAt(225, 100), "group", "data"));
        assertEquals(3, ro.code);
        assertEquals("readOnly", ro.reason());
    }

    // ---- 영역 메모 ----

    static JsonObject memo(JsonArray memos, int i) {
        return memos.get(i).getAsJsonObject();
    }

    static String box(JsonObject m) {
        return m.get("x").getAsInt() + "," + m.get("y").getAsInt() + "," + m.get("w").getAsInt() + ","
                + m.get("h").getAsInt() + " c" + m.get("color").getAsInt() + " " + m.get("text").getAsString();
    }

    @Test
    void areaMemoAddEditFitDelete() throws Exception {
        openBuilt(false);
        JsonObject s0 = snap();
        assertFalse(s0.has("memos"), "no memos: not in the snapshot");
        String a = s0.getAsJsonArray("components").get(0).getAsJsonObject().get("id").getAsString();
        String w = wireAt(225, 100);
        // 고른 것의 둘레(여백 20, 격자에 맞춤), 색은 메모 수로(v1 Add Area Memo…)
        int mark = e.client.mark();
        JsonObject r = edit("edit.areaMemo", "at", new int[] {600, 600}, "ids", List.of(w), "text", "  IF  ");
        assertEquals("added", r.get("outcome").getAsString());
        JsonObject ch = changedAfter(mark);
        String ifBox = around(List.of(w), 600, 600);
        assertEquals(ifBox + " c0 IF", box(memo(ch.getAsJsonArray("memos"), 0)));
        assertTrue(ch.get("dirty").getAsBoolean());
        // 고른 것이 없으면 누른 자리에 기본 크기(200×120)
        edit("edit.areaMemo", "at", new int[] {705, 505}, "text", "ID");
        JsonArray ms = snap().getAsJsonArray("memos");
        assertEquals(2, ms.size());
        assertEquals("600,440,200,120 c1 ID", box(memo(ms, 1)));
        // 메모 안: 고치기(주지 않은 것은 그대로), 제자리
        assertEquals("edited", edit("edit.areaMemo", "at", new int[] {700, 500}, "text", "ID stage", "color", 5).get("outcome").getAsString());
        assertEquals("600,440,200,120 c5 ID stage", box(memo(snap().getAsJsonArray("memos"), 1)));
        assertEquals("same", edit("edit.areaMemo", "at", new int[] {700, 500}, "text", "ID stage").get("outcome").getAsString());
        // 자리·크기
        edit("edit.areaMemo", "at", new int[] {700, 500}, "bounds", new int[] {610, 450, 150, 90});
        assertEquals("610,450,150,90 c5 ID stage", box(memo(snap().getAsJsonArray("memos"), 1)));
        // 고른 것에 맞추기(Fit Area Memo to Selection)
        edit("edit.areaMemo", "at", new int[] {620, 460}, "ids", List.of(a));
        assertEquals(around(List.of(a), 620, 460) + " c5 ID stage", box(memo(snap().getAsJsonArray("memos"), 1)));
        // 겹치면 안쪽(작은) 메모: 둘레 메모 안에 누르면 그것
        assertEquals("added", edit("edit.areaMemo", "at", new int[] {350, 350}, "text", "outer", "bounds", new int[] {0, 0, 400, 400}).get("outcome").getAsString());
        JsonObject ifMemo = memo(snap().getAsJsonArray("memos"), 0);
        int[] inside = {ifMemo.get("x").getAsInt() + 5, ifMemo.get("y").getAsInt() + 5};
        edit("edit.areaMemo", "at", inside, "color", 3);
        assertEquals(ifBox + " c3 IF", box(memo(snap().getAsJsonArray("memos"), 0)));
        // 지우기, 되돌리기
        mark = e.client.mark();
        assertEquals("deleted", edit("edit.areaMemo", "at", inside, "delete", true).get("outcome").getAsString());
        assertEquals(2, changedAfter(mark).getAsJsonArray("memos").size());
        assertEquals("noMemo", edit("edit.areaMemo", "at", new int[] {3000, 3000}, "delete", true).get("outcome").getAsString());
        e.client.call("edit.undo", params("fileId", fileId));
        // v1 AreaMemos: 되돌린 메모는 끝에 다시 붙는다(그리기 차례만 다르다)
        JsonArray back = snap().getAsJsonArray("memos");
        assertEquals(3, back.size());
        assertEquals(ifBox + " c3 IF", box(memo(back, 2)));
    }

    /** v1 AreaMemos.around로 계산한 상자 "x,y,w,h"(같은 부품 객체로). */
    String around(List<String> ids, int x, int y) throws Exception {
        return e.onEngine(() -> {
            kr.ac.hallym.hcs.engine.doc.Doc d = e.engine.files().get(fileId);
            com.cburch.logisim.data.Bounds b = kr.ac.hallym.hcs.app.memo.AreaMemos.around(
                    d.components(d.circuit(main), ids), Location.create(x, y));
            return b.getX() + "," + b.getY() + "," + b.getWidth() + "," + b.getHeight();
        });
    }

    @Test
    void areaMemoErrors() throws Exception {
        openBuilt(false);
        int[] at = {600, 600};
        assertEquals(-32602, e.client.fail("edit.areaMemo", params("fileId", fileId, "circuitId", main, "at", at, "color", 12)).code);
        assertEquals(-32602, e.client.fail("edit.areaMemo", params("fileId", fileId, "circuitId", main, "at", at, "color", -1)).code);
        assertEquals(-32602, e.client.fail("edit.areaMemo", params("fileId", fileId, "circuitId", main, "at", at, "bounds", new int[] {0, 0, 10, 50})).code);
        assertEquals(-32602, e.client.fail("edit.areaMemo", params("fileId", fileId, "circuitId", main, "at", at, "bounds", new int[] {0, 0, 50})).code);
        assertEquals(-32602, e.client.fail("edit.areaMemo", params("fileId", fileId, "circuitId", main)).code);
        assertEquals(1, e.client.fail("edit.areaMemo", params("fileId", fileId, "circuitId", main, "at", at, "ids", List.of("k999999"))).code);
        assertFalse(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean(), "errors change nothing");
    }

    // ---- 저장: 회로 부분은 그대로, 확장 정보에만 ----

    @Test
    void savedFileKeepsTheCircuitsAndAddsTheExtension() throws Exception {
        openBuilt(false);
        File plain = new File(tmp.toFile(), "plain.circ");
        e.client.call("file.save", params("fileId", fileId, "path", plain.getPath()));
        edit("edit.signalGroup", "wire", wireAt(225, 100), "group", "control");
        edit("edit.areaMemo", "at", new int[] {600, 600}, "text", "메모 & <특수>", "color", 4);
        File ext = new File(tmp.toFile(), "ext.circ");
        e.client.call("file.save", params("fileId", fileId, "path", ext.getPath()));
        String withExt = new String(Files.readAllBytes(ext.toPath()), StandardCharsets.UTF_8);
        assertTrue(withExt.contains("<hcs:group") && withExt.contains("group=\"control\""), withExt);
        assertTrue(withExt.contains("<hcs:memo") && withExt.contains("color=\"4\""), withExt);
        String without = withExt.replaceAll("(?s)\\s*<hcs:ext.*?</hcs:ext>", "");
        String want = new String(Files.readAllBytes(plain.toPath()), StandardCharsets.UTF_8);
        assertEquals(CircNormalizer.normalize(want), CircNormalizer.normalize(without));
        // 다시 열면 그대로 읽힌다
        e.client.call("file.close", params("fileId", fileId));
        JsonObject r = e.client.callObject("file.open", params("path", ext.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        JsonObject s = snap();
        assertEquals("500,540,200,120 c4 메모 & <특수>", box(memo(s.getAsJsonArray("memos"), 0)));
        assertEquals("control", group(s.getAsJsonArray("groups"), netOfWire(s, wireAt(225, 100))));
    }
}
