/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 화면의 가짜 엔진이 쓰는 Messages 고정 답(electron/tests/fixtures/messages.json, electron/tools/diag-fixture.ts가
 * 이 엔진에서 받아 적는다)이 지금 엔진의 말과 같은지(D-143): 회로마다 처음 목록과 사이클 뒤 목록의 종류·문구·가까운
 * 이름·사이클. 엔진 문구를 바꾸면 이 테스트가 고정 답을 다시 만들라고 알린다(node tools/diag-fixture.ts).
 */
class DiagFixtureTest {
    static final File FIXTURE = new File(System.getProperty("hcs.electronFixtures"), "messages.json");
    static final File REPO = new File(System.getProperty("hcs.circDir")).getParentFile().getParentFile();

    @TempDir
    Path tmp;

    @Test
    void theFakeEnginesMessagesAreTheRealEnginesWords() throws Exception {
        JsonObject fx = JsonParser.parseString(new String(Files.readAllBytes(FIXTURE.toPath()), StandardCharsets.UTF_8))
                .getAsJsonObject();
        List<String> want = new ArrayList<>();
        List<String> got = new ArrayList<>();
        try (InProcess e = new InProcess()) {
            for (Map.Entry<String, JsonElement> entry : fx.entrySet()) {
                JsonObject spec = entry.getValue().getAsJsonObject();
                File src = new File(REPO, spec.get("from").getAsString());
                File copy = Fixtures.copyWithSiblings(src, Files.createTempDirectory(tmp, "x"));
                String fileId = e.client.callObject("file.open", params("path", copy.getPath())).get("fileId")
                        .getAsString();
                describe(entry.getKey() + " static", spec.getAsJsonArray("static"), want);
                describe(entry.getKey() + " static", list(e, fileId), got);
                if (spec.has("cycles")) {
                    int n = spec.get("cycles").getAsInt();
                    int mark = e.client.mark();
                    e.client.call("sim.cycles", params("fileId", fileId, "n", n));
                    e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("fileId").getAsString()
                            .equals(fileId) && (s.get("cycle").getAsLong() >= n || !s.get("running").getAsBoolean()));
                    JsonArray after = spec.getAsJsonArray("afterCycles");
                    JsonArray now = list(e, fileId);
                    for (int i = 0; i < 100 && now.size() != after.size(); i++) {
                        Thread.sleep(20); // 마지막 스텝의 동적 검사(시뮬레이터 스레드)
                        now = list(e, fileId);
                    }
                    describe(entry.getKey() + " after " + n, after, want);
                    describe(entry.getKey() + " after " + n, now, got);
                }
                e.client.call("file.close", params("fileId", fileId));
            }
        }
        assertEquals(String.join("\n", want), String.join("\n", got),
                "electron/tests/fixtures/messages.json is out of date: ./gradlew :engine:stage, then in electron/ "
                        + "node tools/diag-fixture.ts");
    }

    static JsonArray list(InProcess e, String fileId) {
        return e.client.callObject("diag.list", params("fileId", fileId)).getAsJsonArray("messages");
    }

    /** 비교하는 것: 종류, 찾은 곳, 두 문구, 가까운 이름, 사이클, 부품·선 수. */
    static void describe(String where, JsonArray list, List<String> out) {
        for (JsonElement x : list) {
            JsonObject m = x.getAsJsonObject();
            JsonObject loc = m.getAsJsonObject("location");
            out.add(where + ": " + m.get("code").getAsString() + " " + m.get("kind").getAsString() + " near="
                    + (m.has("near") ? m.get("near").getAsString() : "-") + " cycle="
                    + (loc.has("cycle") ? loc.get("cycle").getAsInt() : "-") + " parts="
                    + loc.getAsJsonArray("components").size() + " wires=" + loc.getAsJsonArray("wires").size()
                    + " | " + m.getAsJsonObject("text").get("ko").getAsString() + " | "
                    + m.getAsJsonObject("text").get("en").getAsString());
        }
    }
}
