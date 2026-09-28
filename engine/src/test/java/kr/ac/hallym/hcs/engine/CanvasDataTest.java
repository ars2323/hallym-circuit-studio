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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 화면 캔버스가 엔진에서 받는 것(N-05, D-137): 서브회로 인스턴스의 모양(appearance)과 sim.values의 몸체 상태(bodies).
 */
class CanvasDataTest {
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

    JsonObject open(File f) throws Exception {
        File copy = tmp.resolve(f.getName()).toFile();
        Files.copy(f.toPath(), copy.toPath());
        JsonObject r = e.client.callObject("file.open", params("path", copy.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        return r;
    }

    JsonObject snapshot(String circuitId) {
        return e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", circuitId));
    }

    static JsonObject named(JsonObject snap, String name) {
        for (JsonElement c : snap.getAsJsonArray("components")) {
            if (c.getAsJsonObject().get("name").getAsString().equals(name)) {
                return c.getAsJsonObject();
            }
        }
        throw new AssertionError("no " + name);
    }

    /** 원조 SubcircuitFactory·CircuitAppearance.paintSubcircuit의 변환: loc + R(θ)(p − anchor). */
    static int[] place(JsonObject comp, JsonObject app, JsonArray p) {
        double theta = radians(app.get("facing").getAsString()) - radians(comp.get("facing").getAsString());
        int dx = p.get(0).getAsInt() - app.getAsJsonArray("anchor").get(0).getAsInt();
        int dy = p.get(1).getAsInt() - app.getAsJsonArray("anchor").get(1).getAsInt();
        long x = Math.round(dx * Math.cos(theta) - dy * Math.sin(theta));
        long y = Math.round(dx * Math.sin(theta) + dy * Math.cos(theta));
        JsonArray loc = comp.getAsJsonArray("loc");
        return new int[] {(int) (loc.get(0).getAsInt() + x), (int) (loc.get(1).getAsInt() + y)};
    }

    static double radians(String facing) {
        switch (facing) {
            case "west":
                return Math.PI;
            case "north":
                return Math.PI / 2;
            case "south":
                return -Math.PI / 2;
            default:
                return 0;
        }
    }

    static Set<String> portLocs(JsonObject comp) {
        Set<String> s = new HashSet<>();
        for (JsonElement p : comp.getAsJsonArray("ports")) {
            JsonArray l = p.getAsJsonObject().getAsJsonArray("loc");
            s.add(l.get(0).getAsInt() + "," + l.get(1).getAsInt());
        }
        return s;
    }

    static void assertPortsLandOnTheEnginePorts(JsonObject comp) {
        JsonObject app = comp.getAsJsonObject("appearance");
        assertNotNull(app, comp.get("name").getAsString());
        JsonArray ports = app.getAsJsonArray("ports");
        assertEquals(comp.getAsJsonArray("ports").size(), ports.size());
        Set<String> want = portLocs(comp);
        Set<String> got = new HashSet<>();
        for (JsonElement p : ports) {
            int[] at = place(comp, app, p.getAsJsonObject().getAsJsonArray("at"));
            got.add(at[0] + "," + at[1]);
        }
        assertEquals(want, got, comp.get("name") + " facing " + comp.get("facing"));
    }

    @Test
    void customAppearanceShapesAndPortsInEveryFacing() throws Exception {
        JsonObject f = open(new File(Fixtures.CIRC_DIR, "demo-datapath.circ"));
        JsonObject regfile = named(snapshot(main), "regfile");
        JsonObject app = regfile.getAsJsonObject("appearance");
        assertFalse(app.get("default").getAsBoolean());
        assertEquals("rect", app.getAsJsonArray("shapes").get(0).getAsJsonObject().get("tag").getAsString());
        JsonObject title = app.getAsJsonArray("shapes").get(1).getAsJsonObject();
        assertEquals("text", title.get("tag").getAsString());
        assertEquals("regfile", title.get("text").getAsString());
        assertEquals("middle", title.getAsJsonObject("attrs").get("text-anchor").getAsString());
        assertEquals(regfile.get("subcircuit").getAsString(), f.getAsJsonArray("circuits").get(1).getAsJsonObject()
                .get("circuitId").getAsString());
        assertPortsLandOnTheEnginePorts(regfile);
        for (String facing : CanvasFixtures.FACINGS) {
            JsonObject attrs = new JsonObject();
            attrs.addProperty("facing", facing);
            String id = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "name",
                    "alu", "loc", new int[] {2000, 2000}, "attrs", attrs)).get("id").getAsString();
            JsonObject placed = null;
            for (JsonElement c : snapshot(main).getAsJsonArray("components")) {
                if (c.getAsJsonObject().get("id").getAsString().equals(id)) {
                    placed = c.getAsJsonObject();
                }
            }
            assertNotNull(placed);
            assertPortsLandOnTheEnginePorts(placed);
            e.client.callObject("edit.undo", params("fileId", fileId));
        }
    }

