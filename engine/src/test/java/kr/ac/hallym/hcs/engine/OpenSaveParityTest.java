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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.doc.RecoveryFiles;
import kr.ac.hallym.hcs.regress.CircEquivalence;
import kr.ac.hallym.hcs.regress.CircNormalizer;

/**
 * 열기만 한 파일은 저장해도 그대로다(규칙 2.3, D-006, D-149): tests/ 아래와 화면 고정 파일의 모든 .circ를 한 엔진으로
 * 차례로 열고 화면이 여는 동안 하는 일(회로마다 model.circuit과 그 안 서브회로 인스턴스의 모양(appearance), model.library,
 * diag.list, mips.facts, 찾기(find.query, N-12), sim.watch와 값 스트림의 몸체 상태(bodies), Cycle View의 record.*(D-144: 표·Registers·Memory·
 * Instruction·필드 경로, 지난 사이클 보기와 돌아오기), 몇 사이클, 캔버스가 서브회로 인스턴스 안으로 들어가 보기(sim.watch
 * path, N-05), 캔버스 덧그림이 묻는 영향 경로·Signal Flow·활성 경로·넷 정보(N-15))을 한 뒤 저장하면 원래 글자와 같다(D-006 정규화). 기록기는 파일을 열 때 붙고(file.open) 사이클마다 적는다.
 * Mark as PC·레지스터 파일 표시는 하지 않는다(학생이 고른 표시는 파일을 바꾸는 편집이다). 더 돌리고 한 번 더 저장해도 같다. 새 부품을 쓰는 파일도
 * 열기만으로는 아무것도 늘지 않는다.
 */
class OpenSaveParityTest {
    static final File REPO = Fixtures.CIRC_DIR.getParentFile().getParentFile();
    static final File SMOKE_JAR = new File(System.getProperty("hcs.smokeJar"));
    static final Pattern LIB = Pattern.compile("<lib desc=\"(file|jar)#([^#\"]+)");
    static final int CYCLES = 4;

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

    /** tests/ 아래(빌드 폴더 제외)와 electron/tests/fixtures의 .circ 전부. */
    static List<File> allFiles() throws IOException {
        List<File> ret = new ArrayList<>();
        for (File root : new File[] {new File(REPO, "tests"), new File(REPO, "electron/tests/fixtures")}) {
            try (Stream<Path> s = Files.walk(root.toPath())) {
                s.filter(p -> p.toString().endsWith(".circ")
                        && !p.toString().contains(File.separator + "build" + File.separator))
                        .sorted().forEach(p -> ret.add(p.toFile()));
            }
        }
        return ret;
    }

    static String name(File f) {
        return REPO.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/');
    }

    static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * f를 dir로 복사하고, .circ 라이브러리도 같은 상대 경로로 복사한다. 스모크 JAR는 빌드 산출물에서 가져오고,
     * hcs-mips.jar는 두지 않는다(엔진이 번들로 잇고 설명자는 그대로 둔다, D-007).
     */
    static File copyWithLibraries(File f, Path dir) throws IOException {
        Path target = dir.resolve(f.getName());
        Files.copy(f.toPath(), target);
        Matcher m = LIB.matcher(read(f));
        while (m.find()) {
            String rel = m.group(2);
            File src = new File(f.getParentFile(), rel);
            Path dst = dir.resolve(rel);
            if (Files.exists(dst)) {
                continue;
            }
            if (m.group(1).equals("file") && src.isFile()) {
                Files.createDirectories(dst.getParent());
                copyWithLibraries(src, dst.getParent());
            } else if (m.group(1).equals("jar") && rel.equals(SMOKE_JAR.getName())) {
                Files.copy(SMOKE_JAR.toPath(), dst);
            }
        }
        return target.toFile();
    }

    @Test
    void allFilesAreFound() throws Exception {
        List<File> files = allFiles();
        assertTrue(files.size() >= 60, "found " + files.size());
        assertTrue(files.stream().anyMatch(f -> name(f).equals("tests/circ/demo-datapath.circ")));
        assertTrue(files.stream().anyMatch(f -> name(f).equals("electron/tests/fixtures/broken-datapath.circ")));
    }

