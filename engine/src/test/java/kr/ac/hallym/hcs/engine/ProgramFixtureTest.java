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
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import kr.ac.hallym.hcs.mips.image.SourceCheck;

/**
 * 화면의 가짜 엔진이 쓰는 MIPS 고정 답(electron/tests/fixtures/programs.json, N-16, D-147)이 지금 엔진의 답과 같은지:
 * ref-mips에 실행 이미지를 불러온 결과(요약·문제·고르기 없음), 불러온 뒤의 사실(PC ≠ entry), 끝까지 돌린 Console 출력,
 * Instruction Memory의 디스어셈블. 가짜 엔진은 파일 내용의 SHA-256으로 답을 찾는다(다시 내보낸 파일이 바뀌면 다른 답).
 * 엔진의 id(k17, c3)는 가짜 엔진에 뜻이 없으므로 부품은 이름({@code @Instruction Memory}), 회로는 이름({@code @main})으로
 * 적는다. 시각(loadedAt)은 뺀다. 다시 쓰기: {@code ./gradlew :engine:test -Phcs.update=true}.
 */
class ProgramFixtureTest {
    static final File FIXTURE = new File(System.getProperty("hcs.electronFixtures"), "programs.json");
    static final File TESTS = Fixtures.REF_MIPS.getParentFile().getParentFile();
    /** 불러올 파일(tests 기준)과 Console을 보려고 돌릴 사이클 수(0: 돌리지 않음). */
    static final String[][] CASES = {
        {"hmx/hallym-mips-v2.4.0/data.hmx", "80"},
        {"hmx/hallym-mips-v2.4.0/main-later.hmx", "0"},
        {"hmx/hallym-mips-v2.4.0/pseudo.hmx", "0"},
        {"hmx/hallym-mips-v2.4.0/no-handler.hmx", "0"},
        {"hmx/truncated.hmx", "0"},
        {"hmx/source-changed.hmx", "0"},
    };

    @TempDir
    Path tmp;

    @Test
    void theFakeEnginesProgramsAreTheRealEnginesAnswers() throws Exception {
        JsonObject got = new JsonObject();
        got.addProperty("circuit", "tests/mips/ref-mips.circ");
        JsonObject byHash = new JsonObject();
        try (InProcess e = new InProcess()) {
            for (String[] c : CASES) {
                File src = new File(TESTS, c[0]);
                Path dir = Files.createTempDirectory(tmp, "p");
                // .hmx와 그 옆의 .s(원본 대조), 그리고 ref-mips를 한 폴더에
                for (File f : src.getParentFile().listFiles()) {
                    if (f.getName().endsWith(".s")) {
                        Files.copy(f.toPath(), dir.resolve(f.getName()));
                    }
                }
                File hmx = dir.resolve(src.getName()).toFile();
                Files.copy(src.toPath(), hmx.toPath());
                File circ = dir.resolve("ref-mips.circ").toFile();
                Files.copy(Fixtures.REF_MIPS.toPath(), circ.toPath());
                JsonObject opened = e.client.callObject("file.open", params("path", circ.getPath()));
                String fileId = opened.get("fileId").getAsString();
                Map<String, String> names = names(e, fileId, opened.get("main").getAsString());

                JsonObject one = new JsonObject();
                one.addProperty("file", src.getName());
                JsonObject r = e.client.callObject("mips.load", params("fileId", fileId, "path", hmx.getPath()));
                r.remove("fileId");
                r.remove("loadedAt");
                one.add("load", named(r, names));
                if (r.get("loaded").getAsBoolean()) {
                    waitForPc(e, fileId);
                    JsonObject facts = e.client.callObject("mips.facts", params("fileId", fileId));
                    JsonArray pc = new JsonArray();
                    for (JsonElement f : facts.getAsJsonArray("facts")) {
                        pc.add(named(f.getAsJsonObject(), names));
                    }
                    one.add("facts", pc);
                    JsonObject prog = facts.getAsJsonObject("program");
                    prog.remove("loadedAt");
                    one.add("program", named(prog, names));
                    String im = key(names, "Instruction Memory");
                    JsonObject d = e.client.callObject("mips.disasm", params("fileId", fileId, "componentId", im));
                    d.remove("fileId");
                    one.add("disasm", named(d, names));
                    int cycles = Integer.parseInt(c[1]);
                    if (cycles > 0) {
                        int m2 = e.client.mark();
                        e.client.call("sim.cycles", params("fileId", fileId, "n", cycles));
                        e.client.awaitNotificationAfter(m2, "sim.state", s -> s.get("fileId").getAsString()
                                .equals(fileId) && s.get("cycle").getAsLong() == cycles);
                        JsonObject con = e.client.callObject("mips.console", params("fileId", fileId));
                        con.remove("fileId");
                        one.addProperty("cycles", cycles);
                        one.add("console", con);
                    }
                }
                e.client.call("file.close", params("fileId", fileId));
                byHash.add(SourceCheck.sha256(src), one);
            }
        }
        got.add("programs", byHash);
        String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(got) + "\n";
        if (Boolean.getBoolean("hcs.update")) {
            Files.write(FIXTURE.toPath(), text.getBytes(StandardCharsets.UTF_8));
        }
        String want = FIXTURE.isFile() ? new String(Files.readAllBytes(FIXTURE.toPath()), StandardCharsets.UTF_8) : "";
        assertEquals(JsonParser.parseString(want.isEmpty() ? "{}" : want), JsonParser.parseString(text),
                "electron/tests/fixtures/programs.json is not the engine's answer: ./gradlew :engine:test"
                        + " -Phcs.update=true writes it again");
    }

