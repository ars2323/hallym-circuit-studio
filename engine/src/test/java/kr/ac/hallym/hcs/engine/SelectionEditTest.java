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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.move.MoveGesture;
import com.cburch.logisim.tools.move.MoveResult;

import kr.ac.hallym.hcs.app.wiring.SafeMove;
import kr.ac.hallym.hcs.app.wiring.WireRules;
import kr.ac.hallym.hcs.engine.doc.EngineLoader;

/**
 * 고른 것에 하는 편집(N-08, D-146): edit.select의 여러 길, 선택을 따라가는 옮기기·지우기·속성, Edit 메뉴(Copy·Cut·
 * Paste·Duplicate)와 떠 있는 선택, 원조 되돌리기 단계(붙여넣기·옮기기·내려놓기 한 단계, 빈 Delete도 한 단계), 돌리기,
 * 숫자 키(KeyConfigurator), 도구 속성, 글자 도구, 놓을 부품의 모습, 끄는 동안의 선, 입력 핀 값, edit.selection 알림.
 */
class SelectionEditTest {
    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    JsonObject edit(String method, Object... kv) {
        List<Object> all = new ArrayList<>(List.of("fileId", fileId, "circuitId", main));
        all.addAll(List.of(kv));
        return e.client.callObject(method, params(all.toArray()));
    }

    String add(String lib, String name, int x, int y) {
        return edit("edit.addComponent", "lib", lib, "name", name, "loc", xy(x, y)).get("id").getAsString();
    }

    JsonObject snapshot() {
        return e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
    }

    List<JsonObject> comps(String name) {
        return Fixtures.byName(snapshot().getAsJsonArray("components"), name);
    }

