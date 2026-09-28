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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircNormalizer;

/**
 * 터널 색·Splitter 편집기 의도(edit.tunnelColor, edit.splitterEdit, edit.splitterSplit, N-12, D-150): 결과, 스냅숏의
 * {@code ext}, model.changed, 되돌리기·다시 실행 한 단계, 저장한 hcs:ext와 원조 Splitter 속성이 편집 동등성 골든
 * (tests/parity/13·14, Swing 앱이 같은 편집으로 저장한 파일)과 같음.
 */
class ExtEditTest {
    static final File PARITY = new File(Fixtures.CIRC_DIR.getParentFile(), "parity");

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
        Object[] all = new Object[kv.length + 4];
        all[0] = "fileId";
        all[1] = fileId;
        all[2] = "circuitId";
        all[3] = main;
        System.arraycopy(kv, 0, all, 4, kv.length);
        return e.client.callObject(method, params(all));
    }

    Client.Failure fails(String method, Object... kv) {
        Object[] all = new Object[kv.length + 4];
        all[0] = "fileId";
        all[1] = fileId;
        all[2] = "circuitId";
        all[3] = main;
        System.arraycopy(kv, 0, all, 4, kv.length);
        return e.client.fail(method, params(all));
    }

    String add(String lib, String name, int x, int y, Object... attrs) {
        Map<String, String> a = new TreeMap<>();
        for (int i = 0; i < attrs.length; i += 2) {
            a.put((String) attrs[i], (String) attrs[i + 1]);
        }
        return edit("edit.addComponent", "lib", lib, "name", name, "loc", xy(x, y), "attrs", a).get("id").getAsString();
    }

    JsonObject snapshot() {
        return e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
    }

    JsonObject part(String id) {
        return Fixtures.byId(snapshot().getAsJsonArray("components"), id);
    }

    JsonObject partAt(String name, int x, int y) {
        for (JsonElement c : snapshot().getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (o.get("name").getAsString().equals(name) && o.getAsJsonArray("loc").equals(json(x, y))) {
                return o;
            }
        }
        return null;
    }

    static JsonArray json(int x, int y) {
        JsonArray a = new JsonArray();
        a.add(x);
        a.add(y);
        return a;
    }

    String wireThrough(int x, int y) {
        for (JsonElement w : snapshot().getAsJsonArray("wires")) {
            JsonObject o = w.getAsJsonObject();
            JsonArray a = o.getAsJsonArray("a");
            JsonArray b = o.getAsJsonArray("b");
            int x0 = Math.min(a.get(0).getAsInt(), b.get(0).getAsInt());
            int x1 = Math.max(a.get(0).getAsInt(), b.get(0).getAsInt());
            int y0 = Math.min(a.get(1).getAsInt(), b.get(1).getAsInt());
            int y1 = Math.max(a.get(1).getAsInt(), b.get(1).getAsInt());
            if (x >= x0 && x <= x1 && y >= y0 && y <= y1) {
                return o.get("id").getAsString();
            }
        }
        return null;
    }

    static String color(JsonObject part) {
        return part.has("ext") && part.getAsJsonObject("ext").has("color")
                ? part.getAsJsonObject("ext").get("color").getAsString() : null;
    }

    static List<String> arms(JsonObject part) {
        List<String> ret = new ArrayList<>();
        if (part.has("ext")) {
            for (JsonElement x : part.getAsJsonObject("ext").getAsJsonArray("arms")) {
                ret.add(x.getAsString());
            }
        }
        return ret;
    }

    File save(String name) {
        File out = tmp.resolve(name).toFile();
        e.client.call("file.save", params("fileId", fileId, "path", out.getPath()));
        return out;
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** 파일의 한 부분(회로 하나, hcs:ext)을 D-006으로 정규화한 글. */
    static String section(String xml, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(xml);
        assertTrue(m.find(), "no " + regex);
        return CircNormalizer.normalize(m.group());
    }

    // ---- edit.tunnelColor ----

    @Test
    void aTunnelColorGoesToEveryTunnelOfThatNameAndUndoRedoIsOneStep() {
        String t1 = add("Wiring", "Tunnel", 200, 100, "label", "ctl");
        String t2 = add("Wiring", "Tunnel", 400, 100, "label", "ctl", "facing", "west");
        String t3 = add("Wiring", "Tunnel", 200, 200, "label", "data");
        assertNull(color(part(t1)), "no colour until the student picks one");
        int mark = e.client.mark();
        JsonObject r = edit("edit.tunnelColor", "id", t1, "color", "#e69f00");
        assertTrue(r.get("changed").getAsBoolean());
        JsonObject changed = e.client.awaitNotificationAfter(mark, "model.changed", p -> true);
        List<String> ids = new ArrayList<>();
        for (JsonElement x : changed.getAsJsonArray("added")) {
            ids.add(x.getAsJsonObject().get("id").getAsString());
            assertEquals("#E69F00", color(x.getAsJsonObject()));
        }
        assertEquals(List.of(t1, t2), ids, "the same parts, in place, with the new colour");
        assertEquals("#E69F00", color(part(t2)));
        assertNull(color(part(t3)));
        assertTrue(changed.get("dirty").getAsBoolean());
        // the same colour again: nothing to do, no undo step
        assertFalse(edit("edit.tunnelColor", "id", t2, "color", "#E69F00").get("changed").getAsBoolean());
        edit("edit.undo");
        assertNull(color(part(t1)));
        edit("edit.redo");
        assertEquals("#E69F00", color(part(t1)));
        // Automatic: the entry goes
        assertTrue(edit("edit.tunnelColor", "id", t1).get("changed").getAsBoolean());
        assertNull(color(part(t2)));
        assertFalse(edit("edit.tunnelColor", "id", t1).get("changed").getAsBoolean(), "already automatic");
    }

    @Test
    void tunnelColorsAreThePalettesAndOnlyForNamedTunnels() {
        String t = add("Wiring", "Tunnel", 200, 100, "label", "a");
        String nameless = add("Wiring", "Tunnel", 200, 300);
        String pin = add("Wiring", "Pin", 100, 100);
        assertEquals(-32602, fails("edit.tunnelColor", "id", t, "color", "#123456").code, "not a palette colour");
        assertEquals(-32602, fails("edit.tunnelColor", "id", t, "color", "red").code);
        assertEquals(-32602, fails("edit.tunnelColor", "id", nameless, "color", "#e69f00").code);
        assertEquals(-32602, fails("edit.tunnelColor", "id", pin, "color", "#e69f00").code);
        assertEquals(1, fails("edit.tunnelColor", "id", "k999999", "color", "#e69f00").code);
    }

    @Test
    void tunnelColorsSaveAsTheSwingAppSavedThem() throws Exception {
        // scene 13's colour part (tests/parity/13-ext-colors-groups-memos.intents)
        String t1 = add("Wiring", "Tunnel", 200, 100, "label", "ctl", "facing", "east");
        String t2 = add("Wiring", "Tunnel", 400, 100, "label", "ctl", "facing", "west");
        String t3 = add("Wiring", "Tunnel", 200, 200, "label", "data", "facing", "east", "width", "8");
        add("Wiring", "Tunnel", 400, 200, "label", "data", "facing", "west", "width", "8");
        String t5 = add("Wiring", "Tunnel", 200, 300, "label", "gone", "facing", "east");
        edit("edit.tunnelColor", "id", t1, "color", "#e69f00");
        edit("edit.tunnelColor", "id", t3, "color", "#56b4e9");
        edit("edit.tunnelColor", "id", t3, "color", "#009e73");
        edit("edit.tunnelColor", "id", t5, "color", "#d55e00");
        edit("edit.tunnelColor", "id", t1);
        edit("edit.tunnelColor", "id", t2, "color", "#332288");
        edit("edit.delete", "ids", new Object[] {t5});
        String saved = read(save("colors.circ"));
        assertTrue(saved.contains("<hcs:tunnel label=\"data\" color=\"#009E73\"/>\n      <hcs:tunnel label=\"ctl\" color=\"#332288\"/>"),
                saved);
        assertFalse(saved.contains("gone\" color"), "a deleted tunnel's colour is pruned on save");
        String golden = read(new File(PARITY, "13-ext-colors-groups-memos.circ"));
        assertTrue(golden.contains("<hcs:tunnel label=\"data\" color=\"#009E73\"/>\n      <hcs:tunnel label=\"ctl\" color=\"#332288\"/>"));
    }

    // ---- edit.splitterEdit / edit.splitterSplit ----

    /** Scene 14 of the edit parity goldens, by the engine's intents: the same circuit and hcs:ext as Swing saved. */
    @Test
    void theSplitterEditorsIntentsGiveTheGoldenOfScene14() throws Exception {
        add("Wiring", "Pin", 200, 200, "width", "8", "label", "bus");
        edit("edit.addWire", "points", new Object[] {xy(200, 200), xy(400, 200)});
        JsonObject split = edit("edit.splitterSplit", "wire", wireThrough(300, 200), "at", xy(300, 200), "ranges",
                "7:4, 3:0", "names", new Object[] {"hi", "lo"});
        assertTrue(split.get("changed").getAsBoolean());
        JsonObject made = part(split.get("id").getAsString());
        assertEquals(json(300, 200), made.getAsJsonArray("loc"));
        assertEquals(List.of("hi", "lo"), arms(made));
        String sp = add("Wiring", "Splitter", 400, 400, "incoming", "32");
        edit("edit.splitterEdit", "id", sp, "ranges", "31:26 op, 25:21 rs, 20:16 rt, 15:11 rd, 10:6 shamt, 5:0 funct");
        JsonObject r = partAt("Splitter", 400, 400);
        assertEquals("6", r.getAsJsonObject("attrs").get("fanout").getAsString());
        assertEquals(List.of("op", "rs", "rt", "rd", "shamt", "funct"), arms(r), "names written in the ranges");
        edit("edit.splitterEdit", "id", r.get("id").getAsString(), "ranges", "31:26, 25:21, 20:16, 15:0", "names",
                new Object[] {"op", "rs", "rt", "imm"});
        String sp2 = add("Wiring", "Splitter", 400, 700, "incoming", "8");
        edit("edit.splitterEdit", "id", sp2, "ranges", "0, 1, 7:2", "lsbTop", true, "names",
                new Object[] {"b0", "b1", "rest"});
        String saved = read(save("splitters.circ"));
        String golden = read(new File(PARITY, "14-splitter-editor.circ"));
        assertEquals(section(golden, "<circuit name=\"main\">.*?</circuit>"),
                section(saved, "<circuit name=\"main\">.*?</circuit>"));
        assertEquals(section(golden, "<hcs:ext .*?</hcs:ext>"), section(saved, "<hcs:ext .*?</hcs:ext>"));
    }

    @Test
    void anEditIsOneUndoStepNamesAloneChangeOnlyTheExtAndNothingNewChangesNothing() {
        String sp = add("Wiring", "Splitter", 400, 400, "incoming", "8");
        JsonObject before = part(sp);
        edit("edit.splitterEdit", "id", sp, "ranges", "7:4 hi, 3:0 lo");
        JsonObject named = partAt("Splitter", 400, 400);
        assertEquals(List.of("hi", "lo"), arms(named));
        // the same ranges and names again: nothing
        assertFalse(edit("edit.splitterEdit", "id", named.get("id").getAsString(), "ranges", "7:4, 3:0").get("changed")
                .getAsBoolean(), "an arm without a name keeps its old name while the count stays");
        // only a name: the same part in place, only its ext
        int mark = e.client.mark();
        edit("edit.splitterEdit", "id", named.get("id").getAsString(), "ranges", "7:4, 3:0", "names",
                new Object[] {"high"});
        JsonObject changed = e.client.awaitNotificationAfter(mark, "model.changed", p -> true);
        assertEquals(0, changed.getAsJsonArray("removed").size(), "names alone do not replace the part");
        assertEquals(List.of("high", "lo"), arms(partAt("Splitter", 400, 400)));
        edit("edit.undo");
        assertEquals(List.of("hi", "lo"), arms(partAt("Splitter", 400, 400)));
        edit("edit.undo");
        JsonObject back = partAt("Splitter", 400, 400);
        assertEquals(before.getAsJsonObject("attrs"), back.getAsJsonObject("attrs"));
        assertEquals(List.of(), arms(back), "no names before the first edit");
        edit("edit.redo");
        assertEquals(List.of("hi", "lo"), arms(partAt("Splitter", 400, 400)));
    }

    @Test
    void splitterEditsTheWireGuardRefusesChangeNothing() {
        // a wire where an arm of the 4-arm splitter would land, at no end of the 2-arm one
        String sp = add("Wiring", "Splitter", 400, 400, "incoming", "8", "fanout", "4");
        List<JsonArray> four = new ArrayList<>();
        for (JsonElement q : part(sp).getAsJsonArray("ports")) {
            four.add(q.getAsJsonObject().getAsJsonArray("loc"));
        }
        edit("edit.splitterEdit", "id", sp, "ranges", "7:4, 3:0");
        List<JsonArray> two = new ArrayList<>();
        for (JsonElement q : partAt("Splitter", 400, 400).getAsJsonArray("ports")) {
            two.add(q.getAsJsonObject().getAsJsonArray("loc"));
        }
        JsonArray spot = four.stream().filter(p -> !two.contains(p)).findFirst().orElseThrow();
        int x = spot.get(0).getAsInt();
        int y = spot.get(1).getAsInt();
        edit("edit.addWire", "points", new Object[] {xy(x, y), xy(x + 60, y)});
        String now = partAt("Splitter", 400, 400).get("id").getAsString();
        JsonObject r = edit("edit.splitterEdit", "id", now, "ranges", "7:6, 5:4, 3:2, 1:0");
        assertFalse(r.get("changed").getAsBoolean());
        assertEquals("refused", r.get("outcome").getAsString());
        assertEquals("2", partAt("Splitter", 400, 400).getAsJsonObject("attrs").get("fanout").getAsString());
    }

    @Test
    void splitterIntentsCheckWhatTheyAreGiven() {
        String pin = add("Wiring", "Pin", 100, 100);
        String sp = add("Wiring", "Splitter", 400, 400, "incoming", "8");
        assertEquals(-32602, fails("edit.splitterEdit", "id", pin, "ranges", "7:0").code, "not a splitter");
        assertEquals(-32602, fails("edit.splitterEdit", "id", sp, "ranges", "9:0").code, "outside the width");
        assertEquals(-32602, fails("edit.splitterEdit", "id", sp, "ranges", "7:4, 4:0").code, "a bit in two arms");
        assertEquals(-32602, fails("edit.splitterEdit", "id", sp, "ranges", "what").code);
        assertEquals(-32602, fails("edit.splitterEdit", "id", sp, "ranges", "7:4, 3:0", "names",
                new Object[] {"a", "b", "c"}).code, "more names than arms");
        edit("edit.addWire", "points", new Object[] {xy(100, 100), xy(100, 200)});
        assertEquals(-32602, fails("edit.splitterSplit", "wire", wireThrough(100, 150), "at", xy(100, 150), "ranges",
                "0").code, "a one-bit wire has nothing to split");
        assertEquals(-32602, fails("edit.splitterSplit", "wire", sp, "at", xy(400, 400), "ranges", "0").code,
                "not a wire");
    }

    @Test
    void takeOneBitIsASplitWithOneArmAndTheNewSplitterIsOnTheWire() {
        add("Wiring", "Pin", 200, 200, "width", "32");
        edit("edit.addWire", "points", new Object[] {xy(200, 200), xy(500, 200)});
        // a point off the wire's grid goes to the nearest grid point on the wire
        JsonObject r = edit("edit.splitterSplit", "wire", wireThrough(330, 200), "at", xy(333, 204), "ranges", "5");
        JsonObject s = part(r.get("id").getAsString());
        assertEquals(json(330, 200), s.getAsJsonArray("loc"));
        assertEquals("1", s.getAsJsonObject("attrs").get("fanout").getAsString());
        assertEquals("0", s.getAsJsonObject("attrs").get("bit5").getAsString());
        assertEquals("none", s.getAsJsonObject("attrs").get("bit4").getAsString());
        assertFalse(s.has("ext"), "a split with no names saves no names");
        edit("edit.undo");
        assertNull(partAt("Splitter", 330, 200));
    }

    @Test
    void aReadOnlyFileRefusesThem() throws Exception {
        File f = new File(Fixtures.CIRC_DIR, "demo-datapath.circ");
        JsonObject r = e.client.callObject("file.open", params("path", f.getPath(), "readOnly", true));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        JsonObject tunnel = Fixtures.byName(snapshot().getAsJsonArray("components"), "Tunnel").get(0);
        Client.Failure x = fails("edit.tunnelColor", "id", tunnel.get("id").getAsString(), "color", "#e69f00");
        assertEquals(3, x.code);
        assertEquals("readOnly", x.reason());
        JsonObject splitter = Fixtures.byName(snapshot().getAsJsonArray("components"), "Splitter").get(0);
        assertNotNull(splitter);
        assertEquals(3, fails("edit.splitterEdit", "id", splitter.get("id").getAsString(), "ranges", "31:0").code);
    }

    @Test
    void theSnapshotCarriesTheSavedArmNamesAndColours() throws Exception {
        File f = new File(Fixtures.CIRC_DIR, "demo-datapath.circ");
        JsonObject r = e.client.callObject("file.open", params("path", f.getPath(), "readOnly", true));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        JsonObject s = partAt("Splitter", 620, 200);
        assertEquals(List.of("op", "rs", "rt", "rd", "shamt", "funct"), arms(s));
        File ext = new File(PARITY, "inputs/ext-sample.circ");
        JsonObject o = e.client.callObject("file.open", params("path", ext.getPath(), "readOnly", true));
        fileId = o.get("fileId").getAsString();
        main = o.get("main").getAsString();
        int coloured = 0;
        for (JsonObject t : Fixtures.byName(snapshot().getAsJsonArray("components"), "Tunnel")) {
            if (color(t) != null) {
                coloured++;
                assertTrue(color(t).matches("#[0-9A-F]{6}"), color(t));
            }
        }
        assertTrue(coloured >= 2, "the sample's coloured tunnels: " + coloured);
    }
}
