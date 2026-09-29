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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.regress.CircNormalizer;

/**
 * 논리설계 및 실험의 예제 셋(A-08, D-168: tests/circ/adder-1bit·ripple-carry-4bit·counter-4bit.circ).
 * <ul>
 * <li>커밋된 파일은 엔진의 원조 편집 코드가 지은 것과 같다(LogicExamples, D-006 정규화).</li>
 * <li>새 부품(Hallym MIPS)이 없는 원조 2.7.1 파일이다: 라이브러리 설명자는 원조의 일곱뿐이고 hcs:ext도 없다.</li>
 * <li>표준 2.7.1 jar(-tty table)로 돌린 진리표가 덧셈 그대로다(가산기 둘).</li>
 * <li>부품 겹침은 ExampleLayoutTest(D-156), 열고 저장한 바이트는 OpenSaveParityTest가 본다.</li>
 * </ul>
 */
class LogicExamplesTest {
    static final File DIR = Fixtures.CIRC_DIR;
    static final File REPO = DIR.getParentFile().getParentFile();
    static final File LOGISIM = new File(REPO, "vendor/logisim-2.7.1/logisim-generic-2.7.1.jar");
    static final boolean UPDATE = Boolean.getBoolean("hcs.update");

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

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    void committedExamplesAreWhatTheEngineBuilds() throws Exception {
        for (String name : LogicExamples.NAMES) {
            File fresh = tmp.resolve(name).toFile();
            LogicExamples.build(e, name, fresh);
            File committed = new File(DIR, name);
            if (UPDATE) {
                Files.copy(fresh.toPath(), committed.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            assertEquals(CircNormalizer.normalize(read(committed)), CircNormalizer.normalize(read(fresh)), name);
        }
    }

    @Test
    void theyAreOriginalFilesWithNoNewPart() throws Exception {
        for (String name : LogicExamples.NAMES) {
            String text = read(new File(DIR, name));
            assertTrue(text.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<project source=\"2.7.1\" version=\"1.0\">"), name);
            List<String> libs = new ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<lib desc=\"([^\"]+)\"").matcher(text);
            while (m.find()) {
                libs.add(m.group(1));
            }
            assertEquals(List.of("#Wiring", "#Gates", "#Plexers", "#Arithmetic", "#Memory", "#I/O", "#Base"), libs, name);
            assertFalse(text.contains("hcs:"), name);
            assertFalse(text.contains("jar#"), name);
        }
    }

    // ---- 돌려 보기: 엔진(원조 CircuitState)으로 입력 핀을 넣고 출력 핀의 넷 값을 본다 --------------------------------

    String fileId;
    String main;
    JsonObject snap;
    int watchMark;

    void open(String name) {
        JsonObject r = e.client.callObject("file.open", params("path", new File(DIR, name).getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        watchMark = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
    }

    String pin(String label) {
        for (JsonElement c : snap.getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (o.get("name").getAsString().equals("Pin") && label.equals(o.getAsJsonObject("attrs").get("label").getAsString())) {
                return o.get("id").getAsString();
            }
        }
        throw new AssertionError("no pin " + label);
    }

    void set(String label, int value) {
        e.client.call("sim.pinValue", params("fileId", fileId, "circuitId", main, "componentId", pin(label), "value", String.valueOf(value)));
    }

    /** 출력 핀 label의 넷이 want(비트 글자)가 될 때까지(보고 받은 값을 차례로 겹친 것). */
    void expect(String label, String want, String what) throws InterruptedException {
        String net = Fixtures.netOf(snap.getAsJsonArray("nets"), pin(label), 0);
        long end = System.currentTimeMillis() + Client.TIMEOUT_MS;
        while (true) {
            String now = null;
            for (JsonObject v : e.client.notificationsAfter(watchMark, "sim.values")) {
                if (v.getAsJsonObject("nets").has(net)) {
                    now = v.getAsJsonObject("nets").get(net).getAsString();
                }
            }
            if (want.equals(now)) {
                return;
            }
            if (System.currentTimeMillis() > end) {
                throw new AssertionError(what + ": " + label + " is " + now + ", not " + want);
            }
            Thread.sleep(5);
        }
    }

    @Test
    void theOneBitAdderAdds() throws Exception {
        open("adder-1bit.circ");
        for (int k = 0; k < 8; k++) {
            int a = k & 1;
            int b = (k >> 1) & 1;
            int c = k >> 2;
            set("A", a);
            set("B", b);
            set("Cin", c);
            int sum = a + b + c;
            String what = a + "+" + b + "+" + c;
            expect("S", String.valueOf(sum & 1), what);
            expect("Cout", String.valueOf(sum >> 1), what);
        }
    }

    @Test
    void theRippleCarryAdderAdds() throws Exception {
        open("ripple-carry-4bit.circ");
        int[][] cases = {{0, 0, 0}, {1, 1, 0}, {5, 3, 0}, {7, 8, 1}, {15, 1, 0}, {15, 15, 1}, {9, 6, 1}, {10, 5, 0}};
        for (int[] t : cases) {
            for (int i = 0; i < 4; i++) {
                set("A" + i, (t[0] >> i) & 1);
                set("B" + i, (t[1] >> i) & 1);
            }
            set("Cin", t[2]);
            int sum = t[0] + t[1] + t[2];
            String what = t[0] + "+" + t[1] + "+" + t[2];
            for (int i = 0; i < 4; i++) {
                expect("S" + i, String.valueOf((sum >> i) & 1), what);
            }
            expect("Cout", String.valueOf(sum >> 4), what);
        }
    }

    @Test
    void theCounterCounts() throws Exception {
        open("counter-4bit.circ");
        expect("Q", "0000", "reset");
        e.client.call("sim.cycles", params("fileId", fileId, "n", 5));
        expect("Q", "0101", "5 cycles");
        e.client.call("sim.cycles", params("fileId", fileId, "n", 12));
        expect("Q", "0001", "17 cycles: 4 bits wrap");
    }
}
