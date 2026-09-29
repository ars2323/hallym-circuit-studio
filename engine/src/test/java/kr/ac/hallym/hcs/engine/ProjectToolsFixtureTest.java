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

import org.junit.jupiter.api.Test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 화면의 가짜 엔진이 쓰는 Analyze Circuit·Get Circuit Statistics 고정 답(electron/tests/fixtures/project-tools.json,
 * N-21, D-162)이 지금 엔진의 답과 같은지: e2e가 여는 예제 파일의 회로마다 model.analyze와 model.statistics. 파일 이름과
 * 회로 이름으로 찾는다(엔진 id는 가짜 엔진에 뜻이 없다). 다시 쓰기: {@code ./gradlew :engine:test -Phcs.update=true}.
 */
class ProjectToolsFixtureTest {
    static final File FIXTURE = new File(System.getProperty("hcs.electronFixtures"), "project-tools.json");
    static final String[] FILES = {"demo-datapath.circ", "gates.circ", "subcircuit.circ", "half-adder.circ"};

    @Test
    void theFakeEnginesAnalysesAndStatisticsAreTheRealEnginesAnswers() throws Exception {
        JsonObject got = new JsonObject();
        try (InProcess e = new InProcess()) {
            for (String name : FILES) {
                File f = new File(Fixtures.CIRC_DIR, name);
                JsonObject opened = e.client.callObject("file.open", params("path", f.getPath(), "readOnly", true));
                String fileId = opened.get("fileId").getAsString();
                JsonObject byCircuit = new JsonObject();
                for (JsonElement ce : opened.getAsJsonArray("circuits")) {
                    JsonObject c = ce.getAsJsonObject();
                    String id = c.get("circuitId").getAsString();
                    JsonObject one = new JsonObject();
                    one.add("analyze", e.client.callObject("model.analyze", params("fileId", fileId, "circuitId", id)));
                    one.add("statistics", e.client.callObject("model.statistics", params("fileId", fileId, "circuitId", id)));
                    byCircuit.add(c.get("name").getAsString(), one);
                }
                got.add(name, byCircuit);
                e.client.call("file.close", params("fileId", fileId));
            }
        }
        String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(got) + "\n";
        if (Boolean.getBoolean("hcs.update")) {
            Files.write(FIXTURE.toPath(), text.getBytes(StandardCharsets.UTF_8));
        }
        String want = FIXTURE.isFile() ? new String(Files.readAllBytes(FIXTURE.toPath()), StandardCharsets.UTF_8) : "";
        assertEquals(JsonParser.parseString(want.isEmpty() ? "{}" : want), JsonParser.parseString(text),
                "electron/tests/fixtures/project-tools.json is not the engine's answer: ./gradlew :engine:test"
                        + " -Phcs.update=true writes it again");
    }
}