    @Test
    void defaultAppearanceIsTheOriginalBoxWithItsNotch() throws Exception {
        open(new File(Fixtures.CIRC_DIR, "subcircuit.circ"));
        JsonObject snap = snapshot(main);
        JsonObject inst = null;
        for (JsonElement c : snap.getAsJsonArray("components")) {
            if (c.getAsJsonObject().has("appearance")) {
                inst = c.getAsJsonObject();
            }
        }
        assertNotNull(inst);
        JsonObject app = inst.getAsJsonObject("appearance");
        assertTrue(app.get("default").getAsBoolean());
        JsonArray shapes = app.getAsJsonArray("shapes");
        // 원조 DefaultAppearance: 회색 홈(곡선)과 굵기 2 상자
        assertEquals("path", shapes.get(0).getAsJsonObject().get("tag").getAsString());
        assertTrue(shapes.get(0).getAsJsonObject().getAsJsonObject("attrs").get("d").getAsString().contains("Q"));
        assertEquals("rect", shapes.get(1).getAsJsonObject().get("tag").getAsString());
        assertPortsLandOnTheEnginePorts(inst);
        for (JsonElement p : app.getAsJsonArray("ports")) {
            assertTrue(p.getAsJsonObject().has("pin"));
        }
    }

    @Test
    void aNewPinInTheSubcircuitComesAsAChangedInstance() throws Exception {
        JsonObject f = open(new File(Fixtures.CIRC_DIR, "subcircuit.circ"));
        String sub = null;
        for (JsonElement c : f.getAsJsonArray("circuits")) {
            String id = c.getAsJsonObject().get("circuitId").getAsString();
            if (!id.equals(main)) {
                sub = id;
            }
        }
        int before = 0;
        String instId = null;
        for (JsonElement c : snapshot(main).getAsJsonArray("components")) {
            if (c.getAsJsonObject().has("appearance")) {
                before = c.getAsJsonObject().getAsJsonObject("appearance").getAsJsonArray("ports").size();
                instId = c.getAsJsonObject().get("id").getAsString();
            }
        }
        int mark = e.client.mark();
        JsonObject attrs = new JsonObject();
        attrs.addProperty("label", "extra");
        e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", sub, "lib", "Wiring",
                "name", "Pin", "loc", new int[] {600, 600}, "attrs", attrs));
        final String mainId = main;
        JsonObject changed = e.client.awaitNotificationAfter(mark, "model.changed",
                c -> c.get("circuitId").getAsString().equals(mainId));
        JsonObject inst = null;
        for (JsonElement a : changed.getAsJsonArray("added")) {
            if (a.getAsJsonObject().has("appearance")) {
                inst = a.getAsJsonObject();
            }
        }
        assertNotNull(inst, "the instance of the edited subcircuit is sent again");
        assertEquals(before + 1, inst.getAsJsonObject("appearance").getAsJsonArray("ports").size());
        assertPortsLandOnTheEnginePorts(inst);
        assertNotNull(instId);
    }

    /** from 뒤의 sim.values를 겹친 몸체들. */
    JsonObject bodiesAfter(int from) {
        JsonObject out = new JsonObject();
        for (JsonObject v : e.client.notificationsAfter(from, "sim.values")) {
            if (v.has("bodies")) {
                for (var b : v.getAsJsonObject("bodies").entrySet()) {
                    out.add(b.getKey(), b.getValue());
                }
            }
        }
        return out;
    }

    JsonObject watchFirstBodies(String circuitId) throws InterruptedException {
        int mark = e.client.mark();
        e.client.callObject("sim.watch", params("fileId", fileId, "circuitId", circuitId));
        e.client.awaitNotificationAfter(mark, "sim.values", v -> v.has("bodies"));
        Thread.sleep(100);
        return bodiesAfter(mark);
    }

    @Test
    void mipsMemoryBodiesComeWithTheValuesAndFollowTheClock() throws Exception {
        open(new File(Fixtures.CIRC_DIR, "demo-datapath.circ"));
        JsonObject snap = snapshot(main);
        String im = named(snap, "Instruction Memory").get("id").getAsString();
        String dm = named(snap, "Data Memory").get("id").getAsString();
        JsonObject first = watchFirstBodies(main);
        JsonArray imLines = first.getAsJsonObject(im).getAsJsonArray("lines");
        assertEquals("4 words", imLines.get(1).getAsString());
        assertEquals("00000000: 00221820", imLines.get(2).getAsString());
        JsonArray dmLines = first.getAsJsonObject(dm).getAsJsonArray("lines");
        assertEquals("stack 7ffc0000-7fffffff", dmLines.get(1).getAsString());
        assertTrue(dmLines.get(4).getAsString().startsWith("data 0 words, stack peak 0 B"), dmLines.toString());
        // 1 Cycle: PC가 4로, 몸체의 워드 줄이 따라온다. 바뀌지 않은 몸체는 다시 오지 않는다
        int mark = e.client.mark();
        e.client.callObject("sim.cycles", params("fileId", fileId, "n", 1));
        e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("cycle").getAsLong() >= 1
                && !s.get("ticking").getAsBoolean());
        JsonObject next = bodiesAfter(mark);
        assertEquals("00000004: 00221822", next.getAsJsonObject(im).getAsJsonArray("lines").get(2).getAsString());
        assertFalse(next.has(dm), "the Data Memory body did not change");
    }

    @Test
    void ramAndRomBodiesAreTheOriginalFourRowTable() throws Exception {
        open(new File(Fixtures.CIRC_DIR, "memory.circ"));
        JsonObject snap = snapshot(main);
        String ram = named(snap, "RAM").get("id").getAsString();
        JsonObject bodies = watchFirstBodies(main);
        JsonObject grid = bodies.getAsJsonObject(ram);
        assertNotNull(grid, bodies.toString());
        assertEquals(4, grid.getAsJsonArray("rows").size());
        int columns = grid.get("columns").getAsInt();
        assertTrue(columns >= 1);
        assertEquals(columns, grid.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonArray("words").size());
        int roms = 0;
        for (JsonElement c : snap.getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (o.get("name").getAsString().equals("ROM")) {
                assertTrue(bodies.has(o.get("id").getAsString()));
                roms++;
            }
        }
        assertEquals(1, roms);
    }

    @Test
    void radixProbeAndConsoleBodies() throws Exception {
        JsonObject f = e.client.callObject("file.new", params());
        fileId = f.get("fileId").getAsString();
        main = f.get("main").getAsString();
        JsonObject c = new JsonObject();
        c.addProperty("width", "32");
        c.addProperty("value", "0x2a");
        e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", "Wiring",
                "name", "Constant", "loc", new int[] {200, 100}, "attrs", c));
        String probe = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "kr.ac.hallym.hcs.mips.MipsLibrary", "name", "Radix Probe", "loc", new int[] {200, 100}))
                .get("id").getAsString();
        String console = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "kr.ac.hallym.hcs.mips.MipsLibrary", "name", "Console", "loc", new int[] {800, 400}))
                .get("id").getAsString();
        JsonObject bodies = watchFirstBodies(main);
        JsonArray lines = bodies.getAsJsonObject(probe).getAsJsonArray("lines");
        assertEquals("0x0000002a", lines.get(0).getAsString());
        assertEquals("42", lines.get(1).getAsString());
        assertEquals("0000 0000 0000 0000 0000 0000 0010 1010", lines.get(2).getAsString());
        assertEquals(0, bodies.getAsJsonObject(probe).get("primary").getAsInt());
        JsonObject con = bodies.getAsJsonObject(console);
        assertEquals(0, con.getAsJsonArray("lines").size() > 0
                ? con.getAsJsonArray("lines").get(0).getAsString().length() : 0);
        // 조작 도구로 누르면 주 진법이 바뀌고(넷 값은 그대로) 몸체만 다시 온다
        int mark = e.client.mark();
        e.client.callObject("sim.poke", params("fileId", fileId, "circuitId", main, "componentId", probe));
        e.client.awaitNotificationAfter(mark, "sim.values", v -> v.has("bodies")
                && v.getAsJsonObject("bodies").has(probe));
        JsonObject after = bodiesAfter(mark).getAsJsonObject(probe);
        // 원조 PokeTool은 새 캐럿을 만들 때 누름을 두 번 준다(InstancePokerAdapter.getPokeCaret 안의 mousePressed와
        // PokeTool.mousePressed). 그래서 처음 누르면 16진수 → 2진수다. 엔진은 원조 그대로다
        assertEquals(2, after.get("primary").getAsInt());
        assertEquals("0000 0000 0000 0000 0000 0000 0010 1010", after.getAsJsonArray("lines").get(0).getAsString());
    }

    /**
     * 몸체를 읽기만 한다(compat 검토): 시뮬레이션이 꺼진 채 놓은 기억 장치·MIPS 부품은 회로 상태에 데이터가 없고, 몸체를
     * 읽어도 그대로 없다(원조 Mem.getState처럼 만들어 넣지 않는다). 켜서 돌린 뒤에는 있는 데이터를 같은 객체 그대로 둔다.
     */
    @Test
    void readingBodiesNeverCreatesOrChangesSimulationState() throws Exception {
        JsonObject f = e.client.callObject("file.new", params());
        fileId = f.get("fileId").getAsString();
        main = f.get("main").getAsString();
        e.client.callObject("sim.enable", params("fileId", fileId, "on", false));
        String mips = "kr.ac.hallym.hcs.mips.MipsLibrary";
        String[][] parts = {{"Memory", "RAM"}, {"Memory", "ROM"}, {"Memory", "Shift Register"}, {"Memory", "Register"},
            {mips, "Instruction Memory"}, {mips, "Data Memory"}, {mips, "Console"}, {mips, "Radix Probe"}};
        int x = 400;
        for (String[] part : parts) {
            e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", part[0],
                    "name", part[1], "loc", new int[] {x, 400}));
            x += 400;
        }
        final String file = fileId;
        // 꺼진 채: 데이터 없음, 읽은 뒤에도 없음
        java.util.List<String> created = e.onEngine(() -> {
            kr.ac.hallym.hcs.engine.doc.Doc d = e.engine.files().get(file);
            com.cburch.logisim.circuit.Circuit c = d.project().getCurrentCircuit();
            com.cburch.logisim.circuit.CircuitState st = d.project().getCircuitState(c);
            java.util.List<String> out = new java.util.ArrayList<>();
            kr.ac.hallym.hcs.engine.sim.Bodies bodies = new kr.ac.hallym.hcs.engine.sim.Bodies();
            for (com.cburch.logisim.comp.Component x0 : c.getNonWires()) {
                Object before = st.getData(x0);
                bodies.of(x0, c, st);
                Object after = st.getData(x0);
                if (before != after) {
                    out.add(x0.getFactory().getName() + ": " + before + " -> " + after);
                }
                if (after != null && !x0.getFactory().getName().equals("Radix Probe")) {
                    out.add(x0.getFactory().getName() + " has data while the simulation is off: " + after);
                }
            }
            return out;
        });
        assertEquals(java.util.List.of(), created);
        // 켜서 돌린 뒤: 있는 데이터를 읽고 같은 객체 그대로
        e.client.callObject("sim.enable", params("fileId", fileId, "on", true));
        int mark = e.client.mark();
        e.client.callObject("sim.cycles", params("fileId", fileId, "n", 1));
        e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("cycle").getAsLong() >= 1
                && !s.get("ticking").getAsBoolean());
        java.util.List<String> changed = e.onEngine(() -> {
            kr.ac.hallym.hcs.engine.doc.Doc d = e.engine.files().get(file);
            com.cburch.logisim.circuit.Circuit c = d.project().getCurrentCircuit();
            com.cburch.logisim.circuit.CircuitState st = d.project().getCircuitState(c);
            java.util.List<String> out = new java.util.ArrayList<>();
            kr.ac.hallym.hcs.engine.sim.Bodies bodies = new kr.ac.hallym.hcs.engine.sim.Bodies();
            int read = 0;
            for (com.cburch.logisim.comp.Component x0 : c.getNonWires()) {
                Object before = st.getData(x0);
                if (bodies.of(x0, c, st) != null) {
                    read++;
                }
                if (st.getData(x0) != before) {
                    out.add(x0.getFactory().getName());
                }
            }
            if (read < 6) {
                out.add("bodies read after running: " + read);
            }
            return out;
        });
        assertEquals(java.util.List.of(), changed);
    }
}
