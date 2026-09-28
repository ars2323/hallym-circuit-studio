/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.splitter.SplitterSpec;

/**
 * Components·검색 창·Find·Splitter 편집기의 화면 시험 자료(N-12, D-150). {@code ./gradlew :engine:canvasFixtures}가
 * 캔버스 자료와 함께 만들고 CI가 다시 만들어 비교한다.
 * <ul>
 * <li>{@code library.json}: {@code model.library}의 답(새 파일, demo-datapath, ref-mips). 가짜 엔진이 그대로 답해 부품
 * 목록·검색이 진짜 엔진의 도구 이름과 차례로 시험된다. 이 파일의 회로 줄에는 id 대신 이름만 둔다.</li>
 * <li>{@code find.json}: 몇 가지 {@code find.query}의 답. 자리는 id 대신 회로 이름과 부품의 이름·자리로 적는다(가짜
 * 엔진이 제 id로 바꾼다, messages.json과 같은 방식).</li>
 * <li>{@code splitter-ranges.json}: 편집기 범위 글의 해석(v1 {@link SplitterSpec#parse})과 그 결과(원조 속성, 팔,
 * 글로 다시 쓰기, 배정 안 된 비트). 화면의 해석(logic/splitter.ts)이 엔진과 같은지 단위 테스트가 본다.</li>
 * </ul>
 */
final class FindFixtures {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    /** find.query로 적어 둘 글(가짜 엔진은 이 글에만 진짜 엔진의 답을 준다). */
    static final Map<String, String[]> QUERIES = new LinkedHashMap<>();

    static {
        QUERIES.put("tests/circ/demo-datapath.circ", new String[] {"RegWrite", "clk", "pc", "PC", "Register", "regfile",
            "RR1", "ALU", "halt", "sel", "Splitter", "zzz"});
        QUERIES.put("tests/mips/ref-mips.circ", new String[] {"pc", "Instruction Memory", "Data Memory", "regfile"});
    }

    private FindFixtures() {
    }

    static void write(Path out, Path repo) throws Exception {
        writeText(out.resolve("library.json"), library(repo));
        writeText(out.resolve("find.json"), find(repo));
        writeText(out.resolve("splitter-ranges.json"), ranges());
    }

    // ---- model.library ----

    static String library(Path repo) throws Exception {
        JsonObject all = new JsonObject();
        all.addProperty("source", "./gradlew :engine:canvasFixtures (FindFixtures, D-150): engine API model.library");
        try (InProcess e = new InProcess()) {
            String fileId = e.client.callObject("file.new", params()).get("fileId").getAsString();
            all.add("new", stripIds(e.client.call("model.library", params("fileId", fileId)).getAsJsonArray()));
            for (String f : new String[] {"tests/circ/demo-datapath.circ", "tests/mips/ref-mips.circ"}) {
                String id = e.client.callObject("file.open", params("path", repo.resolve(f).toString(), "readOnly", true))
                        .get("fileId").getAsString();
                all.add(new File(f).getName(),
                        stripIds(e.client.call("model.library", params("fileId", id)).getAsJsonArray()));
            }
        }
        return PRETTY.toJson(all) + "\n";
    }

    /** 이 파일의 회로 줄에서 엔진 id를 뺀다(가짜 엔진이 제 회로 id를 이름으로 붙인다). */
    static JsonArray stripIds(JsonArray groups) {
        for (JsonElement g : groups) {
            for (JsonElement t : g.getAsJsonObject().getAsJsonArray("tools")) {
                t.getAsJsonObject().remove("circuitId");
            }
            JsonObject o = g.getAsJsonObject();
            if (o.get("lib").isJsonNull()) {
                o.remove("display"); // the file's own name: the fake engine says its own
            }
        }
        return groups;
    }

    // ---- find.query ----

    static String find(Path repo) throws Exception {
        JsonObject all = new JsonObject();
        all.addProperty("source", "./gradlew :engine:canvasFixtures (FindFixtures, D-150): engine API find.query; places by "
                + "circuit name and the part's name and loc instead of engine ids");
        for (Map.Entry<String, String[]> q : QUERIES.entrySet()) {
            try (InProcess e = new InProcess()) {
                JsonObject opened = e.client.callObject("file.open",
                        params("path", repo.resolve(q.getKey()).toString(), "readOnly", true));
                String fileId = opened.get("fileId").getAsString();
                Map<String, String> circuitNames = new HashMap<>();
                Map<String, JsonObject> parts = new HashMap<>();
                for (JsonElement c : opened.getAsJsonArray("circuits")) {
                    String id = c.getAsJsonObject().get("circuitId").getAsString();
                    circuitNames.put(id, c.getAsJsonObject().get("name").getAsString());
                    JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", id));
                    for (JsonElement p : snap.getAsJsonArray("components")) {
                        parts.put(p.getAsJsonObject().get("id").getAsString(), p.getAsJsonObject());
                    }
                }
                JsonObject answers = new JsonObject();
                for (String text : q.getValue()) {
                    JsonObject r = e.client.callObject("find.query", params("fileId", fileId, "text", text));
                    for (JsonElement g : r.getAsJsonArray("groups")) {
                        for (JsonElement pe : g.getAsJsonObject().getAsJsonArray("places")) {
                            JsonObject p = pe.getAsJsonObject();
                            p.addProperty("circuitId", circuitNames.get(p.get("circuitId").getAsString()));
                            p.addProperty("root", circuitNames.get(p.get("root").getAsString()));
                            JsonArray path = new JsonArray();
                            for (JsonElement inst : p.getAsJsonArray("path")) {
                                path.add(place(parts.get(inst.getAsString())));
                            }
                            p.add("path", path);
                            p.add("component", place(parts.get(p.get("componentId").getAsString())));
                            p.remove("componentId");
                        }
                    }
                    r.remove("fileId");
                    answers.add(text, r);
                }
                all.add(new File(q.getKey()).getName(), answers);
            }
        }
        return PRETTY.toJson(all) + "\n";
    }

