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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * find.query(N-12, D-150): v1 Ctrl+F의 색인(D-036)과 묶음·위치 줄(#135, S-09, S-28), v2가 더한 핀·부품 이름 찾기,
 * 인스턴스 경로와 id(화면이 그 인스턴스 안으로 가서 보인다), 편집 뒤 새 색인.
 */
class FindTest {
    InProcess e;
    String fileId;
    Map<String, String> circuits = new java.util.HashMap<>();

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
        JsonObject r = e.client.callObject("file.open",
                params("path", new File(Fixtures.CIRC_DIR, "demo-datapath.circ").getPath(), "readOnly", true));
        fileId = r.get("fileId").getAsString();
        for (JsonElement c : r.getAsJsonArray("circuits")) {
            circuits.put(c.getAsJsonObject().get("name").getAsString(), c.getAsJsonObject().get("circuitId").getAsString());
        }
    }

    @AfterEach
    void stop() {
        e.close();
    }

    JsonArray find(String text) {
        return e.client.callObject("find.query", params("fileId", fileId, "text", text)).getAsJsonArray("groups");
    }

    static List<String> rows(JsonArray groups) {
        List<String> ret = new ArrayList<>();
        for (JsonElement g : groups) {
            JsonObject o = g.getAsJsonObject();
            ret.add(o.get("kind").getAsString() + " " + o.get("text").getAsString() + " · " + o.get("path").getAsString()
                    + " (" + o.getAsJsonArray("places").size() + ")");
        }
        return ret;
    }

    static JsonObject group(JsonArray groups, String kind, String text, String path) {
        for (JsonElement g : groups) {
            JsonObject o = g.getAsJsonObject();
            if (o.get("kind").getAsString().equals(kind) && o.get("text").getAsString().equals(text)
                    && o.get("path").getAsString().equals(path)) {
                return o;
            }
        }
        throw new AssertionError("no " + kind + " " + text + " · " + path + " in " + rows(groups));
    }

    @Test
    void theOrderIsTheSameInEveryOpening(@TempDir Path tmp) throws Exception {
        // the original keeps a circuit's parts in a HashSet: parts on one point came in any order (v1)
        List<String> first = null;
        for (int i = 0; i < 6; i++) {
            Path copy = tmp.resolve("copy" + i + ".circ");
            Files.copy(new File(Fixtures.CIRC_DIR, "demo-datapath.circ").toPath(), copy);
            fileId = e.client.callObject("file.open", params("path", copy.toString(), "readOnly", true)).get("fileId")
                    .getAsString();
            List<String> seen = new ArrayList<>();
            for (JsonElement g : find("clk")) {
                JsonObject o = g.getAsJsonObject();
                for (JsonElement p : o.getAsJsonArray("places")) {
                    seen.add(o.get("kind").getAsString() + " " + o.get("path").getAsString() + " "
                            + p.getAsJsonObject().get("at") + " " + p.getAsJsonObject().get("place").getAsString());
                }
            }
            if (first == null) {
                first = seen;
            }
            assertEquals(first, seen);
        }
    }

    @Test
    void anEmptyQueryFindsNothing() {
        assertEquals(0, find("").size());
        assertEquals(0, find("   ").size());
        assertEquals(0, find("no-such-name-anywhere").size());
    }

    @Test
    void sameNamesInOnePlaceAreOneGroupWithTheirPlacesInOrder() {
        JsonArray g = find("RegWrite");
        List<String> r = rows(g);
        // v1's walk: main's parts top to bottom, into an instance where it stands; then the next part. Parts on one
        // point (a pin and the tunnel on its port) in a fixed order: label, pin, tunnel, subcircuit, part
        assertEquals(List.of("pin RegWrite · main › regfile #1 › RegWrite (1)", "tunnel RegWrite · main › regfile #1 › RegWrite (4)",
                "tunnel RegWrite · main › RegWrite (2)", "pin RegWrite · main › RegWrite (1)"), r);
        JsonObject tunnels = group(g, "tunnel", "RegWrite", "main › RegWrite");
        JsonArray places = tunnels.getAsJsonArray("places");
        assertTrue(places.size() >= 2, "every RegWrite tunnel in main: " + places);
        int lastY = -1;
        int lastX = -1;
        for (JsonElement p : places) {
            JsonObject o = p.getAsJsonObject();
            assertEquals(circuits.get("main"), o.get("circuitId").getAsString());
            assertEquals(circuits.get("main"), o.get("root").getAsString());
            assertEquals(0, o.getAsJsonArray("path").size());
            int x = o.getAsJsonArray("at").get(0).getAsInt();
            int y = o.getAsJsonArray("at").get(1).getAsInt();
            assertTrue(y > lastY || y == lastY && x > lastX, "top to bottom, left to right");
            lastY = y;
            lastX = x;
        }
    }

    @Test
    void placesNameTheAttachedPortReadablyNeverAnInternalName() {
        for (String q : new String[] {"RegWrite", "clk", "WD", "pc", "sel", "RR1"}) {
            for (JsonElement g : find(q)) {
                for (JsonElement p : g.getAsJsonObject().getAsJsonArray("places")) {
                    String place = p.getAsJsonObject().get("place").getAsString();
                    // S-09: readable names (Splitter #10 (combined end), AND Gate #3 (input 2)), not identifiers
                    assertFalse(place.matches(".*\\b(Split|AND|OR|Reg|Mux|Add) #\\d+.*"), place);
                    assertFalse(place.matches(".*\\.(in\\d|out|combined|q|d)\\b.*"), place);
                    assertTrue(place.startsWith("main") || place.startsWith("regfile") || place.startsWith("alu"),
                            place);
                }
            }
        }
        boolean near = false;
        for (JsonElement p : group(find("MemWrite"), "tunnel", "MemWrite", "main › MemWrite").getAsJsonArray("places")) {
            near |= p.getAsJsonObject().get("near").getAsBoolean();
        }
        assertTrue(near, "a tunnel on a port is placed next to that port");
    }

    @Test
    void namesInsideASubcircuitInstanceComeWithTheInstancePath() {
        JsonArray g = find("RR1");
        boolean inside = false;
        for (JsonElement x : g) {
            for (JsonElement p : x.getAsJsonObject().getAsJsonArray("places")) {
                JsonObject o = p.getAsJsonObject();
                if (o.getAsJsonArray("path").size() == 1) {
                    inside = true;
                    assertEquals(circuits.get("main"), o.get("root").getAsString());
                    assertEquals(circuits.get("regfile"), o.get("circuitId").getAsString());
                    // the instance id is one the Canvas can go into (sim.watch path)
                    String inst = o.getAsJsonArray("path").get(0).getAsString();
                    e.client.call("sim.watch", params("fileId", fileId, "circuitId", circuits.get("main"), "path",
                            new Object[] {inst}));
                }
            }
        }
        assertTrue(inside, rows(g).toString());
        // the subcircuit's own name finds its instances
        JsonObject sub = group(find("regfile"), "subcircuit", "regfile", "main › regfile #1");
        assertEquals(1, sub.getAsJsonArray("places").size());
    }

    @Test
    void pinsAreTheirOwnKindAndPartsAreFoundByTheirKindName() {
        JsonArray g = find("halt");
        assertEquals("pin", g.get(0).getAsJsonObject().get("kind").getAsString(), rows(g).toString());
        JsonArray regs = find("Register");
        JsonObject part = group(regs, "part", "Register", "main");
        assertTrue(part.getAsJsonArray("places").size() >= 1);
        String place = part.getAsJsonArray("places").get(0).getAsJsonObject().get("place").getAsString();
        assertTrue(place.startsWith("main › "), place);
        // tunnels and subcircuit instances are found by their names only
        for (JsonElement x : find("Tunnel")) {
            assertFalse(x.getAsJsonObject().get("kind").getAsString().equals("part"), x.toString());
        }
    }

    @Test
    void exactNamesComeFirstAndTheLimitSaysThereIsMore() {
        JsonArray g = find("A");
        assertEquals("A", g.get(0).getAsJsonObject().get("text").getAsString(), rows(g).toString());
        JsonObject few = e.client.callObject("find.query", params("fileId", fileId, "text", "e", "limit", 2));
        assertEquals(2, few.getAsJsonArray("groups").size());
        assertTrue(few.get("more").getAsBoolean());
        assertFalse(e.client.callObject("find.query", params("fileId", fileId, "text", "halt")).get("more")
                .getAsBoolean());
        assertEquals(-32602, e.client.fail("find.query", params("fileId", fileId, "text", "a", "limit", 0)).code);
        assertEquals(1, e.client.fail("find.query", params("fileId", "f999", "text", "a")).code);
    }

    @Test
    void anEditIsFoundAtOnce() {
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        String main = r.get("main").getAsString();
        assertEquals(0, find("zeta").size());
        e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", "Wiring", "name",
                "Pin", "loc", xy(100, 100), "attrs", Map.of("label", "zeta")));
        JsonArray g = find("zeta");
        assertEquals(List.of("pin zeta · main › zeta (1)"), rows(g));
        e.client.callObject("edit.undo", params("fileId", fileId));
        assertEquals(0, find("zeta").size());
    }

    @Test
    void circuitsTheMainCircuitDoesNotReachAreSearchedToo() {
        // tests/parity/inputs/adders.circ: parity3 is used nowhere; its output pin p is found under its own name
        File adders = new File(Fixtures.CIRC_DIR.getParentFile(), "parity/inputs/adders.circ");
        JsonObject r = e.client.callObject("file.open", params("path", adders.getPath(), "readOnly", true));
        fileId = r.get("fileId").getAsString();
        String parity3 = null;
        for (JsonElement c : r.getAsJsonArray("circuits")) {
            if (c.getAsJsonObject().get("name").getAsString().equals("parity3")) {
                parity3 = c.getAsJsonObject().get("circuitId").getAsString();
            }
        }
        JsonObject g = group(find("x"), "pin", "x", "parity3 › x");
        JsonObject place = g.getAsJsonArray("places").get(0).getAsJsonObject();
        assertEquals(parity3, place.get("root").getAsString());
        assertEquals(parity3, place.get("circuitId").getAsString());
        assertEquals(0, place.getAsJsonArray("path").size());
    }

    @Test
    @Tag("timing")
    void refMipsIsQuickEnough() {
        JsonObject r = e.client.callObject("file.open", params("path", Fixtures.REF_MIPS.getPath(), "readOnly", true));
        fileId = r.get("fileId").getAsString();
        find("pc");
        long t0 = System.nanoTime();
        JsonArray g = find("pc");
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(g.size() > 0);
        assertTrue(ms < 1500, "find.query on ref-mips took " + ms + " ms");
    }
}
