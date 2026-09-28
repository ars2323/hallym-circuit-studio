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
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 모양 편집(edit.appearance, model.appearance*, N-11, D-153): 원조 모양 편집기의 도구·Edit 메뉴가 넘기는 동작이
 * 원조 AppearanceCanvas로 가서 되돌리기 한 단계씩이고, 포트·기준점은 늘 맨 위에 남고, 모양이 바뀌면 인스턴스의
 * 포트가 따라오고, 저장하면 원조 {@code <appear>}로 적힌다.
 */
class AppearanceEditTest {
    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;
    String sub;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit s = new Circuit("sub");
        f.addCircuit(s);
        CircuitBuilder cb = new CircuitBuilder(f, s);
        cb.add("Wiring", "Pin", 100, 100, "label", "a");
        cb.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "y");
        cb.commit();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component inst = b.addSubcircuit(s, 300, 300);
        assertNotNull(inst);
        b.commit();
        File path = tmp.resolve("app.circ").toFile();
        CircuitBuilder.save(f, path);
        JsonObject r = e.client.callObject("file.open", params("path", path.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        for (JsonElement c : r.getAsJsonArray("circuits")) {
            if (c.getAsJsonObject().get("name").getAsString().equals("sub")) {
                sub = c.getAsJsonObject().get("circuitId").getAsString();
            }
        }
    }

    @AfterEach
    void stop() {
        e.close();
    }

    JsonObject app() {
        return e.client.callObject("model.appearance", params("fileId", fileId, "circuitId", sub));
    }

    JsonObject op(String op, Object... kv) {
        Object[] all = new Object[kv.length + 6];
        all[0] = "fileId";
        all[1] = fileId;
        all[2] = "circuitId";
        all[3] = sub;
        all[4] = "op";
        all[5] = op;
        System.arraycopy(kv, 0, all, 6, kv.length);
        return e.client.callObject("edit.appearance", params(all));
    }

    static List<String> kinds(JsonObject a) {
        List<String> out = new ArrayList<>();
        for (JsonElement s : a.getAsJsonArray("shapes")) {
            out.add(s.getAsJsonObject().get("kind").getAsString());
        }
        return out;
    }

    static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsInt();
        }
        return out;
    }

    JsonObject shape(int i) {
        return app().getAsJsonArray("shapes").get(i).getAsJsonObject();
    }

    void undo() {
        e.client.callObject("edit.undo", params("fileId", fileId));
    }

    @Test
    void theDefaultAppearanceHasItsShapesPortsAndAnchorOnTop() {
        JsonObject a = app();
        assertTrue(a.get("default").getAsBoolean());
        assertTrue(a.get("editable").getAsBoolean());
        List<String> k = kinds(a);
        assertEquals("anchor", k.get(k.size() - 1), "the anchor is last (on top): " + k);
        assertTrue(k.contains("port"));
        JsonObject port = null;
        for (JsonElement s : a.getAsJsonArray("shapes")) {
            if (s.getAsJsonObject().get("kind").getAsString().equals("port")) {
                port = s.getAsJsonObject();
            }
        }
        assertNotNull(port);
        assertFalse(port.get("removable").getAsBoolean(), "ports are not removed (original canRemove)");
        assertTrue(port.getAsJsonObject("port").has("pin"));
    }

    @Test
    void drawingToolsAddUnderThePortsAndTheFirstEditEndsTheDefault() {
        int before = kinds(app()).size();
        JsonObject attrs = new JsonObject();
        attrs.addProperty("stroke-width", "3");
        attrs.addProperty("paintType", "both");
        attrs.addProperty("fill", "#ff0000");
        JsonObject shape = new JsonObject();
        shape.addProperty("kind", "rect");
        shape.add("bounds", Client.toJson(new int[] {20, 20, 60, 40}));
        JsonObject r = op("add", "shape", shape, "attrs", attrs);
        assertTrue(r.get("changed").getAsBoolean());
        int index = r.get("index").getAsInt();
        JsonObject a = app();
        assertFalse(a.get("default").getAsBoolean(), "an edit makes it the student's own appearance");
        List<String> k = kinds(a);
        assertEquals(before + 1, k.size());
        assertEquals("rect", k.get(index));
        for (int i = index + 1; i < k.size(); i++) {
            assertTrue(k.get(i).equals("port") || k.get(i).equals("anchor"), "ports and anchor stay on top: " + k);
        }
        JsonObject rect = shape(index);
        assertEquals("3", rect.getAsJsonObject("attrs").get("stroke-width").getAsString());
        assertEquals("both", rect.getAsJsonObject("attrs").get("paintType").getAsString());
        assertEquals(index, r.getAsJsonArray("selected").get(0).getAsInt(), "the new shape is selected");
        undo();
        assertEquals(before, kinds(app()).size(), "one undo step");
    }

    @Test
    void theOpenAppearanceIsSentAfterEveryChangeUndoToo() {
        int before = kinds(app()).size();   // asked once, as the screen does when the editor opens
        JsonObject shape = new JsonObject();
        shape.addProperty("kind", "oval");
        shape.add("bounds", Client.toJson(new int[] {20, 20, 30, 30}));
        int m0 = e.client.mark();
        op("add", "shape", shape, "attrs", new JsonObject());
        e.client.callObject("file.dirty", params("fileId", fileId));
        assertTrue(e.client.notificationsAfter(m0, "model.appearance").stream()
                .anyMatch(c -> c.get("circuitId").getAsString().equals(sub) && c.getAsJsonArray("shapes").size() == before + 1),
                "the added shape is sent");
        int m = e.client.mark();
        undo();
        e.client.callObject("file.dirty", params("fileId", fileId));
        assertTrue(e.client.notificationsAfter(m, "model.appearance").stream()
                .anyMatch(c -> c.get("circuitId").getAsString().equals(sub) && c.getAsJsonArray("shapes").size() == before),
                "Undo sends the open appearance again (not only a change from what was asked)");
    }

    @Test
    void everyDrawingToolMakesItsShape() {
        String[] kinds = {"line", "polyline", "polygon", "curve", "oval", "roundrect", "text"};
        for (String kind : kinds) {
            JsonObject sh = new JsonObject();
            sh.addProperty("kind", kind);
            switch (kind) {
            case "line":
                sh.add("points", Client.toJson(new Object[] {new int[] {0, 0}, new int[] {40, 0}}));
                break;
            case "polyline":
            case "polygon":
                sh.add("points", Client.toJson(new Object[] {new int[] {0, 0}, new int[] {40, 0}, new int[] {40, 40}}));
                break;
            case "curve":
                sh.add("points", Client.toJson(new Object[] {new int[] {0, 0}, new int[] {40, 0}, new int[] {20, 20}}));
                break;
            case "text":
                sh.add("at", Client.toJson(new int[] {30, 30}));
                sh.addProperty("text", "ALU");
                break;
            default:
                sh.add("bounds", Client.toJson(new int[] {10, 10, 30, 20}));
            }
            JsonObject r = op("add", "shape", sh);
            assertTrue(r.get("changed").getAsBoolean(), kind);
            assertEquals(kind, shape(r.get("index").getAsInt()).get("kind").getAsString(), kind);
        }
        // 빈 새 글은 놓지 않는다(원조 TextTool)
        JsonObject empty = new JsonObject();
        empty.addProperty("kind", "text");
        empty.add("at", Client.toJson(new int[] {0, 0}));
        empty.addProperty("text", "");
        assertEquals("empty", op("add", "shape", empty).get("outcome").getAsString());
        JsonObject bad = new JsonObject();
        bad.addProperty("kind", "star");
        assertEquals(-32602, e.client.fail("edit.appearance", params("fileId", fileId, "circuitId", sub, "op", "add",
                "shape", bad)).code);
    }

    @Test
    void moveHandleAttributesTextOrderAndVertices() {
        JsonObject sh = new JsonObject();
        sh.addProperty("kind", "polygon");
        sh.add("points", Client.toJson(new Object[] {new int[] {0, 0}, new int[] {40, 0}, new int[] {40, 40}}));
        int poly = op("add", "shape", sh).get("index").getAsInt();

        // 옮기기
        JsonObject moved = op("move", "shapes", new Object[] {poly}, "dx", 10, "dy", 20);
        assertTrue(moved.get("changed").getAsBoolean());
        assertEquals(10, ints(shape(poly).getAsJsonArray("points").get(0).getAsJsonArray())[0]);
        assertEquals(20, ints(shape(poly).getAsJsonArray("points").get(0).getAsJsonArray())[1]);
        // 손잡이(꼭짓점) 끌기
        JsonObject h = op("handle", "shape", poly, "at", new int[] {10, 20}, "dx", -10, "dy", 0);
        assertTrue(h.get("changed").getAsBoolean());
        assertEquals(0, ints(shape(poly).getAsJsonArray("points").get(0).getAsJsonArray())[0]);
        assertEquals(-32602, e.client.fail("edit.appearance", params("fileId", fileId, "circuitId", sub, "op", "handle",
                "shape", poly, "at", new int[] {999, 999}, "dx", 1, "dy", 1)).code);
        // 끄는 동안의 손잡이(원조 getHandles(gesture))
        JsonObject pv = e.client.callObject("model.appearanceHandles", params("fileId", fileId, "circuitId", sub,
                "shape", poly, "at", new int[] {0, 20}, "dx", 5, "dy", 5));
        assertEquals(5, ints(pv.getAsJsonArray("handles").get(0).getAsJsonArray())[0]);
        // 속성 표
        JsonObject attr = op("setAttr", "shapes", new Object[] {poly}, "attr", "stroke-width", "value", "4");
        assertTrue(attr.get("changed").getAsBoolean());
        assertEquals("4", shape(poly).getAsJsonObject("attrs").get("stroke-width").getAsString());
        assertEquals("same", op("setAttr", "shapes", new Object[] {poly}, "attr", "stroke-width", "value", "4")
                .get("outcome").getAsString());
        // 꼭짓점 더하기(두 점 사이의 선 위)와 빼기
        int n = shape(poly).getAsJsonArray("points").size();
        JsonObject add = op("addVertex", "shape", poly, "at", new int[] {30, 20});
        assertTrue(add.get("changed").getAsBoolean(), add.toString());
        assertEquals(n + 1, shape(poly).getAsJsonArray("points").size());
        JsonObject rem = op("removeVertex", "shape", poly, "at", new int[] {30, 20});
        assertTrue(rem.get("changed").getAsBoolean(), rem.toString());
        assertEquals(n, shape(poly).getAsJsonArray("points").size());
        assertEquals("noVertex", op("addVertex", "shape", poly, "at", new int[] {500, 500}).get("outcome")
                .getAsString());

        // 글: 고치고, 빈 글이면 지운다
        JsonObject t = new JsonObject();
        t.addProperty("kind", "text");
        t.add("at", Client.toJson(new int[] {50, 50}));
        t.addProperty("text", "x");
        int text = op("add", "shape", t).get("index").getAsInt();
        op("text", "shape", text, "text", "ALU");
        assertEquals("ALU", shape(text).get("text").getAsString());
        op("setAttr", "shapes", new Object[] {text}, "attr", "align", "value", "left");
        assertEquals("left", shape(text).getAsJsonObject("attrs").get("align").getAsString());
        int count = kinds(app()).size();
        op("text", "shape", text, "text", "");
        assertEquals(count - 1, kinds(app()).size());

        // 차례: 맨 아래로(포트 위로는 못 올라간다)
        JsonObject low = op("lowerBottom", "shapes", new Object[] {poly});
        assertTrue(low.get("changed").getAsBoolean());
        assertEquals("polygon", shape(0).get("kind").getAsString());
        JsonObject top = op("raiseTop", "shapes", new Object[] {0});
        assertTrue(top.get("changed").getAsBoolean());
        List<String> k = kinds(app());
        assertEquals("anchor", k.get(k.size() - 1), "raised to the top of the shapes, under the ports: " + k);
    }

    @Test
    void theEditMenuCutCopyPasteDuplicateDeleteAndItsEnabling() {
        JsonObject sh = new JsonObject();
        sh.addProperty("kind", "rect");
        sh.add("bounds", Client.toJson(new int[] {0, 0, 20, 20}));
        int rect = op("add", "shape", sh).get("index").getAsInt();
        JsonObject menu = e.client.callObject("model.appearanceMenu", params("fileId", fileId, "circuitId", sub,
                "shapes", new Object[] {rect}));
        assertTrue(menu.get("copy").getAsBoolean());
        assertTrue(menu.get("delete").getAsBoolean());
        assertTrue(menu.get("duplicate").getAsBoolean());
        // 포트만 고르면 지울 것이 없다
        List<String> k = kinds(app());
        int port = k.indexOf("port");
        JsonObject portMenu = e.client.callObject("model.appearanceMenu", params("fileId", fileId, "circuitId", sub,
                "shapes", new Object[] {port}));
        assertFalse(portMenu.get("delete").getAsBoolean());
        assertEquals("nothing", op("delete", "shapes", new Object[] {port}).get("outcome").getAsString());

        int count = k.size();
        JsonObject dup = op("duplicate", "shapes", new Object[] {rect});
        assertTrue(dup.get("changed").getAsBoolean());
        assertEquals(count + 1, kinds(app()).size());
        assertEquals(1, dup.getAsJsonArray("selected").size(), "the copy is selected");
        // Copy는 원조처럼 되돌리기 한 단계, Paste는 붙인 것을 고른다
        assertTrue(op("copy", "shapes", new Object[] {rect}).get("changed").getAsBoolean());
        assertTrue(e.client.callObject("model.appearanceMenu", params("fileId", fileId, "circuitId", sub, "shapes",
                new Object[0])).get("paste").getAsBoolean());
        JsonObject paste = op("paste");
        assertTrue(paste.get("changed").getAsBoolean());
        assertEquals(count + 2, kinds(app()).size());
        // Delete, Cut
        JsonObject del = op("delete", "shapes", new Object[] {rect});
        assertTrue(del.get("changed").getAsBoolean());
        assertEquals(count + 1, kinds(app()).size());
        JsonObject cut = op("cut", "shapes", new Object[] {0});
        assertTrue(cut.get("changed").getAsBoolean());
        undo();
        undo();
        assertEquals(count + 2, kinds(app()).size(), "cut and delete undone");
    }

    @Test
    void hitsAreTheSelectToolsQuestions() {
        JsonObject sh = new JsonObject();
        sh.addProperty("kind", "rect");
        sh.add("bounds", Client.toJson(new int[] {200, 200, 40, 40}));
        JsonObject attrs = new JsonObject();
        attrs.addProperty("paintType", "fill");
        int rect = op("add", "shape", sh, "attrs", attrs).get("index").getAsInt();
        JsonObject hit = e.client.callObject("model.appearanceHit", params("fileId", fileId, "circuitId", sub, "at",
                new int[] {220, 220}, "selected", new Object[] {rect}, "rect", new int[] {190, 190, 250, 250}));
        assertEquals(rect, hit.get("top").getAsInt());
        assertEquals(1, hit.getAsJsonArray("inRect").size());
        JsonObject corner = e.client.callObject("model.appearanceHit", params("fileId", fileId, "circuitId", sub, "at",
                new int[] {201, 199}, "selected", new Object[] {rect}));
        assertEquals(rect, corner.getAsJsonObject("handle").get("shape").getAsInt());
        assertEquals(200, ints(corner.getAsJsonObject("handle").getAsJsonArray("at"))[0]);
        JsonObject none = e.client.callObject("model.appearanceHit", params("fileId", fileId, "circuitId", sub, "at",
                new int[] {900, 900}));
        assertTrue(none.get("top").isJsonNull());
    }

    @Test
    void movingAPortMovesTheInstancesPortAndRevertGoesBack() throws Exception {
        List<String> k = kinds(app());
        int port = k.indexOf("port");
        int[] at = ints(shape(port).getAsJsonObject("port").getAsJsonArray("at"));
        int m = e.client.mark();
        JsonObject r = op("move", "shapes", new Object[] {port}, "dx", 0, "dy", -10);
        assertTrue(r.get("changed").getAsBoolean());
        e.client.callObject("file.dirty", params("fileId", fileId));
        assertTrue(e.client.notificationsAfter(m, "model.changed").stream()
                .anyMatch(c -> c.get("circuitId").getAsString().equals(main)), "the instance in main changed");
        assertTrue(e.client.notificationsAfter(m, "model.appearance").stream()
                .anyMatch(c -> c.get("circuitId").getAsString().equals(sub)), "the open appearance is sent again");
        assertEquals(at[1] - 10, ints(shape(port).getAsJsonObject("port").getAsJsonArray("at"))[1]);
        JsonObject rev = op("revert");
        assertTrue(rev.get("changed").getAsBoolean());
        assertTrue(app().get("default").getAsBoolean());
        assertEquals("same", op("revert").get("outcome").getAsString());
        undo();
        assertFalse(app().get("default").getAsBoolean(), "revert is one undo step");
    }

    @Test
    void theSavedAppearanceIsTheOriginalsAppear() throws Exception {
        JsonObject sh = new JsonObject();
        sh.addProperty("kind", "oval");
        sh.add("bounds", Client.toJson(new int[] {40, 40, 20, 20}));
        op("add", "shape", sh);
        File out = tmp.resolve("saved.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", out.getPath()));
        String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("<appear>") && text.contains("<ellipse"), text);
        LogisimFile again = new Loader(null).openLogisimFile(out);
        assertFalse(again.getCircuit("sub").getAppearance().isDefaultAppearance());
        Location anchor = null;
        for (com.cburch.draw.model.CanvasObject o : again.getCircuit("sub").getAppearance().getObjectsFromBottom()) {
            if (o instanceof com.cburch.logisim.circuit.appear.AppearanceAnchor) {
                anchor = ((com.cburch.logisim.circuit.appear.AppearanceAnchor) o).getLocation();
            }
        }
        assertNotNull(anchor);
    }

    @Test
    void aReadOnlyFilesAppearanceIsNotEdited() throws Exception {
        // 읽기 전용 파일
        File path = tmp.resolve("app.circ").toFile();
        e.client.call("file.close", params("fileId", fileId));
        JsonObject r = e.client.callObject("file.open", params("path", path.getPath(), "readOnly", true));
        String f2 = r.get("fileId").getAsString();
        String s2 = null;
        for (JsonElement c : r.getAsJsonArray("circuits")) {
            if (c.getAsJsonObject().get("name").getAsString().equals("sub")) {
                s2 = c.getAsJsonObject().get("circuitId").getAsString();
            }
        }
        assertFalse(e.client.callObject("model.appearance", params("fileId", f2, "circuitId", s2)).get("editable")
                .getAsBoolean());
        Client.Failure fail = e.client.fail("edit.appearance", params("fileId", f2, "circuitId", s2, "op", "revert"));
        assertEquals(3, fail.code);
        assertEquals("readOnly", fail.reason());
    }
}
