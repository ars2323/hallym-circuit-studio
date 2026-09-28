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
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 회로 단위의 의도와 물음(N-11, D-153): Remove Circuit·차례·Set As Main·이름, Port Order·Auto Appearance(끊어질 연결
 * 확인), 파일 구조 알림(file.changed), 인스턴스 경로·핀 영향(model.instances, model.pinImpact), 서브회로 핀을 바꿔 끊긴
 * 연결(model.portImpact, v1 되살림).
 */
class CircuitsTest {
    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;
    String half;
    File path;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    /**
     * main 안에 half(입력 a·b, 출력 s·c; 기본 모양) 인스턴스 하나, 그 네 포트에 선. 그리고 쓰이지 않는 spare 회로.
     */
    void openBuilt() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit h = new Circuit("half");
        f.addCircuit(h);
        CircuitBuilder cb = new CircuitBuilder(f, h);
        cb.add("Wiring", "Pin", 100, 100, "label", "a");
        cb.add("Wiring", "Pin", 100, 200, "label", "b");
        cb.add("Wiring", "Pin", 400, 100, "facing", "west", "output", "true", "label", "s");
        cb.add("Wiring", "Pin", 400, 200, "facing", "west", "output", "true", "label", "c");
        cb.commit();
        f.addCircuit(new Circuit("spare"));
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component inst = b.addSubcircuit(h, 300, 300);
        for (int i = 0; i < inst.getEnds().size(); i++) {
            Location at = CircuitBuilder.port(inst, i);
            boolean west = at.getX() < inst.getLocation().getX();
            b.wire(at, Location.create(at.getX() + (west ? -30 : 30), at.getY()));
        }
        b.commit();
        path = tmp.resolve("built.circ").toFile();
        CircuitBuilder.save(f, path);
        JsonObject r = e.client.callObject("file.open", params("path", path.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        half = circuitNamed(r, "half");
    }

    static String circuitNamed(JsonObject opened, String name) {
        for (JsonElement c : opened.getAsJsonArray("circuits")) {
            if (c.getAsJsonObject().get("name").getAsString().equals(name)) {
                return c.getAsJsonObject().get("circuitId").getAsString();
            }
        }
        throw new AssertionError("no circuit " + name);
    }

    JsonObject edit(String method, Object... kv) {
        Object[] all = new Object[kv.length + 2];
        all[0] = "fileId";
        all[1] = fileId;
        System.arraycopy(kv, 0, all, 2, kv.length);
        return e.client.callObject(method, params(all));
    }

    JsonObject lastFileChanged(int mark) {
        List<JsonObject> n = e.client.notificationsAfter(mark, "file.changed");
        return n.isEmpty() ? null : n.get(n.size() - 1);
    }

    static List<String> names(JsonObject fileChanged) {
        List<String> out = new ArrayList<>();
        for (JsonElement c : fileChanged.getAsJsonArray("circuits")) {
            out.add(c.getAsJsonObject().get("name").getAsString());
        }
        return out;
    }

    void sync() {
        e.client.callObject("file.dirty", params("fileId", fileId)); // 알림은 응답 뒤: 다음 응답이 오면 앞의 알림은 왔다
    }

    @Test
    void addRemoveMoveRenameAndSetMainTellTheFileStructure() throws Exception {
        openBuilt();
        int m = e.client.mark();
        JsonObject r = edit("edit.createCircuit", "name", "alu");
        String alu = r.get("circuitId").getAsString();
        sync();
        JsonObject fc = lastFileChanged(m);
        assertNotNull(fc, "a new circuit is told");
        assertEquals(List.of("main", "half", "spare", "alu"), names(fc));
        assertTrue(fc.get("dirty").getAsBoolean());

        // 차례: alu를 맨 앞으로(원조 moveCircuit), 같은 자리는 바꾸지 않음, 밖은 -32602
        m = e.client.mark();
        assertTrue(edit("edit.moveCircuit", "circuitId", alu, "to", 0).get("changed").getAsBoolean());
        sync();
        assertEquals(List.of("alu", "main", "half", "spare"), names(lastFileChanged(m)));
        assertEquals("same", edit("edit.moveCircuit", "circuitId", alu, "to", 0).get("outcome").getAsString());
        assertEquals(-32602, e.client.fail("edit.moveCircuit", params("fileId", fileId, "circuitId", alu, "to", 9))
                .code);

        // 이름: 회로 속성 circuit(원조 AttrTableCircuitModel), 알림의 이름도 바뀐다
        m = e.client.mark();
        edit("edit.setCircuitAttr", "circuitId", alu, "attr", "circuit", "value", "alu32");
        sync();
        assertEquals(List.of("alu32", "main", "half", "spare"), names(lastFileChanged(m)));

        // 주 회로
        m = e.client.mark();
        edit("edit.setMainCircuit", "circuitId", half);
        sync();
        assertEquals(half, lastFileChanged(m).get("main").getAsString());

        // 지우기: 쓰이는 회로는 거절(inUse), 쓰이지 않는 회로는 지우고 알린다, 되돌리면 돌아온다
        Client.Failure used = e.client.fail("edit.deleteCircuit", params("fileId", fileId, "circuitId", half));
        assertEquals(3, used.code);
        assertEquals("inUse", used.reason());
        m = e.client.mark();
        assertTrue(edit("edit.deleteCircuit", "circuitId", alu).get("changed").getAsBoolean());
        sync();
        assertEquals(List.of("main", "half", "spare"), names(lastFileChanged(m)));
        m = e.client.mark();
        edit("edit.undo");
        sync();
        assertEquals(List.of("alu32", "main", "half", "spare"), names(lastFileChanged(m)));

        // 알림이 없는 편집(선 하나)은 file.changed를 보내지 않는다
        m = e.client.mark();
        edit("edit.addWire", "circuitId", main, "points", new Object[] {new int[] {600, 600}, new int[] {650, 600}});
        sync();
        assertNull(lastFileChanged(m), "no structure change, no file.changed");
    }

    @Test
    void theLastCircuitIsNotRemoved() throws Exception {
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        Client.Failure f = e.client.fail("edit.deleteCircuit", params("fileId", fileId, "circuitId",
                r.get("main").getAsString()));
        assertEquals(3, f.code);
        assertEquals("lastCircuit", f.reason());
    }

    @Test
    void portOrderAsksBeforeItBreaksConnectionsAndIsOneUndoStep() throws Exception {
        openBuilt();
        JsonObject ports = edit("model.ports", "circuitId", half);
        JsonObject sides = ports.getAsJsonObject("sides");
        assertEquals(2, sides.getAsJsonArray("west").size());
        assertEquals("a", sides.getAsJsonArray("west").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("s", sides.getAsJsonArray("east").get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(ports.get("default").getAsBoolean());
        assertEquals(1, ports.get("instances").getAsInt());

        JsonObject order = new JsonObject();
        JsonArray west = new JsonArray();
        west.add("b");
        west.add("a");
        order.add("west", west);
        // 이어진 포트의 자리가 바뀐다: confirm:false면 바꾸지 않고 영향을 준다
        JsonObject ask = edit("edit.portOrder", "circuitId", half, "order", order, "confirm", false);
        assertFalse(ask.get("changed").getAsBoolean());
        assertEquals("needsConfirm", ask.get("outcome").getAsString());
        JsonObject impact = ask.getAsJsonObject("impact");
        assertEquals(1, impact.get("instances").getAsInt());
        assertTrue(impact.get("connections").getAsInt() >= 2, impact.toString());
        assertEquals("main › half #1", impact.getAsJsonArray("where").get(0).getAsString());
        assertTrue(edit("model.ports", "circuitId", half).get("default").getAsBoolean(), "nothing changed");

        // 확인: 바꾸고, 인스턴스(main)의 포트가 model.changed로 온다
        int m = e.client.mark();
        JsonObject done = edit("edit.portOrder", "circuitId", half, "order", order, "confirm", true);
        assertTrue(done.get("changed").getAsBoolean());
        sync();
        assertTrue(e.client.notificationsAfter(m, "model.changed").stream()
                .anyMatch(c -> c.get("circuitId").getAsString().equals(main)), "the instance's ports changed");
        JsonObject after = edit("model.ports", "circuitId", half);
        assertFalse(after.get("default").getAsBoolean());
        assertEquals("b", after.getAsJsonObject("sides").getAsJsonArray("west").get(0).getAsJsonObject().get("name")
                .getAsString());
        // 번호로도: 지금 차례의 1, 0
        JsonObject byIndex = new JsonObject();
        JsonArray idx = new JsonArray();
        idx.add(1);
        idx.add(0);
        byIndex.add("west", idx);
        assertTrue(edit("edit.portOrder", "circuitId", half, "order", byIndex).get("changed").getAsBoolean());
        assertEquals("a", edit("model.ports", "circuitId", half).getAsJsonObject("sides").getAsJsonArray("west").get(0)
                .getAsJsonObject().get("name").getAsString());
        edit("edit.undo");
        edit("edit.undo");
        assertTrue(edit("model.ports", "circuitId", half).get("default").getAsBoolean(), "two undos: default again");

        // 틀린 차례: 그 변의 포트가 아니거나 모자람
        JsonObject bad = new JsonObject();
        JsonArray w = new JsonArray();
        w.add("a");
        w.add("zz");
        bad.add("west", w);
        assertEquals(-32602, e.client.fail("edit.portOrder", params("fileId", fileId, "circuitId", half, "order", bad))
                .code);
        JsonObject side = new JsonObject();
        side.add("up", new JsonArray());
        assertEquals(-32602, e.client.fail("edit.portOrder", params("fileId", fileId, "circuitId", half, "order", side))
                .code);
    }

    @Test
    void autoAppearanceKeepsThePortsInPlaceWhenNothingMoves() throws Exception {
        openBuilt();
        // v1 표준 모양은 기본 모양과 포트 자리가 달라 이어진 포트가 움직인다: 먼저 묻는다(바꾸지 않음)
        JsonObject r = edit("edit.autoAppearance", "circuitId", half, "confirm", false);
        assertEquals("needsConfirm", r.get("outcome").getAsString(), r.toString());
        assertEquals(1, r.getAsJsonObject("impact").get("instances").getAsInt());
        assertTrue(edit("model.ports", "circuitId", half).get("default").getAsBoolean());
        r = edit("edit.autoAppearance", "circuitId", half);
        assertTrue(r.get("changed").getAsBoolean(), "confirm is true unless said: " + r);
        assertFalse(edit("model.ports", "circuitId", half).get("default").getAsBoolean());
        // 같은 모양을 다시: 끊을 것이 없어 묻지 않고 바꾼다(원조 모양 동작 한 번)
        JsonObject again = edit("edit.autoAppearance", "circuitId", half, "confirm", false);
        assertTrue(again.get("changed").getAsBoolean(), again.toString());
        assertFalse(again.has("impact"), again.toString());
        edit("edit.undo");
        edit("edit.undo");
        assertTrue(edit("model.ports", "circuitId", half).get("default").getAsBoolean(), "each one undo step");
        // 차례를 바꾼 뒤의 Auto Appearance는 지금 차례를 지킨다(v1 AutoAppearance.sides)
        edit("edit.portOrder", "circuitId", half, "order", JsonOrder.west("b", "a"));
        assertTrue(edit("edit.autoAppearance", "circuitId", half).get("changed").getAsBoolean());
        assertEquals("b", edit("model.ports", "circuitId", half).getAsJsonObject("sides").getAsJsonArray("west")
                .get(0).getAsJsonObject().get("name").getAsString());
        // 빈 회로: 포트가 없다
        String spare = circuitNamed(e.client.callObject("file.open", params("path", path.getPath())), "spare");
        assertEquals("noPorts", edit("edit.autoAppearance", "circuitId", spare).get("outcome").getAsString());
    }

    @Test
    void instancePathsFromMainAndThePinImpact() throws Exception {
        openBuilt();
        JsonObject i = edit("model.instances", "circuitId", half);
        assertEquals(main, i.get("main").getAsString());
        JsonArray paths = i.getAsJsonArray("paths");
        assertEquals(1, paths.size());
        assertEquals("main › half", paths.get(0).getAsJsonObject().get("text").getAsString());
        assertEquals(1, paths.get(0).getAsJsonObject().getAsJsonArray("ids").size());
        assertEquals(1, i.get("instances").getAsInt());
        assertEquals(4, i.get("connected").getAsInt());
        assertTrue(i.get("default").getAsBoolean());
        // main 자신: 경로 없음
        assertEquals(0, edit("model.instances", "circuitId", main).getAsJsonArray("paths").size());
        // 핀 a를 지우거나 옮기면 끊길 수 있는 연결
        JsonObject snap = edit("model.circuit", "circuitId", half);
        String pinA = null;
        for (JsonElement c : snap.getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if ("a".equals(o.getAsJsonObject("attrs").has("label") ? o.getAsJsonObject("attrs").get("label")
                    .getAsString() : null)) {
                pinA = o.get("id").getAsString();
            }
        }
        JsonObject pi = edit("model.pinImpact", "circuitId", half, "ids", new Object[] {pinA});
        assertEquals(1, pi.get("connections").getAsInt());
        assertEquals(1, pi.get("instances").getAsInt());
    }

    @Test
    void deletingAPinTellsTheBrokenInstanceConnection() throws Exception {
        openBuilt();
        JsonObject snap = edit("model.circuit", "circuitId", half);
        String pinB = null;
        for (JsonElement c : snap.getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            JsonObject a = o.getAsJsonObject("attrs");
            if (a.has("label") && a.get("label").getAsString().equals("b")) {
                pinB = o.get("id").getAsString();
            }
        }
        // half를 보고 있다(원조: 지금 회로가 주 회로가 아니고 인스턴스가 있을 때)
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", half));
        int m = e.client.mark();
        edit("edit.delete", "circuitId", half, "ids", new Object[] {pinB});
        sync();
        List<JsonObject> impacts = e.client.notificationsAfter(m, "model.portImpact");
        assertEquals(1, impacts.size(), "one impact notice");
        JsonObject imp = impacts.get(0);
        assertEquals(half, imp.get("circuitId").getAsString());
        assertEquals("half", imp.get("name").getAsString());
        assertTrue(imp.get("broken").getAsInt() >= 1, imp.toString());
        // main을 보며 한 편집(주 회로)은 알리지 않는다
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        m = e.client.mark();
        edit("edit.addWire", "circuitId", main, "points", new Object[] {new int[] {600, 600}, new int[] {650, 600}});
        sync();
        assertTrue(e.client.notificationsAfter(m, "model.portImpact").isEmpty());
    }

    /**
     * 옮겨져 떨어진 포트는 v1처럼 새 자리에서 옛 선 끝까지 선을 이어 되살린다(따로 되돌리는 한 단계): 입력 a·b 가운데
     * b만 이어져 있고, a를 지우면 기본 모양이 줄어 b의 포트가 a의 자리로 올라간다.
     */
    @Test
    void aMovedPortIsWiredBackAsItsOwnUndoStep() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit two = new Circuit("two");
        f.addCircuit(two);
        CircuitBuilder cb = new CircuitBuilder(f, two);
        cb.add("Wiring", "Pin", 100, 100, "label", "a");
        cb.add("Wiring", "Pin", 100, 200, "label", "b");
        cb.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "y");
        cb.commit();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component inst = b.addSubcircuit(two, 300, 300);
        Location lowerWest = null;
        for (int i = 0; i < inst.getEnds().size(); i++) {
            Location at = CircuitBuilder.port(inst, i);
            if (at.getX() < inst.getLocation().getX() && (lowerWest == null || at.getY() > lowerWest.getY())) {
                lowerWest = at;
            }
        }
        b.wire(lowerWest, Location.create(lowerWest.getX() - 40, lowerWest.getY()));
        b.commit();
        path = tmp.resolve("two.circ").toFile();
        CircuitBuilder.save(f, path);
        JsonObject r = e.client.callObject("file.open", params("path", path.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        String twoId = circuitNamed(r, "two");
        String pinA = null;
        for (JsonElement c : edit("model.circuit", "circuitId", twoId).getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (o.getAsJsonObject("attrs").has("label") && o.getAsJsonObject("attrs").get("label").getAsString()
                    .equals("a")) {
                pinA = o.get("id").getAsString();
            }
        }
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", twoId));
        int wiresBefore = edit("model.circuit", "circuitId", main).getAsJsonArray("wires").size();
        int m = e.client.mark();
        edit("edit.delete", "circuitId", twoId, "ids", new Object[] {pinA});
        sync();
        List<JsonObject> impacts = e.client.notificationsAfter(m, "model.portImpact");
        assertEquals(1, impacts.size(), "one notice");
        assertEquals(1, impacts.get(0).get("broken").getAsInt(), impacts.toString());
        assertEquals(1, impacts.get(0).get("kept").getAsInt(), "wired back: " + impacts);
        assertTrue(edit("model.circuit", "circuitId", main).getAsJsonArray("wires").size() > wiresBefore,
                "a wire from the new port to the old wire end");
        // 되살림은 따로 되돌린다: 한 번 되돌리면 선만 빠지고 핀은 지운 그대로
        edit("edit.undo");
        assertEquals(wiresBefore, edit("model.circuit", "circuitId", main).getAsJsonArray("wires").size());
        assertEquals(2, edit("model.circuit", "circuitId", twoId).getAsJsonArray("components").size());
    }

    @Test
    void savingWritesWhatTheSwingAppWrites() throws Exception {
        openBuilt();
        edit("edit.portOrder", "circuitId", half, "order", JsonOrder.west("b", "a"));
        File out = tmp.resolve("after.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", out.getPath()));
        String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("<appear>"), "a user appearance is saved as the original's <appear>");
        assertTrue(text.contains("circ-port"), text);
        // 원조 로더로 다시 열린다
        LogisimFile again = new Loader(null).openLogisimFile(out);
        assertFalse(again.getCircuit("half").getAppearance().isDefaultAppearance());
    }

    /** order 객체를 짓는 작은 도우미. */
    static final class JsonOrder {
        static JsonObject west(String... names) {
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            for (String n : names) {
                a.add(n);
            }
            o.add("west", a);
            return o;
        }
    }
}