    static JsonObject place(JsonObject part) {
        JsonObject o = new JsonObject();
        o.addProperty("name", part.get("name").getAsString());
        o.add("loc", part.get("loc"));
        return o;
    }

    // ---- splitter ranges ----

    /** 범위 글 · 폭 · 위 팔 방향의 여러 경우와 v1 해석의 결과. */
    static String ranges() {
        Object[][] cases = {
            {"31:26, 25:21, 20:16, 15:0", 32, true}, {"31:26 op, 25:21 rs, 20:16 rt, 15:11 rd, 10:6 shamt, 5:0 funct", 32, true},
            {"31:26 op, 25:0 addr", 32, true}, {"31:26, 25:21, 20:16, 15:0", 32, false}, {"4x8", 32, true},
            {"4x8", 32, false}, {"32x1", 32, true}, {"8X4", 32, true}, {"2×4", 8, true}, {"7:4 hi, 3:0 lo", 8, true},
            {"0, 1, 7:2", 8, false}, {"0, 1, 7:2", 8, true}, {"3:0, 7:4", 8, true}, {"5", 32, true}, {"7,3:0", 8, true},
            {"  7 : 4 , 3 : 0  ", 8, true}, {"4:7, 0:3", 8, true}, {"a.b_c", 8, true}, {"7:4 a.b, 3:0 c_d", 8, true},
            {"9:0", 8, true}, {"7:4, 4:0", 8, true}, {"", 8, true}, {"what", 8, true}, {"7:4 1x", 8, true},
            {"0x8", 8, true}, {"33x1", 32, true}, {"4x8", 0, true}, {"15:8, 7:0", 0, true}, {"7:4,,3:0", 8, true},
            {"7-4", 8, true}, {"31", 32, true}, {"31:0", 32, true}, {"1:0", 2, true}, {"0", 1, true}, {"0, 2", 3, true},
        };
        JsonArray out = new JsonArray();
        for (Object[] c : cases) {
            JsonObject o = new JsonObject();
            o.addProperty("text", (String) c[0]);
            o.addProperty("width", (Integer) c[1]);
            o.addProperty("msbTop", (Boolean) c[2]);
            try {
                SplitterSpec s = SplitterSpec.parse((String) c[0], (Integer) c[1], (Boolean) c[2]);
                JsonObject r = new JsonObject();
                r.addProperty("width", s.width());
                JsonArray arms = new JsonArray();
                for (SplitterSpec.Arm a : s.arms()) {
                    JsonObject arm = new JsonObject();
                    JsonArray bits = new JsonArray();
                    a.bits().forEach(bits::add);
                    arm.add("bits", bits);
                    arm.addProperty("name", a.name());
                    arm.addProperty("range", a.range());
                    arm.addProperty("label", a.label());
                    arms.add(arm);
                }
                r.add("arms", arms);
                JsonObject attrs = new JsonObject();
                s.toStandardAttrs().forEach(attrs::addProperty);
                r.add("attrs", attrs);
                r.addProperty("text", s.toText());
                JsonArray un = new JsonArray();
                s.unassigned().forEach(un::add);
                r.add("unassigned", un);
                o.add("ok", r);
            } catch (SplitterSpec.ParseException e) {
                o.addProperty("error", e.getMessage());
            }
            out.add(o);
        }
        JsonObject presets = new JsonObject();
        for (SplitterSpec.Preset p : SplitterSpec.Preset.values()) {
            presets.addProperty(p.name(), p.spec(true).toText());
        }
        JsonObject all = new JsonObject();
        all.addProperty("source", "./gradlew :engine:canvasFixtures (FindFixtures, D-150): v1 SplitterSpec.parse");
        all.add("presets", presets);
        all.add("cases", out);
        return PRETTY.toJson(all) + "\n";
    }

    private static void writeText(Path target, String text) throws IOException {
        Files.write(target, text.getBytes(StandardCharsets.UTF_8));
    }
}