    /**
     * 마지막 edit.selection 알림(from 뒤). 알림은 편집의 응답 뒤에 오므로 요청 하나를 더 보내 그 응답까지 기다린다(엔진은
     * 요청을 차례로 처리하고, 한 요청의 알림은 다음 요청 전에 나간다).
     */
    JsonObject selection(int from) {
        e.client.call("file.dirty", params("fileId", fileId));
        List<JsonObject> all = e.client.notificationsAfter(from, "edit.selection");
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    static List<String> ids(JsonObject sel) {
        List<String> out = new ArrayList<>();
        for (JsonElement x : sel.getAsJsonArray("ids")) {
            out.add(x.getAsString());
        }
        return out;
    }

    @Test
    void selectingByIdsShiftRectangleFilterAndAll() throws Exception {
        String and = add("Gates", "AND Gate", 300, 200);
        String pin = add("Wiring", "Pin", 100, 190);
        edit("edit.addWire", "points", new Object[] {xy(100, 190), xy(250, 190)});
        int m = e.client.mark();
        assertFalse(edit("edit.select", "ids", new Object[] {and}).get("changed").getAsBoolean(),
                "selecting changes neither the model nor the undo log");
        assertEquals(List.of(and), ids(selection(m)));
        edit("edit.select", "ids", new Object[] {pin}, "toggle", true);
        assertEquals(List.of(and, pin).stream().sorted().toList(), ids(selection(m)).stream().sorted().toList());
        edit("edit.select", "ids", new Object[] {and}, "toggle", true);
        assertEquals(List.of(pin), ids(selection(m)));
        // the rectangle: everything wholly inside, the wire too
        edit("edit.select", "rect", new int[] {40, 100, 400, 300});
        JsonObject s = selection(m);
        assertEquals(3, ids(s).size(), s.toString());
        assertTrue(ids(s).stream().anyMatch(x -> x.startsWith("w")));
        // Only Wires, then Select All, then nothing
        edit("edit.select", "filter", "wires");
        assertTrue(ids(selection(m)).stream().allMatch(x -> x.startsWith("w")));
        edit("edit.select", "all", true);
        assertEquals(3, ids(selection(m)).size());
        edit("edit.select");
        assertEquals(List.of(), ids(selection(m)));
        assertEquals(-32602, e.client.fail("edit.select", params("fileId", fileId, "circuitId", main, "filter",
                "gates")).code);
    }

    @Test
    void addingSelectsThePartAndAWiringToolClearsTheSelection() throws Exception {
        int m = e.client.mark();
        String and = add("Gates", "AND Gate", 300, 200);
        assertEquals(List.of(and), ids(selection(m)), "AddTool: back to the Edit Tool with the new part selected");
        edit("edit.addWire", "points", new Object[] {xy(100, 400), xy(200, 400)}, "tool", "edit");
        assertEquals(List.of(and), ids(selection(m)), "a wire from the Edit Tool keeps the selection");
        edit("edit.addWire", "points", new Object[] {xy(100, 500), xy(200, 500)});
        assertEquals(List.of(), ids(selection(m)), "taking the Wiring Tool drops the selection");
    }

    @Test
    void moveTheSelectionKeepsItSelectedAndUndoBringsItBack() throws Exception {
        String not = add("Gates", "NOT Gate", 300, 200);
        int m = e.client.mark();
        JsonObject r = edit("edit.move", "dx", 0, "dy", 50);
        assertEquals("moved", r.get("outcome").getAsString());
        List<String> after = ids(selection(m));
        assertEquals(1, after.size());
        assertNotEquals(not, after.get(0), "Logisim put a new object in its place: a new id");
        assertEquals(250, comps("NOT Gate").get(0).getAsJsonArray("loc").get(1).getAsInt());
        edit("edit.undo");
        assertEquals(200, comps("NOT Gate").get(0).getAsJsonArray("loc").get(1).getAsInt());
        assertEquals(1, ids(selection(m)).size(), "the selection comes back with the undo (Selection's saved selection)");
        // the original clamps at 0 and snaps to the grid (SelectTool.computeDxDy)
        edit("edit.select", "ids", new Object[] {comps("NOT Gate").get(0).get("id").getAsString()});
        edit("edit.move", "dx", -1000, "dy", 3);
        JsonObject moved = comps("NOT Gate").get(0);
        JsonArray b = moved.getAsJsonArray("bounds");
        assertEquals(0, b.get(0).getAsInt(), "not past the left edge");
        assertEquals(200, moved.getAsJsonArray("loc").get(1).getAsInt(), "3 snaps to 0");
        edit("edit.select");
        assertEquals("empty", edit("edit.move", "dx", 10, "dy", 0).get("outcome").getAsString());
    }

    @Test
    void anEmptyDeleteLeavesAnUndoStepAsTheOriginalDoes() throws Exception {
        add("Gates", "AND Gate", 300, 200);
        edit("edit.select");
        JsonObject r = edit("edit.delete");
        assertTrue(r.get("changed").getAsBoolean(), "an undo step is recorded");
        assertEquals("empty", r.get("outcome").getAsString());
        assertEquals(1, comps("AND Gate").size());
        assertTrue(edit("edit.undo").get("changed").getAsBoolean()); // the empty Delete
        assertEquals(1, comps("AND Gate").size(), "undoing it changes nothing");
        edit("edit.undo"); // the AND
        assertEquals(0, comps("AND Gate").size());
    }

    @Test
    void pasteFloatsThenMoveAndDropJoinThePasteInOneUndoStep() throws Exception {
        String not = add("Gates", "NOT Gate", 200, 100);
        edit("edit.copy", "ids", new Object[] {not});
        int m = e.client.mark();
        edit("edit.paste");
        JsonObject s = selection(m);
        assertEquals(List.of(), ids(s));
        assertEquals(1, s.getAsJsonArray("floating").size(), "the copy floats: it is not in the circuit");
        JsonObject floating = s.getAsJsonArray("floating").get(0).getAsJsonObject();
        assertEquals("NOT Gate", floating.get("name").getAsString());
        assertEquals(1, comps("NOT Gate").size());
        // moving what floats puts the moved copy into the circuit, still selected (SelectionBase.translateHelper)
        edit("edit.move", "dx", 0, "dy", 100);
        JsonObject moved = selection(m);
        assertEquals(0, moved.getAsJsonArray("floating").size());
        assertEquals(1, ids(moved).size());
        assertEquals(2, comps("NOT Gate").size());
        assertFalse(edit("edit.select").get("changed").getAsBoolean(), "nothing floats: nothing to drop");
        edit("edit.undo"); // paste + move: one step (Logisim's shouldAppendTo)
        assertEquals(1, comps("NOT Gate").size());
        edit("edit.undo"); // the copy (not a change of the circuit)
        assertEquals(1, comps("NOT Gate").size());
        edit("edit.undo"); // the NOT
        assertEquals(0, comps("NOT Gate").size());
    }

    @Test
    void theFloatingPartKeepsItsIdWhenItDrops() throws Exception {
        String not = add("Gates", "NOT Gate", 200, 100);
        edit("edit.copy", "ids", new Object[] {not});
        int m = e.client.mark();
        edit("edit.paste");
        String floatingId = selection(m).getAsJsonArray("floating").get(0).getAsJsonObject().get("id").getAsString();
        int m2 = e.client.mark();
        edit("edit.select");
        JsonObject change = e.client.awaitNotificationAfter(m2, "model.changed", c -> true);
        assertEquals(floatingId, change.getAsJsonArray("added").get(0).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void cutAndPasteAndDuplicate() throws Exception {
        String and = add("Gates", "AND Gate", 300, 200);
        edit("edit.cut", "ids", new Object[] {and});
        assertEquals(0, comps("AND Gate").size());
        edit("edit.paste");
        edit("edit.select");
        assertEquals(1, comps("AND Gate").size());
        assertEquals(300, comps("AND Gate").get(0).getAsJsonArray("loc").get(0).getAsInt(), "back where it was cut");
        String not = add("Gates", "NOT Gate", 200, 400);
        int m = e.client.mark();
        edit("edit.duplicate", "ids", new Object[] {not});
        // v1 SafeDuplicate: the copy floats, or -- where it would touch the original -- is moved clear (and so placed)
        JsonObject s = selection(m);
        assertEquals(1, s.getAsJsonArray("floating").size() + comps("NOT Gate").size() - 1, s.toString());
        edit("edit.select");
        assertEquals(2, comps("NOT Gate").size());
        edit("edit.select");
        assertEquals("empty", edit("edit.copy").get("outcome").getAsString(), "nothing selected: Copy is off");
        assertEquals("empty", edit("edit.duplicate").get("outcome").getAsString());
    }

    @Test
    void attributesRotationAndNumberKeysOnTheSelection() throws Exception {
        String and = add("Gates", "AND Gate", 300, 200);
        String or = add("Gates", "OR Gate", 300, 400);
        edit("edit.select", "ids", new Object[] {and, or});
        edit("edit.setAttr", "attr", "inputs", "value", "3");
        assertEquals("3", comps("AND Gate").get(0).getAsJsonObject("attrs").get("inputs").getAsString());
        assertEquals("3", comps("OR Gate").get(0).getAsJsonObject("attrs").get("inputs").getAsString());
        edit("edit.rotate");
        assertEquals("south", comps("AND Gate").get(0).get("facing").getAsString());
        assertEquals("south", comps("OR Gate").get(0).get("facing").getAsString());
        edit("edit.rotate", "clockwise", false);
        assertEquals("east", comps("AND Gate").get(0).get("facing").getAsString());
        // digits: the gates' Number of Inputs; two within the original's 0.8 s make one number (chain)
        edit("edit.keyConfig", "key", "5");
        assertEquals("5", comps("AND Gate").get(0).getAsJsonObject("attrs").get("inputs").getAsString());
        edit("edit.keyConfig", "key", "1");
        edit("edit.keyConfig", "key", "2", "chain", true);
        assertEquals("12", comps("OR Gate").get(0).getAsJsonObject("attrs").get("inputs").getAsString());
        edit("edit.keyConfig", "key", "8", "alt", true);
        assertEquals("8", comps("AND Gate").get(0).getAsJsonObject("attrs").get("width").getAsString(),
                "Alt+digit: Data Bits");
        edit("edit.select");
        assertEquals(-32602, e.client.fail("edit.setAttr", params("fileId", fileId, "circuitId", main, "attr",
                "inputs", "value", "2")).code, "nothing selected");
    }

    @Test
    void theToolsAttributesStayAndGoIntoTheNextPart() throws Exception {
        JsonObject r = e.client.callObject("edit.setToolAttr", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate", "attr", "inputs", "value", "4"));
        assertTrue(r.get("changed").getAsBoolean());
        assertEquals("same", e.client.callObject("edit.setToolAttr", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate", "attr", "inputs", "value", "4")).get("outcome").getAsString());
        add("Gates", "AND Gate", 300, 200);
        assertEquals("4", comps("AND Gate").get(0).getAsJsonObject("attrs").get("inputs").getAsString());
        // a key on the tool (AddTool.processKeyEvent), then the tool's facing (an arrow on the placing tool)
        edit("edit.keyConfig", "lib", "Gates", "name", "OR Gate", "key", "3");
        e.client.callObject("edit.setToolAttr", params("fileId", fileId, "lib", "Gates", "name", "OR Gate", "attr",
                "facing", "value", "north"));
        add("Gates", "OR Gate", 300, 400);
        JsonObject orGate = comps("OR Gate").get(0);
        assertEquals("3", orGate.getAsJsonObject("attrs").get("inputs").getAsString());
        assertEquals("north", orGate.get("facing").getAsString());
        JsonObject ghost = e.client.callObject("model.tool", params("fileId", fileId, "lib", "Gates", "name",
                "OR Gate", "loc", xy(100, 100)));
        JsonObject part = ghost.getAsJsonObject("component");
        assertEquals("ghost", part.get("id").getAsString());
        assertEquals("north", part.get("facing").getAsString());
        assertEquals(4, part.getAsJsonArray("ports").size(), "3 inputs and the output");
        assertEquals(1, comps("OR Gate").size(), "the ghost is not placed");
        assertEquals(-32602, e.client.fail("edit.setToolAttr", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate", "attr", "colour", "value", "4")).code);
        assertEquals(1, e.client.fail("model.tool", params("fileId", fileId, "lib", "Gates", "name",
                "Flux Gate")).code);
        edit("edit.undo"); // the OR
        edit("edit.undo"); // the tool's facing
        JsonObject again = e.client.callObject("model.tool", params("fileId", fileId, "lib", "Gates", "name",
                "OR Gate"));
        assertEquals("east", again.getAsJsonObject("component").get("facing").getAsString(),
                "a tool attribute is an undo step (ToolAttributeAction)");
    }

    @Test
    void theTextToolAddsALabelAndEditsLabels() throws Exception {
        JsonObject r = edit("edit.text", "loc", xy(105, 95), "text", "hello");
        assertTrue(r.get("changed").getAsBoolean());
        JsonObject label = comps("Text").get(0);
        assertEquals("hello", label.getAsJsonObject("attrs").get("text").getAsString());
        assertEquals(105, label.getAsJsonArray("loc").get(0).getAsInt(), "not snapped to the grid");
        assertEquals("empty", edit("edit.text", "loc", xy(300, 300), "text", "").get("outcome").getAsString());
        String pin = add("Wiring", "Pin", 100, 300);
        edit("edit.text", "id", pin, "text", "A");
        assertEquals("A", comps("Pin").get(0).getAsJsonObject("attrs").get("label").getAsString());
        // clearing a Label's text: the original makes "Remove Label" of adding it again -- it stays (I-82)
        edit("edit.text", "id", label.get("id").getAsString(), "text", "");
        assertEquals(1, comps("Text").size());
        assertEquals("hello", comps("Text").get(0).getAsJsonObject("attrs").get("text").getAsString());
    }

    @Test
    void theMovePreviewIsTheOriginalsFollowingWires() throws Exception {
        File f = Fixtures.counter(tmp);
        JsonObject r = e.client.callObject("file.open", params("path", f.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        String counter = comps("Counter").get(0).get("id").getAsString();
        edit("edit.addWire", "points", new Object[] {xy(300, 200), xy(300, 100)});
        edit("edit.select", "ids", new Object[] {counter});
        JsonObject p = e.client.callObject("model.movePreview", params("fileId", fileId, "circuitId", main, "dx",
                33, "dy", 0));
        assertEquals(30, p.get("dx").getAsInt(), "snapped like the move");
        JsonObject none = e.client.callObject("model.movePreview", params("fileId", fileId, "circuitId", main, "dx",
                30, "dy", 0, "connect", false));
        assertEquals(0, none.getAsJsonArray("added").size());
        assertTrue(p.getAsJsonArray("added").size() + p.getAsJsonArray("unconnected").size() > 0, p.toString());
    }

    @Test
    void aPinTakesATypedValue() throws Exception {
        String pin = edit("edit.addComponent", "lib", "Wiring", "name", "Pin", "loc", xy(100, 100), "attrs",
                params("width", "8", "tristate", "false")).get("id").getAsString();
        String net = Fixtures.netOf(snapshot().getAsJsonArray("nets"), pin, 0);
        int m = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        e.client.call("sim.pinValue", params("fileId", fileId, "circuitId", main, "componentId", pin, "value", "0x1F"));
        e.client.awaitNotificationAfter(m, "sim.values", v -> v.getAsJsonObject("nets").has(net)
                && v.getAsJsonObject("nets").get(net).getAsString().equals("00011111"));
        e.client.call("sim.pinValue", params("fileId", fileId, "circuitId", main, "componentId", pin, "value", "-3"));
        e.client.awaitNotificationAfter(m, "sim.values", v -> v.getAsJsonObject("nets").has(net)
                && v.getAsJsonObject("nets").get(net).getAsString().equals("11111101"));
        Client.Failure f = e.client.fail("sim.pinValue", params("fileId", fileId, "circuitId", main, "componentId", pin,
                "value", "0x1FF"));
        assertEquals(-32602, f.code);
        assertEquals("badValue", f.reason());
        String out = edit("edit.addComponent", "lib", "Wiring", "name", "Pin", "loc", xy(300, 100), "attrs",
                params("output", "true")).get("id").getAsString();
        assertEquals(-32602, e.client.fail("sim.pinValue", params("fileId", fileId, "circuitId", main, "componentId",
                out, "value", "1")).code);
    }

    /** Edit 도구의 누름(SelectTool.mousePressed): 답이 끌기의 뜻, 선택은 원조 규칙대로. */
    @Test
    void aPressSaysWhetherTheDragMovesOrDrawsARectangle() throws Exception {
        String and = add("Gates", "AND Gate", 300, 200);
        String not = add("Gates", "NOT Gate", 500, 200);
        int m = e.client.mark();
        JsonObject r = edit("edit.select", "at", xy(280, 200));
        assertEquals("moving", r.get("outcome").getAsString(), "a press on a part moves it");
        assertEquals(List.of(and), ids(selection(m)));
        assertEquals("rect", edit("edit.select", "at", xy(100, 100)).get("outcome").getAsString());
        assertEquals(List.of(), ids(selection(m)), "a press on nothing drops the selection");
        edit("edit.select", "at", xy(280, 200));
        edit("edit.select", "at", xy(490, 200), "toggle", true);
        assertEquals(2, ids(selection(m)).size(), "Shift adds");
        assertEquals("moving", edit("edit.select", "at", xy(280, 200)).get("outcome").getAsString());
        assertEquals(2, ids(selection(m)).size(), "a press inside the selection keeps it (to move it all)");
        edit("edit.select", "at", xy(280, 200), "toggle", true);
        assertEquals(List.of(not), ids(selection(m)), "Shift on a selected part takes it out");
        assertEquals("rect", edit("edit.select", "at", xy(100, 100), "toggle", true).get("outcome").getAsString());
        assertEquals(List.of(not), ids(selection(m)), "Shift on nothing keeps the selection for the rectangle");
    }

    /** 부품 도구를 든 채 방향 키(AddTool.keyPressed): 설정기가 받지 않으면 놓을 부품의 방향(되돌리기 한 단계). */
    @Test
    void anArrowTurnsTheHeldToolAndAltArrowGoesToItsKeys() throws Exception {
        assertTrue(edit("edit.keyConfig", "lib", "Wiring", "name", "Pin", "key", "ArrowLeft").get("changed")
                .getAsBoolean());
        JsonObject ghost = e.client.callObject("model.tool", params("fileId", fileId, "lib", "Wiring", "name", "Pin"));
        assertEquals("west", ghost.getAsJsonObject("component").get("facing").getAsString());
        edit("edit.keyConfig", "lib", "Gates", "name", "AND Gate", "key", "ArrowUp");
        assertEquals("north", e.client.callObject("model.tool", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate")).getAsJsonObject("component").get("facing").getAsString());
        edit("edit.undo");
        assertEquals("east", e.client.callObject("model.tool", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate")).getAsJsonObject("component").get("facing").getAsString());
        // a part without a facing (a Label's is its text's): nothing
        assertFalse(edit("edit.keyConfig", "lib", "Base", "name", "Text Tool", "key", "ArrowUp").get("changed")
                .getAsBoolean());
    }

    /** 글자 도구가 여는 칸(TextTool.mousePressed): 라벨 없는 부품은 몸체, 라벨 있는 부품은 라벨 위, 빈 곳은 새 Label. */
    @Test
    void theTextToolsFieldIsWhereTheOriginalOpensIt() throws Exception {
        String pin = add("Wiring", "Pin", 200, 200);
        JsonObject body = e.client.callObject("model.textAt", params("fileId", fileId, "circuitId", main, "loc",
                xy(195, 200)));
        assertEquals(pin, body.get("id").getAsString(), "a part without a label: anywhere on it");
        assertEquals("", body.get("text").getAsString());
        assertEquals(4, body.getAsJsonArray("box").size());
        edit("edit.setAttr", "ids", new Object[] {pin}, "attr", "label", "value", "A");
        JsonObject onBody = e.client.callObject("model.textAt", params("fileId", fileId, "circuitId", main, "loc",
                xy(195, 200)));
        assertTrue(onBody.get("id").isJsonNull(), "with a label, the body is not the label: a new Label there");
        assertEquals("", onBody.get("text").getAsString());
        JsonObject blank = e.client.callObject("model.textAt", params("fileId", fileId, "circuitId", main, "loc",
                xy(400, 400)));
        assertTrue(blank.get("id").isJsonNull());
        assertFalse(blank.has("none"));
        JsonObject negative = e.client.callObject("model.textAt", params("fileId", fileId, "circuitId", main, "loc",
                xy(-10, 400)));
        assertTrue(negative.get("none").getAsBoolean(), "no Label at a negative point (TextTool)");
        // a Label's own text
        edit("edit.text", "loc", xy(400, 400), "text", "hi");
        JsonObject label = e.client.callObject("model.textAt", params("fileId", fileId, "circuitId", main, "loc",
                xy(402, 398)));
        assertEquals("hi", label.get("text").getAsString());
        assertEquals(comps("Text").get(0).get("id").getAsString(), label.get("id").getAsString());
        assertEquals(1, comps("Text").size(), "asking changes nothing");
    }

    /** model.tool의 attrs: 놓을 부품에만(검색창의 "and 3"), 도구 속성은 그대로. */
    @Test
    void aGhostWithAttributesLeavesTheToolAlone() throws Exception {
        // (the tool's own value: whatever the tool has now; the Gates tools are shared in the process, as Wiring's)
        String own = e.client.callObject("model.tool", params("fileId", fileId, "lib", "Gates", "name", "AND Gate"))
                .getAsJsonObject("component").getAsJsonObject("attrs").get("inputs").getAsString();
        String other = own.equals("3") ? "4" : "3";
        JsonObject given = e.client.callObject("model.tool", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate", "attrs", params("inputs", other)));
        assertEquals(other, given.getAsJsonObject("component").getAsJsonObject("attrs").get("inputs").getAsString());
        JsonObject plain = e.client.callObject("model.tool", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate"));
        assertEquals(own, plain.getAsJsonObject("component").getAsJsonObject("attrs").get("inputs").getAsString());
        assertEquals(-32602, e.client.fail("model.tool", params("fileId", fileId, "lib", "Gates", "name",
                "AND Gate", "attrs", params("colour", "red"))).code);
    }

    /** 엔진 모델의 모양: 선 끝과 부품 이름·자리(v1 SafeMoveTest.geometry와 같은 적기). */
    Set<String> geometry() {
        Set<String> ret = new TreeSet<>();
        JsonObject s = snapshot();
        for (JsonElement w : s.getAsJsonArray("wires")) {
            JsonArray a = w.getAsJsonObject().getAsJsonArray("a"), b = w.getAsJsonObject().getAsJsonArray("b");
            ret.add("W(" + a.get(0) + "," + a.get(1) + ")(" + b.get(0) + "," + b.get(1) + ")");
        }
        for (JsonElement x : s.getAsJsonArray("components")) {
            JsonArray l = x.getAsJsonObject().getAsJsonArray("loc");
            ret.add(x.getAsJsonObject().get("name").getAsString() + "(" + l.get(0) + "," + l.get(1) + ")");
        }
        return ret;
    }

    static Set<String> geometry(Circuit c) {
        Set<String> ret = new TreeSet<>();
        for (Wire w : c.getWires()) {
            ret.add("W" + w.getEnd0() + w.getEnd1());
        }
        for (Component x : c.getNonWires()) {
            ret.add(x.getFactory().getName() + x.getLocation());
        }
        return ret;
    }

    String wireAt(int x0, int y0, int x1, int y1) {
        for (JsonElement w : snapshot().getAsJsonArray("wires")) {
            JsonObject o = w.getAsJsonObject();
            JsonArray a = o.getAsJsonArray("a"), b = o.getAsJsonArray("b");
            if (a.get(0).getAsInt() == x0 && a.get(1).getAsInt() == y0 && b.get(0).getAsInt() == x1
                    && b.get(1).getAsInt() == y1) {
                return o.get("id").getAsString();
            }
        }
        throw new AssertionError("no wire " + x0 + "," + y0 + "-" + x1 + "," + y1);
    }

    /** B-11: 선 하나만 골라 끌면 v1 SegmentDrag -- 양쪽 다리가 늘고 줄어 꺾임이 따라오고, 되돌리기 한 번에 돌아온다. */
    @Test
    void draggingOneWiresMiddleStretchesItsLegsAsV1Did() throws Exception {
        edit("edit.addComponent", "lib", "Wiring", "name", "Pin", "loc", xy(100, 100));
        edit("edit.addWire", "points", new Object[] {xy(100, 100), xy(200, 100)});
        edit("edit.addWire", "points", new Object[] {xy(200, 100), xy(200, 200)});
        edit("edit.addWire", "points", new Object[] {xy(200, 200), xy(300, 200)});
        edit("edit.addComponent", "lib", "Wiring", "name", "Pin", "loc", xy(300, 200), "attrs",
                params("facing", "west", "output", "true"));
        Set<String> before = geometry();
        edit("edit.select", "ids", new Object[] {wireAt(200, 100, 200, 200)});
        assertEquals("moved", edit("edit.move", "dx", 40, "dy", 0).get("outcome").getAsString());
        Set<String> after = geometry();
        assertTrue(after.containsAll(List.of("W(100,100)(240,100)", "W(240,100)(240,200)", "W(240,200)(300,200)")),
                after.toString());
        assertEquals(3, snapshot().getAsJsonArray("wires").size(), "the legs follow; no extra pieces");
        edit("edit.undo");
        assertEquals(before, geometry(), "one undo brings the segment and its legs back");
    }

    /**
     * W-01·W-02·W-03·S-03: 엔진의 옮기기는 v1 SafeMove 그대로다 -- demo-datapath의 부품마다 같은 옮기기를 v1 길(원조
     * MoveGesture + SafeMove를 같은 JVM에서 직접)과 엔진 의도(edit.select + edit.move)로 해서 모양이 같다(길 찾기가
     * 결정적이고 정리 단계·고무줄까지 같다). 새로 생긴 군더더기(고리·막다른 끝·쪼개진 일직선)는 없다.
     */
    @Test
    void theEnginesMoveIsV1sSafeMoveOnTheDemo() throws Exception {
        File demo = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "demo-datapath.circ"), tmp);
        JsonObject r = e.client.callObject("file.open", params("path", demo.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        LogisimFile v1File = new EngineLoader().openLogisimFile(demo);
        Project proj = new Project(v1File);
        proj.getSimulator().setIsRunning(false);
        Circuit c = v1File.getMainCircuit();
        assertEquals(geometry(c), geometry(), "the same circuit to start with");
        List<String> tidy = WireRules.clutter(c);
        int[][] where = {{300, 200}, {860, 240}, {1100, 240}, {1440, 440}, {200, 120}};
        int[][] moves = {{0, 20}, {20, 0}, {-20, -20}};
        int moved = 0;
        for (int[] at : where) {
            for (int[] d : moves) {
                Component x = null;
                for (Component k : c.getNonWires()) {
                    if (k.getLocation().getX() == at[0] && k.getLocation().getY() == at[1]
                            && !k.getFactory().getName().equals("Tunnel") && !k.getFactory().getName().equals("Pin")) {
                        x = k;
                    }
                }
                assertNotNull(x, at[0] + "," + at[1]);
                Selection sel = new Canvas(proj).getSelection();
                sel.addAll(List.of(x));
                MoveResult mr = new MoveGesture((g, gx, gy) -> { }, c, sel.getAnchoredComponents())
                        .forceRequest(d[0], d[1]);
                SafeMove.Outcome o = SafeMove.move(proj, sel, d[0], d[1], mr);
                String id = null;
                for (JsonElement k : snapshot().getAsJsonArray("components")) {
                    JsonObject ko = k.getAsJsonObject();
                    if (ko.getAsJsonArray("loc").get(0).getAsInt() == at[0]
                            && ko.getAsJsonArray("loc").get(1).getAsInt() == at[1]
                            && ko.get("name").getAsString().equals(x.getFactory().getName())) {
                        id = ko.get("id").getAsString();
                    }
                }
                assertNotNull(id, x.getFactory().getName());
                edit("edit.select", "ids", new Object[] {id});
                String outcome = edit("edit.move", "dx", d[0], "dy", d[1]).get("outcome").getAsString();
                String what = x.getFactory().getName() + " by " + d[0] + "," + d[1];
                assertEquals(o == SafeMove.Outcome.MOVED ? "moved"
                        : o == SafeMove.Outcome.MOVED_WITHOUT_WIRES ? "movedWithoutWires" : "refused", outcome, what);
                assertEquals(geometry(c), geometry(), what + ": the engine's wires are v1's");
                if (o == SafeMove.Outcome.MOVED) {
                    moved++;
                    List<String> now = WireRules.clutter(c);
                    now.removeAll(tidy);
                    assertEquals(List.of(), now, what + ": no loops, dead ends or split lines (S-03)");
                }
                if (o != SafeMove.Outcome.REFUSED) {
                    proj.undoAction();
                    edit("edit.undo");
                }
                assertEquals(geometry(c), geometry(), what + ": undone alike");
            }
        }
        assertTrue(moved >= 5, "most of the moves keep their wires: " + moved);
    }

    List<JsonObject> compsIn(String circuitId, String name) {
        return Fixtures.byName(e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", circuitId))
                .getAsJsonArray("components"), name);
    }

    /** main에 AND 게이트 하나, 회로 sub와 main 안의 sub 인스턴스; AND를 복사해 붙여 넣어 main에 떠 있게 한다. */
    String[] pastedInMainWithASub() {
        add("Gates", "AND Gate", 300, 200);
        String sub = e.client.callObject("edit.createCircuit", params("fileId", fileId, "name", "sub"))
                .get("circuitId").getAsString();
        e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", sub, "lib", "Wiring",
                "name", "Pin", "loc", xy(100, 100)));
        String inst = edit("edit.addComponent", "name", "sub", "loc", xy(500, 400)).get("id").getAsString();
        String and = comps("AND Gate").get(0).get("id").getAsString();
        edit("edit.copy", "ids", new Object[] {and});
        edit("edit.paste");
        return new String[] {sub, inst};
    }

    /**
     * V2(D-146): 시뮬레이션이 보는 회로를 바꿔도(sim.watch: 다른 회로, 인스턴스 안) 모델은 바뀌지 않는다 -- 떠 있는
     * 붙여넣기는 그대로이고 model.changed가 없다. 화면이 회로를 바꾸기 전에 보내는 edit.select(비우기)가 그것을
     * 붙여 넣은 회로 main에 내려놓는다(편집 의도: model.changed가 온다).
     */
    @Test
    void watchingAnotherCircuitNeverDropsAPasteTheWindowsClearDoes() throws Exception {
        String[] s = pastedInMainWithASub();
        String sub = s[0], inst = s[1];
        int m = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", sub));
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main, "path", new Object[] {inst}));
        e.client.call("file.dirty", params("fileId", fileId));
        assertEquals(List.of(), e.client.notificationsAfter(m, "model.changed"), "sim.watch changes no model");
        assertEquals(1, comps("AND Gate").size(), "still floating");
        // what the window sends before it shows another circuit: a clear on the paste's circuit
        JsonObject r = edit("edit.select");
        assertTrue(r.get("changed").getAsBoolean(), r.toString());
        JsonObject changed = e.client.awaitNotificationAfter(m, "model.changed",
                c -> c.get("circuitId").getAsString().equals(main));
        assertEquals(1, changed.getAsJsonArray("added").size(), changed.toString());
        assertEquals(2, comps("AND Gate").size(), "dropped into main, where it was pasted");
        assertEquals(0, compsIn(sub, "AND Gate").size());
    }

    /**
     * C2(D-146): 인스턴스 안을 보는 동안(원조 setCircuitState가 지금 회로를 서브회로로 바꾼다) 다른 회로를 편집하면
     * 떠 있던 붙여넣기는 붙여 넣은 회로 main에 내려앉는다(원조는 회로를 바꾸기 전에 내려놓는다) -- 서브회로가 아니다.
     * 되살리기 재생(편집 의도만 새 엔진에 같은 차례로)도 같은 모델이 된다.
     */
    @Test
    void aPasteLandsWhereItWasPastedEvenAfterWatchingInsideAnInstance() throws Exception {
        String[] s = pastedInMainWithASub();
        String sub = s[0], inst = s[1];
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main, "path", new Object[] {inst}));
        int m = e.client.mark();
        JsonObject r = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", sub, "lib",
                "Gates", "name", "OR Gate", "loc", xy(300, 300)));
        assertTrue(r.get("changed").getAsBoolean());
        e.client.awaitNotificationAfter(m, "model.changed", c -> c.get("circuitId").getAsString().equals(main));
        assertEquals(2, comps("AND Gate").size(), "the paste dropped into main");
        assertEquals(0, compsIn(sub, "AND Gate").size(), "not into the subcircuit on show");
        assertEquals(1, compsIn(sub, "OR Gate").size());
        Set<String> here = geometry();
        Set<String> hereSub = geometryOf(sub);
        // the recovery journal's replay: the same edit intents (no sim.*) in a new engine
        e.close();
        e = new InProcess();
        JsonObject n = e.client.callObject("file.new", params());
        fileId = n.get("fileId").getAsString();
        main = n.get("main").getAsString();
        String[] again = pastedInMainWithASub();
        e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", again[0], "lib", "Gates",
                "name", "OR Gate", "loc", xy(300, 300)));
        assertEquals(here, geometry());
        assertEquals(hereSub, geometryOf(again[0]));
    }

    Set<String> geometryOf(String circuitId) {
        String keep = main;
        main = circuitId;
        try {
            return geometry();
        } finally {
            main = keep;
        }
    }
}
