/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * model.toolbar(N-17, D-158, I-112): 파일의 {@code <toolbar>} 차례(구분선 없이), 놓는 도구는 lib·name과 라이브러리
 * 도구와 다른 속성만, 기본 도구는 tool 이름. 그 속성으로 든 도구(model.tool)와 놓은 부품(edit.addComponent)이 원조
 * 도구 모음의 도구와 같은 부품이다(출력 핀은 출력 핀). 읽기만 한다(dirty 아님, 저장 글자 그대로).
 */
class ToolbarTest {
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

    static String names(JsonArray bar) {
        StringBuilder b = new StringBuilder();
        for (JsonElement x : bar) {
            JsonObject o = x.getAsJsonObject();
            b.append(b.length() == 0 ? "" : ", ").append(o.has("tool") ? o.get("tool").getAsString()
                    : (o.get("lib").isJsonNull() ? "" : o.get("lib").getAsString() + "/") + o.get("name").getAsString());
        }
        return b.toString();
    }

    @Test
    void theDefaultTemplatesToolbarInOrderWithoutSeparators() {
        String fileId = e.client.callObject("file.new", params()).get("fileId").getAsString();
        JsonArray bar = e.client.call("model.toolbar", params("fileId", fileId)).getAsJsonArray();
        assertEquals("Poke Tool, Edit Tool, Text Tool, Wiring/Pin, Wiring/Pin, Gates/NOT Gate, Gates/AND Gate, Gates/OR Gate",
                names(bar));
        // the second Pin is the output pin: its attributes differ from the Wiring library's Pin tool
        JsonObject out = bar.get(4).getAsJsonObject();
        assertEquals("true", out.getAsJsonObject("attrs").get("output").getAsString());
        assertEquals("west", out.getAsJsonObject("attrs").get("facing").getAsString());
        assertFalse(bar.get(6).getAsJsonObject().has("attrs"), "AND Gate as the library has it: nothing to add");
    }

    @Test
    void aToolFromTheToolbarPlacesTheToolbarsPart() throws Exception {
        File f = tmp.resolve("demo-datapath.circ").toFile();
        Files.copy(new File(Fixtures.CIRC_DIR, "demo-datapath.circ").toPath(), f.toPath());
        String before = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        JsonObject opened = e.client.callObject("file.open", params("path", f.getPath()));
        String fileId = opened.get("fileId").getAsString();
        String main = opened.get("main").getAsString();
        JsonArray bar = e.client.call("model.toolbar", params("fileId", fileId)).getAsJsonArray();
        assertEquals(8, bar.size(), names(bar));
        assertFalse(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean());
        // Ctrl+6 in v1 (the fifth: the output pin): held and placed with its attributes
        JsonObject pin = bar.get(4).getAsJsonObject();
        JsonObject ghost = e.client.callObject("model.tool", params("fileId", fileId, "lib", pin.get("lib").getAsString(),
                "name", pin.get("name").getAsString(), "attrs", pin.getAsJsonObject("attrs"))).getAsJsonObject("component");
        assertEquals("true", ghost.getAsJsonObject("attrs").get("output").getAsString());
        // asking for the toolbar and holding its tools changes nothing: saved as it was (D-149's normal form)
        File saved = tmp.resolve("saved.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", saved.getPath()));
        assertEquals(kr.ac.hallym.hcs.regress.CircNormalizer.normalize(before),
                kr.ac.hallym.hcs.regress.CircNormalizer.normalize(Files.readString(saved.toPath(), StandardCharsets.UTF_8)),
                "asking for the toolbar changes nothing");
        JsonObject added = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main,
                "lib", pin.get("lib").getAsString(), "name", "Pin", "loc", new int[] {900, 900}, "attrs", pin.getAsJsonObject("attrs")));
        assertTrue(added.get("changed").getAsBoolean());
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        boolean found = false;
        for (JsonElement c : snap.getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            JsonArray loc = o.getAsJsonArray("loc");
            if (loc.get(0).getAsInt() == 900 && loc.get(1).getAsInt() == 900) {
                assertEquals("Pin", o.get("name").getAsString());
                assertEquals("true", o.getAsJsonObject("attrs").get("output").getAsString());
                found = true;
            }
        }
        assertTrue(found, "the output pin at (900,900)");
    }
}
