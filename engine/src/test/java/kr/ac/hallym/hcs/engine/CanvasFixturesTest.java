/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 캔버스 시험 자료의 값 프레임(D-137): 정해진 자리(전파가 끝난 뒤)의 값 전체를 찍고, 사이클 프레임은 앞 자리와 달라진
 * 것만. 16ms 묶음의 경계가 어디에 떨어지든 같은 글자다(예전에는 한 사이클 안에서 0→1→0인 클럭 넷이 프레임에 들기도
 * 하고 빠지기도 해 CI의 다시 만들기 비교가 가끔 실패했다).
 */
class CanvasFixturesTest {
    @TempDir
    Path tmp;

    static JsonObject frame(String nets, String bodies) {
        JsonObject o = new JsonObject();
        o.add("nets", JsonParser.parseString(nets));
        o.add("bodies", JsonParser.parseString(bodies));
        return o;
    }

    @Test
    void aCycleFrameHasOnlyWhatDiffersFromTheStateBefore() {
        JsonObject before = frame("{\"n1\":\"0\",\"n2\":\"1\",\"n3\":\"0101\"}", "{\"k1\":{\"lines\":[\"a\"]}}");
        JsonObject now = frame("{\"n1\":\"0\",\"n2\":\"0\",\"n3\":\"0101\",\"n4\":\"x\"}",
                "{\"k1\":{\"lines\":[\"a\"]},\"k2\":{\"lines\":[\"b\"]}}");
        JsonObject d = CanvasFixtures.changed(before, now);
        // the clock that went 0 -> 1 -> 0 within the cycle (n1) is not there; a changed net and a new one are
        assertEquals("{\"n2\":\"0\",\"n4\":\"x\"}", d.get("nets").toString());
        assertEquals("{\"k2\":{\"lines\":[\"b\"]}}", d.get("bodies").toString());
        assertEquals("{\"nets\":{},\"bodies\":{}}", CanvasFixtures.changed(now, now).toString());
    }

    @Test
    void theSameCircuitGivesTheSameFramesTwiceAndEachCycleFrameOnlyChanges() throws Exception {
        File demo = new File(Fixtures.CIRC_DIR, "demo-datapath.circ");
        String[] text = new String[2];
        for (int i = 0; i < 2; i++) {
            Path out = tmp.resolve("demo-" + i + ".json");
            try (InProcess e = new InProcess()) {
                CanvasFixtures.circuit(e, demo, out);
            }
            text[i] = Files.readString(out, StandardCharsets.UTF_8);
        }
        assertEquals(text[0], text[1]);
        JsonObject watch = JsonParser.parseString(text[0]).getAsJsonObject().getAsJsonObject("watch");
        int cycleFrames = 0;
        for (Map.Entry<String, JsonElement> w : watch.entrySet()) {
            Map<String, String> state = new HashMap<>();
            int k = 0;
            for (JsonElement f : w.getValue().getAsJsonArray()) {
                JsonObject nets = f.getAsJsonObject().getAsJsonObject("nets");
                if (k == 0) {
                    assertFalse(nets.isEmpty(), w.getKey() + ": the first frame has every net");
                } else {
                    cycleFrames++;
                    for (Map.Entry<String, JsonElement> n : nets.entrySet()) {
                        assertNotEquals(state.get(n.getKey()), n.getValue().getAsString(),
                                w.getKey() + " frame " + k + ": " + n.getKey() + " did not change");
                    }
                }
                nets.entrySet().forEach(n -> state.put(n.getKey(), n.getValue().getAsString()));
                k++;
            }
        }
        assertTrue(cycleFrames >= CanvasFixtures.CYCLES, "cycle frames were written");
    }
}