    @TestFactory
    Stream<DynamicTest> openingAsTheScreenDoesThenSavingChangesNothing() throws Exception {
        // 한 엔진에서 차례로 연다(화면의 탭처럼): 앞 파일의 도구 기본값이 뒤 파일에 끼어들지 않는다
        return allFiles().stream().map(f -> DynamicTest.dynamicTest(name(f), () -> {
            File copy = copyWithLibraries(f, Files.createTempDirectory(tmp, "o"));
            JsonObject opened = e.client.callObject("file.open", params("path", copy.getPath()));
            assertEquals(new JsonArray(), opened.getAsJsonArray("messages"), "no load errors");
            String fileId = opened.get("fileId").getAsString();
            screenOpens(fileId, opened);
            assertFalse(e.client.callObject("file.dirty", params("fileId", fileId)).get("dirty").getAsBoolean(),
                    "opening does not make the file dirty");
            // what the recovery file would hold now (N-19, D-152): the file as it was
            assertEquals(CircNormalizer.normalize(read(f)), CircNormalizer.normalize(recoveryText(fileId)),
                    "the recovery file's writer writes what a save writes");
            File saved = new File(copy.getParentFile(), "saved-" + f.getName());
            e.client.call("file.save", params("fileId", fileId, "path", saved.getPath()));
            assertSavedLike(f, saved, "the first save");
            if (!read(f).contains("jar#")) {
                assertEquals(Collections.<String>emptyList(), CircEquivalence.compare(f, saved));
            }
            // 더 돌리고 다시 저장(Ctrl+S): 원조는 두 번째 저장에 앞 저장이 불러온 도구(ROM)를 적는다
            screenOpens(fileId, opened);
            e.client.call("file.save", params("fileId", fileId));
            assertSavedLike(f, saved, "the second save");
            e.client.call("file.close", params("fileId", fileId));
        }));
    }

    /**
     * 학생이 직접 정하는 확장 정보를 바꾸는 의도(N-12: edit.tunnelColor, edit.splitterEdit)를 하고 되돌리면 저장이 원래
     * 글자와 같다: 터널·스플리터가 있는 모든 파일에서, 이름 있는 터널 하나의 색을 바꾸고 스플리터 하나의 팔 이름을 바꾼 뒤
     * 둘 다 되돌린다. 다시 실행하고 또 되돌려도 같다.
     */
    @TestFactory
    Stream<DynamicTest> extEditsUndoneSaveTheOriginal() throws Exception {
        List<File> files = new ArrayList<>();
        for (File f : allFiles()) {
            String t = read(f);
            if (!t.contains("jar#") && (t.contains("name=\"Tunnel\"") || t.contains("name=\"Splitter\""))) {
                files.add(f);
            }
        }
        assertTrue(files.size() >= 10, "files with tunnels or splitters: " + files.size());
        return files.stream().map(f -> DynamicTest.dynamicTest(name(f), () -> {
            File copy = copyWithLibraries(f, Files.createTempDirectory(tmp, "x"));
            JsonObject opened = e.client.callObject("file.open", params("path", copy.getPath()));
            String fileId = opened.get("fileId").getAsString();
            int edits = 0;
            java.util.Set<String> coloured = new java.util.HashSet<>();
            for (JsonElement ce : opened.getAsJsonArray("circuits")) {
                String circuitId = ce.getAsJsonObject().get("circuitId").getAsString();
                JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", circuitId));
                for (JsonElement c : snap.getAsJsonArray("components")) {
                    JsonObject o = c.getAsJsonObject();
                    String id = o.get("id").getAsString();
                    if (o.get("name").getAsString().equals("Tunnel") && o.getAsJsonObject("attrs").has("label")
                            && !o.getAsJsonObject("attrs").get("label").getAsString().isBlank() && edits % 2 == 0
                            && coloured.add(circuitId + " " + o.getAsJsonObject("attrs").get("label").getAsString())) {
                        String now = o.has("ext") ? o.getAsJsonObject("ext").get("color").getAsString() : "";
                        String pick = now.equalsIgnoreCase("#E69F00") ? "#56B4E9" : "#E69F00";
                        JsonObject r = e.client.callObject("edit.tunnelColor",
                                params("fileId", fileId, "circuitId", circuitId, "id", id, "color", pick));
                        assertTrue(r.get("changed").getAsBoolean(), r.toString());
                        edits++;
                    } else if (o.get("name").getAsString().equals("Splitter") && edits % 2 == 1) {
                        String ranges = ranges(o.getAsJsonObject("attrs"));
                        if (ranges == null) {
                            continue;
                        }
                        JsonObject r = e.client.callObject("edit.splitterEdit", params("fileId", fileId, "circuitId",
                                circuitId, "id", id, "ranges", ranges.substring(1), "lsbTop", ranges.startsWith("L"),
                                "names", new Object[] {"renamed"}));
                        assertTrue(r.get("changed").getAsBoolean(), ranges + " " + r);
                        edits++;
                    }
                }
            }
            for (int k = 0; k < edits; k++) {
                e.client.call("edit.undo", params("fileId", fileId));
            }
            if (edits > 0) {
                e.client.call("edit.redo", params("fileId", fileId));
                e.client.call("edit.undo", params("fileId", fileId));
            }
            File saved = new File(copy.getParentFile(), "saved-" + f.getName());
            e.client.call("file.save", params("fileId", fileId, "path", saved.getPath()));
            assertSavedLike(f, saved, "after " + edits + " extension edits undone");
            e.client.call("file.close", params("fileId", fileId));
        }));
    }

