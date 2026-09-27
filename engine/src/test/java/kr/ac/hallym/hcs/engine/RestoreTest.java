/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static kr.ac.hallym.hcs.engine.Client.xy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 다시 시작한 엔진이 파일을 되살리는 길(N-04, D-142, docs/engine-api.md 7절): {@code engine.hello}의 idFloor,
 * {@code file.new}·{@code file.open}의 restore(앞 엔진의 파일·회로 id). 화면이 앞 id로 든 탭이 그대로 맞고, 새 번호는
 * 앞 엔진의 id와 겹치지 않는다.
 */
class RestoreTest {
    @TempDir
    Path tmp;

    InProcess e;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    static long number(String id) {
        return Long.parseLong(id.substring(1));
    }

    static Map<String, String> circuits(JsonObject opened) {
        Map<String, String> m = new LinkedHashMap<>();
        for (JsonElement c : opened.getAsJsonArray("circuits")) {
            m.put(c.getAsJsonObject().get("name").getAsString(), c.getAsJsonObject().get("circuitId").getAsString());
        }
        return m;
    }

    @Test
    void idFloorMakesEveryNewIdGreater() {
        e.client.callObject("engine.hello", params("client", "test", "version", "0", "idFloor", 900_000));
        JsonObject f = e.client.callObject("file.new", params());
        assertTrue(number(f.get("fileId").getAsString()) > 900_000, f.toString());
        String main = f.get("main").getAsString();
        assertTrue(number(main) > 900_000, main);
        JsonObject r = e.client.callObject("edit.addComponent", params("fileId", f.get("fileId").getAsString(),
                "circuitId", main, "lib", "Gates", "name", "AND Gate", "loc", xy(200, 200)));
        assertTrue(number(r.get("id").getAsString()) > 900_000, r.toString());
    }

    @Test
    void aSmallerFloorDoesNotGoBack() {
        JsonObject a = e.client.callObject("file.new", params());
        e.client.callObject("engine.hello", params("client", "test", "version", "0", "idFloor", 0));
        JsonObject b = e.client.callObject("file.new", params());
        assertTrue(number(b.get("fileId").getAsString()) > number(a.get("fileId").getAsString()));
    }

    @Test
    void openRestoresTheFileAndCircuitIdsByName() throws Exception {
        File circ = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "demo-datapath.circ"), tmp);
        JsonObject first = e.client.callObject("file.open", params("path", circ.getPath()));
        Map<String, String> before = circuits(first);
        e.client.callObject("file.close", params("fileId", first.get("fileId").getAsString()));

        // As a restarted engine would: the floor above the old ids, then the old ids back.
        e.client.callObject("engine.hello", params("client", "test", "version", "0", "idFloor", 2_000_000));
        Map<String, String> wanted = new HashMap<>();
        before.forEach((name, id) -> wanted.put(name, "c" + (number(id) + 1_000_000)));
        JsonObject again = e.client.callObject("file.open", params("path", circ.getPath(),
                "restore", params("fileId", "f1500000", "circuits", wanted)));
        assertEquals("f1500000", again.get("fileId").getAsString());
        assertEquals(wanted, new HashMap<>(circuits(again)));
        assertEquals(wanted.get("main"), again.get("main").getAsString());

        // A subcircuit instance names the restored id; model.library and edits use it.
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", "f1500000", "circuitId", wanted.get("main")));
        boolean sawSub = false;
        for (JsonElement c : snap.getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (o.has("subcircuit")) {
                assertTrue(wanted.containsValue(o.get("subcircuit").getAsString()), o.toString());
                sawSub = true;
            }
        }
        assertTrue(sawSub, "demo-datapath has subcircuit instances");
        JsonElement lib = e.client.call("model.library", params("fileId", "f1500000"));
        for (JsonElement t : lib.getAsJsonArray().get(0).getAsJsonObject().getAsJsonArray("tools")) {
            JsonObject o = t.getAsJsonObject();
            assertEquals(wanted.get(o.get("name").getAsString()), o.get("circuitId").getAsString());
        }
        int from = e.client.rawLines().size();
        JsonObject r = e.client.callObject("edit.addComponent", params("fileId", "f1500000", "circuitId",
                wanted.get("regfile"), "lib", "Gates", "name", "OR Gate", "loc", xy(600, 600)));
        assertTrue(r.get("changed").getAsBoolean());
        JsonObject changed = e.client.awaitNotificationAfter(from, "model.changed", p -> true);
        assertEquals("f1500000", changed.get("fileId").getAsString());
        assertEquals(wanted.get("regfile"), changed.get("circuitId").getAsString());

        // Ids made after it do not meet the restored ones.
        JsonObject fresh = e.client.callObject("file.new", params());
        assertTrue(number(fresh.get("fileId").getAsString()) > 2_000_000);
        assertTrue(number(fresh.get("main").getAsString()) > 2_000_000);
    }

    @Test
    void newRestoresItsIdsAndACircuitNotNamedGetsAFreshOne() {
        JsonObject f = e.client.callObject("file.new", params("restore", params("fileId", "f777777",
                "circuits", params("main", "c777778", "gone", "c777779"))));
        assertEquals("f777777", f.get("fileId").getAsString());
        assertEquals("c777778", f.get("main").getAsString());
        JsonObject g = e.client.callObject("file.new", params("restore", params("fileId", "f777780",
                "circuits", params("other", "c777781"))));
        assertEquals("f777780", g.get("fileId").getAsString());
        assertNotEquals("c777781", g.get("main").getAsString());
        assertTrue(number(g.get("main").getAsString()) > 777_778);
    }

    @Test
    void aRestoredIdInUseOrMalformedIsRefused() {
        JsonObject f = e.client.callObject("file.new", params());
        String id = f.get("fileId").getAsString();
        assertEquals(-32602, e.client.fail("file.new", params("restore", params("fileId", id))).code);
        assertEquals(-32602, e.client.fail("file.new", params("restore", params("fileId", "k12"))).code);
        assertEquals(-32602, e.client.fail("file.new", params("restore", params("fileId", "f"))).code);
        assertEquals(-32602, e.client.fail("file.new", params("restore", "f9")).code);
        assertEquals(-32602, e.client.fail("engine.hello", params("client", "t", "version", "0", "idFloor", -1)).code);
        assertEquals(-32602, e.client.fail("engine.hello", params("client", "t", "version", "0", "idFloor", 1.5)).code);
        // A circuit id in the wrong form is not adopted: the circuit gets a fresh one.
        JsonObject g = e.client.callObject("file.new", params("restore", params("fileId", "f888888",
                "circuits", params("main", "x1"))));
        assertTrue(g.get("main").getAsString().startsWith("c"));
    }
}
