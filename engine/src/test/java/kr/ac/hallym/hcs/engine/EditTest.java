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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** edit.*: 의도마다 결과, model.changed, 되돌리기·다시 실행 한 단계. */
class EditTest {
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

    /** 빈 새 파일(원조 File › New). */
    void fresh() {
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
    }

    /** base 회로를 만들어 저장하고 연다. */
    File openBuilt(java.util.function.Consumer<CircuitBuilder> build) throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        build.accept(b);
        b.commit();
        File out = Files.createTempFile(tmp, "base", ".circ").toFile();
        CircuitBuilder.save(f, out);
        JsonObject r = e.client.callObject("file.open", params("path", out.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        return out;
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

    JsonObject snapshot() {
        return e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
    }

    /** 선을 "a-b" 글자로. */
    static Set<String> wires(JsonObject snap) {
        Set<String> ret = new TreeSet<>();
        for (JsonElement w : snap.getAsJsonArray("wires")) {
            JsonObject o = w.getAsJsonObject();
            ret.add(o.getAsJsonArray("a") + "-" + o.getAsJsonArray("b"));
        }
        return ret;
    }

    static String wire(int x0, int y0, int x1, int y1) {
        return "[" + x0 + "," + y0 + "]-[" + x1 + "," + y1 + "]";
    }

    JsonObject changedAfter(int mark) {
        return e.client.awaitNotificationAfter(mark, "model.changed", p -> p.get("fileId").getAsString().equals(fileId));
    }

    // ---- addComponent ----

    @Test
    void addComponentReturnsTheIdAndPublishesItThenUndoRedo() {
        fresh();
        int mark = e.client.mark();
        JsonObject r = edit("edit.addComponent", "lib", "Gates", "name", "AND Gate", "loc", xy(200, 200));
        assertTrue(r.get("changed").getAsBoolean());
        String id = r.get("id").getAsString();
        JsonObject ch = changedAfter(mark);
        assertEquals(main, ch.get("circuitId").getAsString());
        assertEquals(new JsonArray(), ch.getAsJsonArray("removed"));
        assertEquals(1, ch.getAsJsonArray("added").size());
        JsonObject added = ch.getAsJsonArray("added").get(0).getAsJsonObject();
        assertEquals(id, added.get("id").getAsString());
        assertEquals("Gates", added.get("lib").getAsString());
        assertEquals("AND Gate", added.get("name").getAsString());
        assertEquals("east", added.get("facing").getAsString());
        assertTrue(ch.get("dirty").getAsBoolean());
        assertTrue(ch.has("nets") && ch.has("junctions"));
        assertEquals(6, ch.getAsJsonArray("nets").size(), "five inputs (2.7.1 default) and the output, each alone");

        int m2 = e.client.mark();
        assertTrue(e.client.callObject("edit.undo", params("fileId", fileId)).get("changed").getAsBoolean());
        JsonObject undone = changedAfter(m2);
        assertEquals(id, undone.getAsJsonArray("removed").get(0).getAsString());
        assertEquals(0, snapshot().getAsJsonArray("components").size());
        assertFalse(undone.get("dirty").getAsBoolean(), "back to the saved state");

        int m3 = e.client.mark();
        assertTrue(e.client.callObject("edit.redo", params("fileId", fileId)).get("changed").getAsBoolean());
        JsonObject redone = changedAfter(m3);
        assertEquals(1, redone.getAsJsonArray("added").size());
        assertEquals(1, snapshot().getAsJsonArray("components").size());
        assertFalse(e.client.callObject("edit.redo", params("fileId", fileId)).get("changed").getAsBoolean());
    }

    @Test
    void addComponentAppliesAttributesInCircStrings() {
        fresh();
        JsonObject r = edit("edit.addComponent", "lib", "Wiring", "name", "Pin", "loc", xy(100, 100), "attrs",
                params("width", "8", "facing", "west", "output", "true", "label", "result"));
        JsonObject c = Fixtures.byId(snapshot().getAsJsonArray("components"), r.get("id").getAsString());
        JsonObject attrs = c.getAsJsonObject("attrs");
        assertEquals("8", attrs.get("width").getAsString());
        assertEquals("west", attrs.get("facing").getAsString());
        assertEquals("true", attrs.get("output").getAsString());
        assertEquals("result", attrs.get("label").getAsString());
        assertEquals("west", c.get("facing").getAsString());
        JsonObject port = c.getAsJsonArray("ports").get(0).getAsJsonObject();
        assertEquals(8, port.get("width").getAsInt());
        assertEquals("in", port.get("dir").getAsString(), "an output pin reads its net");
    }

    @Test
    void addComponentErrors() {
        fresh();
        assertEquals(1, e.client.fail("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "Gates", "name", "No Such Gate", "loc", xy(100, 100))).code);
        assertEquals(1, e.client.fail("edit.addComponent", params("fileId", fileId, "circuitId", "c999999", "lib",
                "Gates", "name", "AND Gate", "loc", xy(100, 100))).code);
        Client.Failure neg = e.client.fail("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "Gates", "name", "AND Gate", "loc", xy(10, 10)));
        assertEquals(3, neg.code);
        assertEquals("negativeCoord", neg.reason());
        assertEquals(-32602, e.client.fail("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "Gates", "name", "AND Gate", "loc", xy(100, 100), "attrs", params("nope", "1"))).code);
        assertEquals(-32602, e.client.fail("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "Gates", "name", "AND Gate", "loc", xy(100, 100), "attrs", params("inputs", "many"))).code);
        edit("edit.addComponent", "lib", "Wiring", "name", "Constant", "loc", xy(300, 300));
        Client.Failure clash = e.client.fail("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "Wiring", "name", "Constant", "loc", xy(300, 300)));
        assertEquals(3, clash.code, "two outputs on one point");
        assertEquals("exclusive", clash.reason());
        assertEquals(1, snapshot().getAsJsonArray("components").size(), "failed intents change nothing");
    }

    @Test
    void placingTheFirstMipsPartPutsTheLibraryInTheFileAndUndoTakesItOut() throws Exception {
        fresh();
        JsonObject r = edit("edit.addComponent", "lib", "kr.ac.hallym.hcs.mips.MipsLibrary", "name", "Data Memory",
                "loc", xy(300, 300));
        JsonObject c = Fixtures.byId(snapshot().getAsJsonArray("components"), r.get("id").getAsString());
        assertEquals("kr.ac.hallym.hcs.mips.MipsLibrary", c.get("lib").getAsString());
        File out = tmp.resolve("mips.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", out.getPath()));
        String xml = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml.contains("jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary"), "V-01 shadow descriptor");
        e.client.call("edit.undo", params("fileId", fileId));
        File out2 = tmp.resolve("empty.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", out2.getPath()));
        assertFalse(new String(Files.readAllBytes(out2.toPath()), StandardCharsets.UTF_8).contains("MipsLibrary"));
    }

    @Test
    void subcircuitsCanBePlacedButNotInsideThemselves() throws Exception {
        JsonObject r = e.client.callObject("file.open", params("path",
                new File(Fixtures.CIRC_DIR, "subcircuit.circ").getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        String sub = null;
        String subName = null;
        for (JsonElement c : r.getAsJsonArray("circuits")) {
            if (!c.getAsJsonObject().get("circuitId").getAsString().equals(main)) {
                sub = c.getAsJsonObject().get("circuitId").getAsString();
                subName = c.getAsJsonObject().get("name").getAsString();
            }
        }
        assertNotNull(sub);
        JsonObject added = edit("edit.addComponent", "lib", null, "name", subName, "loc", xy(900, 900));
        JsonObject c = Fixtures.byId(snapshot().getAsJsonArray("components"), added.get("id").getAsString());
        assertEquals(sub, c.get("subcircuit").getAsString());
        Client.Failure f = e.client.fail("edit.addComponent", params("fileId", fileId, "circuitId", sub, "lib", null,
                "name", subName, "loc", xy(900, 900)));
        assertEquals(3, f.code);
        assertEquals("circular", f.reason());
    }

    // ---- addWire ----

    @Test
    void wiresMergeSplitAndShortenLikeTheWiringTool() {
        fresh();
        edit("edit.addWire", "points", new Object[] {xy(100, 100), xy(200, 100)});
        edit("edit.addWire", "points", new Object[] {xy(200, 100), xy(300, 100)});
        assertEquals(Set.of(wire(100, 100, 300, 100)), wires(snapshot()), "collinear wires merge");
        edit("edit.addWire", "points", new Object[] {xy(200, 100), xy(200, 200)});
        JsonObject s = snapshot();
        assertEquals(Set.of(wire(100, 100, 200, 100), wire(200, 100, 300, 100), wire(200, 100, 200, 200)), wires(s),
                "a T splits the wire");
        assertEquals("[[200,100]]", s.getAsJsonArray("junctions").toString());
        assertEquals(1, s.getAsJsonArray("nets").size());
        // 끝에서 선을 따라 되돌아가면 줄인다
        JsonObject r = edit("edit.addWire", "points", new Object[] {xy(200, 200), xy(200, 150)});
        assertEquals("shortened", r.get("outcome").getAsString());
        assertTrue(wires(snapshot()).contains(wire(200, 100, 200, 150)));
        JsonObject gone = edit("edit.addWire", "points", new Object[] {xy(200, 150), xy(200, 100)});
        assertEquals("removed", gone.get("outcome").getAsString());
        assertEquals(Set.of(wire(100, 100, 300, 100)), wires(snapshot()), "the rest merges back");
    }

    @Test
    void lShapedWiresFollowTheMiddlePoint() {
        fresh();
        edit("edit.addWire", "points", new Object[] {xy(100, 100), xy(200, 100), xy(200, 200)});
        assertEquals(Set.of(wire(100, 100, 200, 100), wire(200, 100, 200, 200)), wires(snapshot()));
        edit("edit.addWire", "points", new Object[] {xy(400, 100), xy(400, 200), xy(500, 200)});
        assertTrue(wires(snapshot()).containsAll(Set.of(wire(400, 100, 400, 200), wire(400, 200, 500, 200))));
        assertEquals(-32602, e.client.fail("edit.addWire", params("fileId", fileId, "circuitId", main, "points",
                new Object[] {xy(100, 300), xy(200, 400)})).code, "an L needs its corner");
        assertEquals(-32602, e.client.fail("edit.addWire", params("fileId", fileId, "circuitId", main, "points",
                new Object[] {xy(100, 300), xy(150, 350), xy(200, 400)})).code);
        assertEquals(-32602, e.client.fail("edit.addWire", params("fileId", fileId, "circuitId", main, "points",
                new Object[] {xy(100, 300)})).code);
        assertFalse(edit("edit.addWire", "points", new Object[] {xy(100, 300), xy(100, 300)}).get("changed")
                .getAsBoolean());
    }

    @Test
    void oneUndoRemovesAnLShapedWire() {
        fresh();
        edit("edit.addWire", "points", new Object[] {xy(100, 100), xy(200, 100), xy(200, 200)});
        e.client.call("edit.undo", params("fileId", fileId));
        assertEquals(Set.of(), wires(snapshot()));
    }

    // ---- move ----

    @Test
    void movingAGateKeepsItsWireConnected() throws Exception {
        openBuilt(b -> {
            Component pin = b.add("Wiring", "Pin", 100, 200, "tristate", "false");
            Component not = b.add("Gates", "NOT Gate", 300, 200);
            b.wire(CircuitBuilder.port(pin, 0), CircuitBuilder.port(not, 1));
        });
        JsonObject before = snapshot();
        JsonObject not = Fixtures.byName(before.getAsJsonArray("components"), "NOT Gate").get(0);
        JsonObject pin = Fixtures.byName(before.getAsJsonArray("components"), "Pin").get(0);
        String netBefore = Fixtures.netOf(before.getAsJsonArray("nets"), pin.get("id").getAsString(), 0);
        assertEquals(netBefore, Fixtures.netOf(before.getAsJsonArray("nets"), not.get("id").getAsString(), 1));

        int mark = e.client.mark();
        JsonObject r = edit("edit.move", "ids", new Object[] {not.get("id").getAsString()}, "dx", 0, "dy", 50);
        assertTrue(r.get("changed").getAsBoolean());
        assertEquals("moved", r.get("outcome").getAsString());
        JsonObject ch = changedAfter(mark);
        assertTrue(contains(ch.getAsJsonArray("removed"), not.get("id").getAsString()),
                "Logisim replaces a moved component: old id removed");
        JsonObject after = snapshot();
        JsonObject moved = Fixtures.byName(after.getAsJsonArray("components"), "NOT Gate").get(0);
        assertEquals("[300,250]", moved.getAsJsonArray("loc").toString());
        assertEquals(Fixtures.netOf(after.getAsJsonArray("nets"), pin.get("id").getAsString(), 0),
                Fixtures.netOf(after.getAsJsonArray("nets"), moved.get("id").getAsString(), 1), "still connected");
        e.client.call("edit.undo", params("fileId", fileId));
        assertEquals(wires(before), wires(snapshot()), "one undo restores the move and its wires");
    }

    @Test
    void movingWithoutConnectLeavesTheWiresBehind() throws Exception {
        openBuilt(b -> {
            Component pin = b.add("Wiring", "Pin", 100, 200, "tristate", "false");
            Component not = b.add("Gates", "NOT Gate", 300, 200);
            b.wire(CircuitBuilder.port(pin, 0), CircuitBuilder.port(not, 1));
        });
        JsonObject before = snapshot();
        String not = Fixtures.byName(before.getAsJsonArray("components"), "NOT Gate").get(0).get("id").getAsString();
        JsonObject r = edit("edit.move", "ids", new Object[] {not}, "dx", 0, "dy", 50, "connect", false);
        assertTrue(r.get("changed").getAsBoolean());
        assertEquals(wires(before), wires(snapshot()), "the wire stays where it was");
        assertFalse(edit("edit.move", "ids", new Object[] {}, "dx", 0, "dy", 0).get("changed").getAsBoolean());
    }

    @Test
    void moveOntoAnotherDriverIsRefused() throws Exception {
        fresh();
        String a = edit("edit.addComponent", "lib", "Wiring", "name", "Constant", "loc", xy(300, 300)).get("id")
                .getAsString();
        edit("edit.addComponent", "lib", "Wiring", "name", "Constant", "loc", xy(300, 400));
        Client.Failure f = e.client.fail("edit.move", params("fileId", fileId, "circuitId", main, "ids",
                new Object[] {a}, "dx", 0, "dy", 100));
        assertEquals(3, f.code);
        assertEquals("exclusive", f.reason());
    }

    // ---- delete ----

    @Test
    void deleteThenUndoThenRedo() throws Exception {
        fresh();
        String gate = edit("edit.addComponent", "lib", "Gates", "name", "OR Gate", "loc", xy(300, 300)).get("id")
                .getAsString();
        edit("edit.addWire", "points", new Object[] {xy(100, 100), xy(200, 100)});
        String w = snapshot().getAsJsonArray("wires").get(0).getAsJsonObject().get("id").getAsString();
        int mark = e.client.mark();
        assertTrue(edit("edit.delete", "ids", new Object[] {gate, w}).get("changed").getAsBoolean());
        JsonObject ch = changedAfter(mark);
        Set<String> removed = new HashSet<>();
        ch.getAsJsonArray("removed").forEach(x -> removed.add(x.getAsString()));
        assertEquals(Set.of(gate, w), removed);
        assertEquals(0, snapshot().getAsJsonArray("components").size());
        assertEquals(0, snapshot().getAsJsonArray("wires").size());
        e.client.call("edit.undo", params("fileId", fileId));
        assertEquals(1, snapshot().getAsJsonArray("components").size());
        assertEquals(1, snapshot().getAsJsonArray("wires").size());
        e.client.call("edit.redo", params("fileId", fileId));
        assertEquals(0, snapshot().getAsJsonArray("components").size(), "redo deletes the same things again");
        assertEquals(1, e.client.fail("edit.delete", params("fileId", fileId, "circuitId", main, "ids",
                new Object[] {gate})).code, "a deleted id is gone");
    }

    // ---- setAttr ----

    @Test
    void setAttrChangesInPlaceAndUndoes() throws Exception {
        fresh();
        String gate = edit("edit.addComponent", "lib", "Gates", "name", "AND Gate", "loc", xy(300, 300)).get("id")
                .getAsString();
        JsonObject before = Fixtures.byId(snapshot().getAsJsonArray("components"), gate);
        int mark = e.client.mark();
        assertTrue(edit("edit.setAttr", "ids", new Object[] {gate}, "attr", "inputs", "value", "3").get("changed")
                .getAsBoolean());
        JsonObject ch = changedAfter(mark);
        JsonObject after = Fixtures.byId(ch.getAsJsonArray("added"), gate);
        assertNotNull(after, "same id, changed in place");
        assertEquals("3", after.getAsJsonObject("attrs").get("inputs").getAsString());
        assertEquals(4, after.getAsJsonArray("ports").size());
        assertNotEquals(before.getAsJsonArray("ports").size(), after.getAsJsonArray("ports").size());
        edit("edit.setAttr", "ids", new Object[] {gate}, "attr", "facing", "value", "south");
        assertEquals("south", Fixtures.byId(snapshot().getAsJsonArray("components"), gate).get("facing").getAsString());
        e.client.call("edit.undo", params("fileId", fileId));
        e.client.call("edit.undo", params("fileId", fileId));
        JsonObject back = Fixtures.byId(snapshot().getAsJsonArray("components"), gate);
        assertEquals(before.getAsJsonObject("attrs"), back.getAsJsonObject("attrs"));
        assertEquals(-32602, e.client.fail("edit.setAttr", params("fileId", fileId, "circuitId", main, "ids",
                new Object[] {gate}, "attr", "inputs", "value", "lots")).code);
        assertEquals(-32602, e.client.fail("edit.setAttr", params("fileId", fileId, "circuitId", main, "ids",
                new Object[] {gate}, "attr", "nope", "value", "1")).code);
    }

    @Test
    void setAttrOnSeveralComponentsIsOneStep() {
        fresh();
        String a = edit("edit.addComponent", "lib", "Gates", "name", "AND Gate", "loc", xy(300, 300)).get("id")
                .getAsString();
        String b = edit("edit.addComponent", "lib", "Gates", "name", "OR Gate", "loc", xy(300, 500)).get("id")
                .getAsString();
        edit("edit.setAttr", "ids", new Object[] {a, b}, "attr", "size", "value", "30");
        for (String id : List.of(a, b)) {
            assertEquals("30", Fixtures.byId(snapshot().getAsJsonArray("components"), id).getAsJsonObject("attrs")
                    .get("size").getAsString());
        }
        e.client.call("edit.undo", params("fileId", fileId));
        for (String id : List.of(a, b)) {
            assertEquals("50", Fixtures.byId(snapshot().getAsJsonArray("components"), id).getAsJsonObject("attrs")
                    .get("size").getAsString());
        }
    }

    @Test
    void undoWithNothingToUndo() {
        fresh();
        assertFalse(e.client.callObject("edit.undo", params("fileId", fileId)).get("changed").getAsBoolean());
    }

    @Test
    void idsAreForgottenWhenObjectsLeaveTheModel() throws Exception {
        fresh();
        for (int i = 0; i < 5; i++) {
            edit("edit.addComponent", "lib", "Gates", "name", "AND Gate", "loc", xy(300, 100 + 100 * i));
        }
        for (int i = 0; i < 5; i++) {
            e.client.call("edit.undo", params("fileId", fileId));
        }
        int size = e.onEngine(() -> e.engine.files().get(fileId).ids().size());
        assertEquals(1, size, "only the circuit id remains");
    }

    static boolean contains(JsonArray a, String s) {
        for (JsonElement x : a) {
            if (x.getAsString().equals(s)) {
                return true;
            }
        }
        return false;
    }

    static List<String> list(JsonArray a) {
        List<String> ret = new ArrayList<>();
        a.forEach(x -> ret.add(x.getAsString()));
        return ret;
    }

    static Location loc(JsonArray a) {
        return Location.create(a.get(0).getAsInt(), a.get(1).getAsInt());
    }
}