    /** 엔진 id → 이름(회로 {@code @main}, 부품 {@code @Instruction Memory}): ref-mips에는 MIPS 메모리가 하나씩이다. */
    static Map<String, String> names(InProcess e, String fileId, String main) {
        Map<String, String> out = new HashMap<>();
        out.put(main, "@main");
        JsonArray comps = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main))
                .getAsJsonArray("components");
        for (String n : new String[] {"Instruction Memory", "Data Memory", "Console"}) {
            out.put(Fixtures.byName(comps, n).get(0).get("id").getAsString(), "@" + n);
        }
        return out;
    }

    static String key(Map<String, String> names, String name) {
        for (Map.Entry<String, String> x : names.entrySet()) {
            if (x.getValue().equals("@" + name)) {
                return x.getKey();
            }
        }
        throw new AssertionError(name);
    }

    /** 글 값 가운데 엔진 id를 이름으로 바꾼 사본. */
    static JsonObject named(JsonObject o, Map<String, String> names) {
        return JsonParser.parseString(rename(o, names).toString()).getAsJsonObject();
    }

    private static JsonElement rename(JsonElement el, Map<String, String> names) {
        if (el.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> x : el.getAsJsonObject().entrySet()) {
                out.add(x.getKey(), rename(x.getValue(), names));
            }
            return out;
        }
        if (el.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement x : el.getAsJsonArray()) {
                out.add(rename(x, names));
            }
            return out;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() && names.containsKey(el.getAsString())) {
            return new com.google.gson.JsonPrimitive(names.get(el.getAsString()));
        }
        return el;
    }

    /** Reset 뒤 PC가 정해질 때까지(사실 pcEntry는 그 값을 본다). */
    static void waitForPc(InProcess e, String fileId) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            String pc = e.onEngine(() -> {
                com.cburch.logisim.circuit.CircuitState st = e.engine.files().get(fileId).project().getCircuitState();
                while (st.getParentState() != null) {
                    st = st.getParentState();
                }
                return kr.ac.hallym.hcs.app.sim.StatusModel.pc(st);
            });
            if (pc != null) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the PC never settled");
    }
}