    /**
     * 스플리터 속성의 범위 글(편집기에 치는 꼴): 위 팔부터 {@code 7:4, 3:0}. 앞 글자 M은 위 팔이 높은 비트(MSB on top),
     * L은 낮은 비트. 비트가 없는 팔이 있으면 편집기 글로 쓸 수 없어 null.
     */
    static String ranges(JsonObject attrs) {
        int fanout = Integer.parseInt(attrs.get("fanout").getAsString());
        int width = Integer.parseInt(attrs.get("incoming").getAsString());
        List<List<Integer>> arms = new ArrayList<>();
        for (int i = 0; i < fanout; i++) {
            arms.add(new ArrayList<>());
        }
        for (int b = width - 1; b >= 0; b--) {
            String v = attrs.has("bit" + b) ? attrs.get("bit" + b).getAsString() : "none";
            if (!v.equals("none")) {
                arms.get(Integer.parseInt(v)).add(b);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (List<Integer> bits : arms) {
            if (bits.isEmpty()) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            for (int i = 0; i < bits.size(); i++) {
                int start = bits.get(i);
                int end = start;
                while (i + 1 < bits.size() && bits.get(i + 1) == end - 1) {
                    end = bits.get(++i);
                }
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(',');
                }
                sb.append(start == end ? Integer.toString(start) : start + ":" + end);
            }
        }
        boolean msbTop = fanout < 2 || arms.get(0).get(0) > arms.get(1).get(0);
        for (int i = 0; i + 1 < fanout; i++) {
            if ((arms.get(i).get(0) > arms.get(i + 1).get(0)) != msbTop) {
                return null; // neither order: the editor would sort the arms
            }
        }
        return (msbTop ? "M" : "L") + sb;
    }

    /** 두 파일을 함께 열어 두어도 저마다 제 도구 기본값({@code <lib>} 아래 {@code <tool>})으로 저장한다. */
    @Test
    void toolDefaultsStayWithTheirFile() throws Exception {
        File withTools = new File(REPO, "tests/parity/01-place-parts.circ");
        File plain = new File(Fixtures.CIRC_DIR, "gates.circ");
        assertTrue(read(withTools).contains("<tool name=\"Splitter\">"));
        assertFalse(read(plain).contains("<tool name=\"Splitter\">"));
        File a = copyWithLibraries(withTools, Files.createTempDirectory(tmp, "a"));
        File b = copyWithLibraries(plain, Files.createTempDirectory(tmp, "b"));
        String idA = e.client.callObject("file.open", params("path", a.getPath())).get("fileId").getAsString();
        String idB = e.client.callObject("file.open", params("path", b.getPath())).get("fileId").getAsString();
        File savedB = new File(b.getParentFile(), "saved.circ");
        File savedA = new File(a.getParentFile(), "saved.circ");
        e.client.call("file.save", params("fileId", idB, "path", savedB.getPath()));
        e.client.call("file.save", params("fileId", idA, "path", savedA.getPath()));
        assertSavedLike(plain, savedB, "the plain file");
        assertSavedLike(withTools, savedA, "the file with tool defaults");
    }

    /** D-006 비교: 원래 파일과 저장한 파일의 정규화 글자가 같다. */
    static void assertSavedLike(File original, File saved, String what) throws IOException {
        String want = CircNormalizer.normalize(read(original));
        String got = CircNormalizer.normalize(read(saved));
        assertEquals(want, got, () -> what + ": " + difference(want, got));
    }

    /** 실패 글: 한쪽에만 있는 줄(여러 번 나오는 줄은 개수로 센다). */
    static String difference(String want, String got) {
        List<String> missing = new ArrayList<>(List.of(want.split("\n")));
        List<String> extra = new ArrayList<>();
        for (String line : got.split("\n")) {
            if (!missing.remove(line)) {
                extra.add(line);
            }
        }
        return "saved file differs from the original; missing " + missing + ", added " + extra;
    }

    /**
     * 캔버스 덧그림이 묻는 것(N-15, D-151): 활성 경로, 부품마다(앞 몇 개) 영향 경로·Signal Flow(Active Path Only,
     * Through Registers 포함), 첫 선의 넷 정보와 선에서 시작한 흐름. 모두 읽기만 한다.
     */
    void overlays(String fileId, String main, JsonObject snap) {
        e.client.callObject("flow.activePath", params("fileId", fileId, "circuitId", main));
        if (snap == null) {
            return;
        }
        int n = 0;
        for (JsonElement ce : snap.getAsJsonArray("components")) {
            if (n++ >= 6) {
                break;
            }
            String id = ce.getAsJsonObject().get("id").getAsString();
            e.client.callObject("trace.influence", params("fileId", fileId, "circuitId", main, "from",
                    java.util.List.of(id), "mode", "both", "throughRegisters", true));
            e.client.callObject("flow.path", params("fileId", fileId, "circuitId", main, "componentId", id,
                    "activePathOnly", true, "throughRegisters", true));
        }
        JsonArray wires = snap.getAsJsonArray("wires");
        if (wires.size() > 0) {
            String w = wires.get(0).getAsJsonObject().get("id").getAsString();
            e.client.callObject("trace.net", params("fileId", fileId, "circuitId", main, "wire", w));
            e.client.callObject("flow.path", params("fileId", fileId, "circuitId", main, "wire", w, "backward", true));
        }
    }

    /** 복구 파일의 쓰개(RecoveryFiles.bytes, main이 편집 뒤에 부르는 file.recoverWrite의 몸체)가 쓸 글자. */
    String recoveryText(String fileId) throws Exception {
        return new String(e.onEngine(() -> RecoveryFiles.bytes(e.engine.files().get(fileId))), StandardCharsets.UTF_8);
    }

    /**
     * 화면(electron app.ts)이 파일을 열 때 부르는 것 전부와 몇 사이클. 복구 파일 쓰개도 한 번 돈다(편집 뒤에 main이
     * 부른다: 원조 writer가 도구를 불러오는 버릇이 다음 저장에 남지 않아야 한다, D-149·D-152).
     */
    void screenOpens(String fileId, JsonObject opened) throws Exception {
        recoveryText(fileId);
        JsonObject mainSnapshot = null;
        java.util.Map<String, JsonObject> snapshots = new java.util.HashMap<>();
        for (JsonElement ce : opened.getAsJsonArray("circuits")) {
            String id = ce.getAsJsonObject().get("circuitId").getAsString();
            JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", id));
            snapshots.put(id, snap);
            // 캔버스(N-05)가 그리는 서브회로 인스턴스의 모양: 모든 인스턴스에 있다
            for (JsonElement c : snap.getAsJsonArray("components")) {
                JsonObject o = c.getAsJsonObject();
                if (o.has("subcircuit")) {
                    assertTrue(o.has("appearance") && o.getAsJsonObject("appearance").has("shapes"), o.toString());
                }
            }
            if (!opened.get("main").isJsonNull() && id.equals(opened.get("main").getAsString())) {
                mainSnapshot = snap;
            }
        }
        e.client.call("model.library", params("fileId", fileId));
        // N-17(D-158): 도구 모음의 도구(Ctrl+2…9)와 그 도구를 든 모습(읽기만)
        for (JsonElement t : e.client.call("model.toolbar", params("fileId", fileId)).getAsJsonArray()) {
            JsonObject o = t.getAsJsonObject();
            if (!o.has("tool")) {
                e.client.callObject("model.tool", params("fileId", fileId, "lib", o.get("lib"), "name", o.get("name"),
                        "attrs", o.has("attrs") ? o.get("attrs") : new JsonObject()));
            }
        }
        e.client.callObject("diag.list", params("fileId", fileId));
        e.client.callObject("mips.facts", params("fileId", fileId));
        // Find와 검색 창(N-12): 이름 색인을 만들고 붙은 포트로 자리 글을 짓는다(읽기만)
        e.client.callObject("find.query", params("fileId", fileId, "text", "a"));
        e.client.callObject("find.query", params("fileId", fileId, "text", "Register"));
        // 회로·모양·라이브러리 창(N-11, D-153): Circuits 패널, Port Order 창, 모양 편집 화면, 인스턴스 안내 띠,
        // Load/Unload Library 창, 저장 전 영향. 모두 읽기만 한다(모양 편집 화면은 원조 AppearanceView를 만든다)
        e.client.callObject("model.libraries", params("fileId", fileId));
        e.client.callObject("file.saveImpact", params("fileId", fileId));
        for (JsonElement ce : opened.getAsJsonArray("circuits")) {
            String id = ce.getAsJsonObject().get("circuitId").getAsString();
            e.client.callObject("model.ports", params("fileId", fileId, "circuitId", id));
            e.client.callObject("model.instances", params("fileId", fileId, "circuitId", id));
            JsonObject app = e.client.callObject("model.appearance", params("fileId", fileId, "circuitId", id));
            int shapes = app.getAsJsonArray("shapes").size();
            e.client.callObject("model.appearanceMenu", params("fileId", fileId, "circuitId", id, "shapes",
                    shapes > 0 ? new Object[] {0} : new Object[0]));
            e.client.callObject("model.appearanceHit", params("fileId", fileId, "circuitId", id, "at",
                    new Object[] {50, 50}, "rect", new Object[] {0, 0, 200, 200}));
        }
        // N-21(D-162): Undo History의 목록, Project › Analyze Circuit·Get Circuit Statistics(원조 계산 클래스),
        // Create Submission의 점검(경로 없이: zip을 쓰지 않는다). 모두 읽기만 한다
        e.client.callObject("model.history", params("fileId", fileId));
        e.client.callObject("file.submission", params("fileId", fileId));
        for (JsonElement ce : opened.getAsJsonArray("circuits")) {
            String id = ce.getAsJsonObject().get("circuitId").getAsString();
            e.client.callObject("model.analyze", params("fileId", fileId, "circuitId", id));
            e.client.callObject("model.statistics", params("fileId", fileId, "circuitId", id));
        }
        if (opened.get("main").isJsonNull()) {
            return;
        }
        String main = opened.get("main").getAsString();
        int watched = e.client.mark();
        e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        // 값 스트림의 첫 묶음(몸체 상태 bodies를 읽는 길까지)이 온 뒤 돌린다. 빈 회로는 보낼 값이 없다
        if (mainSnapshot != null && mainSnapshot.getAsJsonArray("nets").size() > 0) {
            e.client.awaitNotificationAfter(watched, "sim.values", v -> v.get("fileId").getAsString().equals(fileId));
        }
        cycleViewAsks(fileId, main);
        long want = e.client.callObject("sim.state", params("fileId", fileId)).get("cycle").getAsLong() + CYCLES;
        int mark = e.client.mark();
        try {
            e.client.call("sim.cycles", params("fileId", fileId, "n", CYCLES));
            e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("fileId").getAsString().equals(fileId)
                    && (s.get("cycle").getAsLong() >= want || !s.get("running").getAsBoolean()));
        } catch (Client.Failure stopped) {
            // 진동으로 멈춘 회로는 Reset 전까지 더 돌지 않는다(오류 4, D-134 9항): 화면도 그대로 둔다
            assertEquals(4, stopped.code, stopped.getMessage());
        }
        // N-07(D-145): Tick Once 두 번(한 사이클), 주 회로의 첫 입력 핀을 Poke로 두 번(제자리), 캐럿 닫기
        try {
            e.client.call("sim.tick", params("fileId", fileId));
            e.client.call("sim.tick", params("fileId", fileId));
        } catch (Client.Failure stopped) {
            assertEquals(4, stopped.code, stopped.getMessage()); // 진동으로 꺼져 있다
        }
        if (mainSnapshot != null) {
            for (JsonElement c : mainSnapshot.getAsJsonArray("components")) {
                JsonObject o = c.getAsJsonObject();
                if (o.get("name").getAsString().equals("Pin")
                        && "false".equals(o.getAsJsonObject("attrs").get("output").getAsString())) {
                    for (int k = 0; k < 2; k++) {
                        e.client.call("sim.poke", params("fileId", fileId, "circuitId", main, "componentId",
                                o.get("id").getAsString()));
                    }
                    e.client.call("sim.pokeStop", params("fileId", fileId));
                    break;
                }
            }
        }
        e.client.callObject("diag.list", params("fileId", fileId));
        e.client.callObject("mips.facts", params("fileId", fileId));
        overlays(fileId, main, mainSnapshot);
        // N-08(D-146): 편집 도구가 모델을 바꾸지 않고 부르는 것 — Edit 도구의 누름(고르기)과 끄는 동안의 선, 복사,
        // 사각형 고르기와 비우기, 부품 놓기 도구의 모습(값을 준 것도), 글자 도구의 칸
        if (mainSnapshot != null) {
            for (JsonElement c : mainSnapshot.getAsJsonArray("components")) {
                JsonObject o = c.getAsJsonObject();
                JsonArray b = o.getAsJsonArray("bounds");
                int[] mid = {b.get(0).getAsInt() + b.get(2).getAsInt() / 2, b.get(1).getAsInt() + b.get(3).getAsInt() / 2};
                e.client.callObject("edit.select", params("fileId", fileId, "circuitId", main, "at", mid));
                e.client.callObject("model.movePreview", params("fileId", fileId, "circuitId", main, "dx", 10,
                        "dy", 0));
                e.client.callObject("edit.copy", params("fileId", fileId, "circuitId", main));
                e.client.callObject("model.textAt", params("fileId", fileId, "circuitId", main, "loc", mid));
                break;
            }
            e.client.callObject("edit.select", params("fileId", fileId, "circuitId", main, "rect",
                    new int[] {0, 0, 4000, 4000}));
            e.client.callObject("edit.select", params("fileId", fileId, "circuitId", main, "at", new int[] {-50, -50}));
            e.client.callObject("edit.select", params("fileId", fileId, "circuitId", main, "ids", new JsonArray()));
            try {
                e.client.callObject("model.tool", params("fileId", fileId, "lib", "Wiring", "name", "Pin"));
                e.client.callObject("model.tool", params("fileId", fileId, "lib", "Gates", "name", "AND Gate",
                        "attrs", params("inputs", "3")));
            } catch (Client.Failure noLibrary) {
                assertEquals(1, noLibrary.code, noLibrary.getMessage()); // 그 라이브러리가 없는 파일(JAR 라이브러리만)
            }
            e.client.callObject("model.textAt", params("fileId", fileId, "circuitId", main, "loc", new int[] {5, 5}));
        }
        // N-10(D-157): 속성 표(고른 것·회로·도구), 우클릭 메뉴의 사실(부품·선·빈 곳), RAM·ROM 내용 읽기 — 모델을 바꾸지 않는다
        if (mainSnapshot != null) {
            e.client.callObject("model.attributes", params("fileId", fileId, "circuitId", main));
            e.client.callObject("model.attributes", params("fileId", fileId, "circuitId", main, "circuit", true));
            e.client.callObject("model.menu", params("fileId", fileId, "circuitId", main, "at", new int[] {-40, -40}));
            for (JsonElement c : mainSnapshot.getAsJsonArray("components")) {
                JsonObject o = c.getAsJsonObject();
                String id = o.get("id").getAsString();
                JsonArray b = o.getAsJsonArray("bounds");
                int[] mid = {b.get(0).getAsInt() + b.get(2).getAsInt() / 2, b.get(1).getAsInt() + b.get(3).getAsInt() / 2};
                JsonArray one = new JsonArray();
                one.add(id);
                e.client.callObject("model.attributes", params("fileId", fileId, "circuitId", main, "ids", one));
                e.client.callObject("model.menu", params("fileId", fileId, "circuitId", main, "at", mid, "id", id));
                String n = o.get("name").getAsString();
                if (n.equals("RAM") || n.equals("ROM")) {
                    try {
                        e.client.callObject("mem.read", params("fileId", fileId, "circuitId", main, "componentId", id,
                                "count", 64));
                    } catch (Client.Failure notYet) {
                        assertEquals(4, notYet.code, notYet.getMessage()); // 아직 전파가 닿지 않은 RAM
                    }
                }
            }
            for (JsonElement w : mainSnapshot.getAsJsonArray("wires")) {
                JsonObject o = w.getAsJsonObject();
                JsonArray a = o.getAsJsonArray("a");
                e.client.callObject("model.menu", params("fileId", fileId, "circuitId", main, "at",
                        new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()}, "id", o.get("id").getAsString()));
                break;
            }
            try {
                e.client.callObject("model.attributes", params("fileId", fileId, "lib", "Gates", "name", "AND Gate"));
            } catch (Client.Failure noLibrary) {
                assertEquals(1, noLibrary.code, noLibrary.getMessage());
            }
        }
        // 캔버스가 서브회로 인스턴스 안을 보는 길(sim.watch path)과 돌아오기
        if (mainSnapshot != null) {
            for (JsonElement c : mainSnapshot.getAsJsonArray("components")) {
                JsonObject o = c.getAsJsonObject();
                if (o.has("subcircuit") && o.get("lib").isJsonNull()) {
                    JsonArray path = new JsonArray();
                    path.add(o.get("id").getAsString());
                    int m = e.client.mark();
                    e.client.call("sim.watch", params("fileId", fileId, "circuitId", main, "path", path));
                    JsonObject inner = snapshots.get(o.get("subcircuit").getAsString());
                    if (inner != null && inner.getAsJsonArray("nets").size() > 0) { // 넷이 없으면 보낼 값도 없다
                        e.client.awaitNotificationAfter(m, "sim.values", v -> v.has("path"));
                    }
                    break;
                }
            }
            e.client.call("sim.watch", params("fileId", fileId, "circuitId", main));
        }
        cycleViewAsks(fileId, main);
        // 지난 사이클을 보고(체크포인트에서 다시 만든 상태를 바꿔 끼움) 마지막으로 돌아온다
        JsonObject st = e.client.callObject("record.state", params("fileId", fileId));
        if (!st.get("empty").getAsBoolean() && st.get("last").getAsInt() > st.get("first").getAsInt()) {
            try {
                e.client.callObject("record.view", params("fileId", fileId, "cycle", st.get("first").getAsInt()));
                cycleViewAsks(fileId, main);
                e.client.callObject("record.view", params("fileId", fileId, "latest", true));
            } catch (Client.Failure stopped) {
                assertEquals(4, stopped.code, stopped.getMessage()); // 진동으로 꺼진 시뮬레이션
            }
        }
    }

    /** Cycle View(electron cycleview.ts)가 보일 때 묻는 것: 상태, 표, Registers, Memory, Instruction, 필드 경로. */
    void cycleViewAsks(String fileId, String main) {
        e.client.callObject("record.state", params("fileId", fileId));
        e.client.callObject("record.table", params("fileId", fileId));
        e.client.callObject("record.registers", params("fileId", fileId));
        e.client.callObject("record.memory", params("fileId", fileId));
        e.client.callObject("record.instruction", params("fileId", fileId));
        e.client.callObject("record.fieldPaths", params("fileId", fileId, "circuitId", main));
    }
}
