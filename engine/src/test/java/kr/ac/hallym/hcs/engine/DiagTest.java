/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.edit.Intents;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * diag.list·diag.changed·trace.origin(D-143, docs/engine-api.md "diag·trace"): 정상 회로는 편집 없이 열어도,
 * 사이클을 돌려도 메시지 0건; 고장 회로 모음(tests/circ/faults)은 회로마다 기대한 메시지 한 줄(v2 문구 골든
 * messages.v2.*.expected); 가까운 이름; 안정된 id와 바뀐 목록만 알리기; 동적 진단과 Reset; 진동 고리; E/X 출처.
 */
class DiagTest {
    static final File FAULTS = new File(Fixtures.CIRC_DIR, "faults");

    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    JsonObject open(File f) {
        JsonObject r = e.client.callObject("file.open", params("path", f.getPath()));
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        return r;
    }

    JsonArray list() {
        JsonObject r = e.client.callObject("diag.list", params("fileId", fileId));
        assertEquals(fileId, r.get("fileId").getAsString());
        return r.getAsJsonArray("messages");
    }

    /** n 사이클을 돌리고, 끝나거나(사이클 n) 발진으로 멈출 때까지 기다린다. */
    void cycles(int n) {
        int mark = e.client.mark();
        e.client.call("sim.cycles", params("fileId", fileId, "n", n));
        e.client.awaitNotificationAfter(mark, "sim.state", s -> s.get("fileId").getAsString().equals(fileId)
                && (s.get("cycle").getAsLong() >= n || !s.get("running").getAsBoolean()));
    }

    /** 목록이 want개가 될 때까지 diag.list를 다시 묻는다(동적 진단은 시뮬레이터 스레드에서 찾는다). */
    JsonArray listUntil(int want) throws InterruptedException {
        return listUntil(l -> l.size() == want);
    }

    /** 목록이 조건을 채울 때까지 diag.list를 다시 묻는다(시간이 넘으면 마지막 목록). */
    JsonArray listUntil(java.util.function.Predicate<JsonArray> ok) throws InterruptedException {
        long end = System.currentTimeMillis() + Client.TIMEOUT_MS;
        JsonArray l = list();
        while (!ok.test(l) && System.currentTimeMillis() < end) {
            Thread.sleep(20);
            l = list();
        }
        return l;
    }

    /**
     * 정상 회로(v1 StaticCheckTest·DynamicCheckTest와 같은 모음에 라이브러리 회로 둘을 더함): tests/circ 바로 아래(엔진
     * 회귀 회로와 데모), libs의 adder_check·ripple_carry, 참조 CPU 둘. 빼는 것: faults/(고장 회로 모음), values.circ(일부러
     * 합선·떠 있는 출력을 만드는 엔진 회귀 회로), flow/(Signal Flow 고정 회로: 값을 주지 않은 입력 핀, 일부러 둔 고리와
     * 클럭 없는 Register), libs/1bit_adder.circ(다른 회로가 서브회로로 쓰는 라이브러리라 혼자 열면 입력 핀이 떠 있다).
     */
    static List<File> normalCircuits() throws Exception {
        List<File> files = new ArrayList<>();
        for (File f : Fixtures.circFiles()) {
            String n = Fixtures.name(f);
            if (!n.contains("/") && !n.equals("values.circ") || n.equals("libs/adder_check.circ")
                    || n.equals("libs/ripple_carry.circ")) {
                files.add(f);
            }
        }
        files.add(Fixtures.REF_MIPS);
        files.add(new File(Fixtures.REF_MIPS.getParentFile(), "ref-mips-v1-stack.circ"));
        return files;
    }

    static String text(JsonObject m, String lang) {
        return m.getAsJsonObject("text").get(lang).getAsString();
    }

    /** 메시지 가운데 부품 componentId를 가리키는 것이 있다. */
    static boolean mentions(JsonArray messages, String componentId) {
        for (JsonElement m : messages) {
            for (JsonElement c : m.getAsJsonObject().getAsJsonObject("location").getAsJsonArray("components")) {
                if (c.getAsString().equals(componentId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 편집한 뒤 아직 다시 보지 않은 진단이 사라진 부품을 가리키면 목록에 넣지 않는다(D-143). 동적 진단은 기록이 편집 뒤
     * 새로 시작해야(시뮬레이터 스레드) 다시 보므로, 그 사이에 적는 목록(프레임의 알림, diag.list)에 옛 진단이 남는다.
     * 그대로 적으면 사라진 부품에 새 id를 주어 화면이 모르는 id를 받고 id 표가 는다(EditTest.idsAreForgotten…이 CI에서
     * 가끔 실패).
     */
    @Test
    void aMessageAboutARemovedComponentIsLeftOutUntilTheChecksRunAgain() throws Exception {
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
        // 입력이 떠 있는 AND: 동적 진단 E_APPEARED가 이 게이트를 가리킨다
        String and = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib",
                "Gates", "name", "AND Gate", "loc", new int[] {300, 300})).get("id").getAsString();
        assertTrue(mentions(listUntil(l -> mentions(l, and)), and), "the floating AND gate has a message");
        // 되돌리기(전파를 요청하지 않는다)와 목록을 엔진 스레드의 한 일로: 기록이 아직 새로 시작하지 않은 때
        JsonArray stale = e.onEngine(() -> {
            Doc d = e.engine.files().get(fileId);
            Intents.undo(d);
            return e.engine.diags().session(d).list().getAsJsonArray("messages");
        });
        assertFalse(mentions(stale, and), "a message about the removed gate: " + stale);
    }

    // ---- 정상 회로 0건 ----

    @Test
    void normalCircuitsHaveNoMessagesBeforeAndAfterCycles() throws Exception {
        List<File> files = normalCircuits();
        assertTrue(files.size() >= 11, files.toString());
        List<String> found = new ArrayList<>();
        for (File f : files) {
            File copy = Fixtures.copyWithSiblings(f, Files.createTempDirectory(tmp, "n"));
            open(copy);
            for (JsonElement m : list()) {
                found.add(Fixtures.name(f) + " (static): " + text(m.getAsJsonObject(), "en"));
            }
            cycles(6);
            Thread.sleep(150); // 마지막 스텝의 동적 검사(시뮬레이터 스레드)가 끝날 틈
            for (JsonElement m : list()) {
                found.add(f.getName() + " (after 6 cycles): " + text(m.getAsJsonObject(), "en"));
            }
            e.client.call("file.close", params("fileId", fileId));
        }
        assertEquals(new ArrayList<String>(), found);
        for (JsonObject c : e.client.notifications("diag.changed")) {
            assertEquals(0, c.getAsJsonArray("messages").size(), "no diag.changed with messages: " + c);
        }
    }

    // ---- 고장 회로 모음: 회로마다 한 줄, v2 문구 골든 ----

    @Test
    void everyFaultCircuitGivesOneMessageInBothLanguages() throws Exception {
        File[] circs = FAULTS.listFiles((d, n) -> n.endsWith(".circ"));
        assertNotNull(circs);
        java.util.Arrays.sort(circs);
        assertTrue(circs.length >= 22, "the v1 set and the near-name cases");
        StringBuilder ko = new StringBuilder();
        StringBuilder en = new StringBuilder();
        List<String> problems = new ArrayList<>();
        java.util.regex.Pattern internal = java.util.regex.Pattern.compile("[^\\s.(]\\.[A-Za-z][A-Za-z0-9]*");
        for (File f : circs) {
            String name = f.getName().replace(".circ", "");
            open(Fixtures.copyWithSiblings(f, Files.createTempDirectory(tmp, "f")));
            JsonArray l = list();
            if (!name.startsWith("static-")) {
                cycles(4); // v1 고장 회로 모음과 같은 8스텝
                // 동적 진단·진동은 시뮬레이터 스레드에서 찾으므로 sim.state보다 늦을 수 있다: 동적 메시지 한 줄을 기다린다
                // (발진 회로는 그 전까지 정적 조합 루프 한 줄이다)
                l = listUntil(x -> x.size() == 1 && x.get(0).getAsJsonObject().get("kind").getAsString()
                        .equals("dynamic"));
            }
            if (l.size() != 1) {
                problems.add(name + ": " + l);
            }
            for (JsonElement x : l) {
                JsonObject m = x.getAsJsonObject();
                ko.append(name).append(": ").append(text(m, "ko")).append('\n');
                en.append(name).append(": ").append(text(m, "en")).append('\n');
                String code = m.get("code").getAsString();
                String prefix = code.equals("OSCILLATION") || code.equals("MIPS_STATUS")
                        || code.startsWith("E_") || code.startsWith("X_") ? "dynamic-|mips-" : "static-";
                if (!name.matches("(" + prefix + ").*")) {
                    problems.add(name + ": kind " + code);
                }
                assertEquals(name.startsWith("static-") ? "static" : "dynamic", m.get("kind").getAsString(), name);
                assertEquals("error", m.get("severity").getAsString());
                for (String t : new String[] {text(m, "ko"), text(m, "en")}) {
                    if (internal.matcher(t).find()) {
                        problems.add(name + ": internal port name in " + t);
                    }
                    if (t.contains("충돌") || t.contains("conflicting")) {
                        if (!name.equals("dynamic-e-conflict")) {
                            problems.add(name + ": conflict wording in " + t);
                        }
                    }
                    if (!t.equals(t.trim()) || t.contains("  ")) {
                        problems.add(name + ": spacing in '" + t + "'");
                    }
                }
            }
            e.client.call("file.close", params("fileId", fileId));
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        golden("messages.v2.ko.expected", ko.toString());
        golden("messages.v2.en.expected", en.toString());
    }

    /**
     * 파일을 열 때부터 있는 MIPS 부품 문제(정렬되지 않은 주소, 영역 밖 주소)는 돌리기 전에 사이클 0으로 나온다. 파일을 연
     * 첫 전파가 기록기만 붙고 진단은 아직 붙지 않은 사이에 끝나면, 진단이 스텝 0을 못 보고 첫 틱에 지금 상태를 읽어
     * "사이클 1"로 말했다(everyFaultCircuitGivesOneMessageInBothLanguages가 가끔 실패). 엔진은 모든 청취자가 붙은 뒤
     * 스텝 0을 다시 적는다(D-143).
     */
    @Test
    void aMipsProblemThereFromTheStartIsCycle0BeforeTheClockRuns() throws Exception {
        for (String name : new String[] {"mips-imem-unaligned", "mips-no-region"}) {
            open(Fixtures.copyWithSiblings(new File(FAULTS, name + ".circ"), Files.createTempDirectory(tmp, "m")));
            JsonArray l = listUntil(1);
            assertEquals(1, l.size(), name + ": " + l);
            JsonObject m = l.get(0).getAsJsonObject();
            assertEquals("MIPS_STATUS", m.get("code").getAsString(), name);
            assertEquals(0, m.getAsJsonObject("location").get("cycle").getAsInt(), name);
            assertTrue(text(m, "en").startsWith("Cycle 0: "), text(m, "en"));
            e.client.call("file.close", params("fileId", fileId));
        }
    }

    /** 기대 파일과 비교한다. 갱신: ./gradlew :engine:test -Phcs.update=true. */
    static void golden(String name, String got) throws Exception {
        File expected = new File(FAULTS, name);
        if (Boolean.getBoolean("hcs.update") || !expected.exists()) {
            Files.write(expected.toPath(), got.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(new String(Files.readAllBytes(expected.toPath()), StandardCharsets.UTF_8), got,
                name + " (update with -Phcs.update=true)");
    }

    // ---- 가까운 이름 ----

    @Test
    void nearNamesOnlyWhenThereIsExactlyOneOfTheSameWidth() throws Exception {
        String[][] cases = {
            {"static-tunnel-near-hit", "RegWrite"},
            {"static-tunnel-unpaired", "RegDst"},
            {"static-tunnel-near-width", null},     // RegWrite는 32비트, RegWirte는 1비트
            {"static-tunnel-near-ambiguous", null}, // ALUSrcA, ALUSrcB 둘
        };
        for (String[] c : cases) {
            open(new File(FAULTS, c[0] + ".circ"));
            JsonArray l = list();
            assertEquals(1, l.size(), c[0]);
            JsonObject m = l.get(0).getAsJsonObject();
            assertEquals("TUNNEL_UNPAIRED", m.get("code").getAsString());
            if (c[1] == null) {
                assertFalse(m.has("near"), c[0]);
                assertFalse(text(m, "ko").contains("혹시"), c[0]);
                assertFalse(text(m, "en").contains("Did you mean"), c[0]);
            } else {
                assertEquals(c[1], m.get("near").getAsString(), c[0]);
                assertTrue(text(m, "ko").endsWith("혹시 " + c[1] + "?"), text(m, "ko"));
                assertTrue(text(m, "en").endsWith("Did you mean " + c[1] + "?"), text(m, "en"));
            }
            e.client.call("file.close", params("fileId", fileId));
        }
    }

    // ---- 자리(location): 규약의 id가 스냅숏에 있다 ----

    @Test
    void locationPointsAtIdsOfTheSnapshot() throws Exception {
        open(new File(FAULTS, "static-short.circ"));
        JsonObject m = list().get(0).getAsJsonObject();
        JsonObject loc = m.getAsJsonObject("location");
        assertEquals(main, loc.get("circuitId").getAsString());
        assertEquals(main, loc.get("root").getAsString());
        assertEquals(0, loc.getAsJsonArray("path").size());
        assertFalse(loc.has("cycle"), "static: no cycle");
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        Set<String> comps = ids(snap.getAsJsonArray("components"));
        Set<String> wires = ids(snap.getAsJsonArray("wires"));
        Set<String> nets = ids(snap.getAsJsonArray("nets"));
        assertEquals(2, loc.getAsJsonArray("components").size(), "the two drivers");
        for (JsonElement c : loc.getAsJsonArray("components")) {
            assertTrue(comps.contains(c.getAsString()), c.toString());
        }
        for (JsonElement w : loc.getAsJsonArray("wires")) {
            assertTrue(wires.contains(w.getAsString()), w.toString());
        }
        assertFalse(loc.getAsJsonArray("nets").isEmpty());
        for (JsonElement n : loc.getAsJsonArray("nets")) {
            assertTrue(nets.contains(n.getAsString()), n.toString());
        }
        assertEquals(2, loc.getAsJsonArray("at").size());
    }

    static Set<String> ids(JsonArray items) {
        Set<String> ret = new HashSet<>();
        for (JsonElement x : items) {
            ret.add(x.getAsJsonObject().get("id").getAsString());
        }
        return ret;
    }

    // ---- 안정된 id, 바뀐 목록만 알린다 ----

    @Test
    void idsStayAndOnlyChangedListsAreSent() throws Exception {
        open(new File(FAULTS, "static-tunnel-near-hit.circ"));
        JsonArray first = list();
        assertEquals(1, first.size());
        String id = first.get(0).getAsJsonObject().get("id").getAsString();
        assertEquals(id, list().get(0).getAsJsonObject().get("id").getAsString(), "asked again: the same id");
        // 상관없는 편집(떨어진 곳에 상수 하나): 같은 메시지, 같은 id. 넷 번호는 편집마다 다시 매기므로(model.changed와
        // 같다) 자리의 넷 id가 바뀌었으면 diag.changed가 올 수 있지만, 그때도 id와 문구는 그대로다
        int mark = e.client.mark();
        e.client.call("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", "Wiring",
                "name", "Constant", "loc", new int[] {900, 900}));
        Thread.sleep(400);
        for (JsonObject c : e.client.notificationsAfter(mark, "diag.changed")) {
            assertEquals(1, c.getAsJsonArray("messages").size());
            JsonObject m = c.getAsJsonArray("messages").get(0).getAsJsonObject();
            assertEquals(id, m.get("id").getAsString());
            assertEquals(text(first.get(0).getAsJsonObject(), "ko"), text(m, "ko"));
        }
        assertTrue(e.client.notificationsAfter(mark, "diag.changed").size() <= 1);
        assertEquals(id, list().get(0).getAsJsonObject().get("id").getAsString());
        mark = e.client.mark();
        Thread.sleep(300);
        assertEquals(List.of(), e.client.notificationsAfter(mark, "diag.changed"), "nothing new: nothing sent");
        // 받는 터널을 RegWrite로 고치면 0건: diag.changed 한 번
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        String tunnel = null;
        for (JsonElement c : snap.getAsJsonArray("components")) {
            JsonObject o = c.getAsJsonObject();
            if (o.get("name").getAsString().equals("Tunnel")
                    && o.getAsJsonObject("attrs").get("label").getAsString().equals("RegWirte")) {
                tunnel = o.get("id").getAsString();
            }
        }
        assertNotNull(tunnel);
        mark = e.client.mark();
        e.client.call("edit.setAttr", params("fileId", fileId, "circuitId", main, "ids", new String[] {tunnel},
                "attr", "label", "value", "RegWrite"));
        JsonObject changed = e.client.awaitNotificationAfter(mark, "diag.changed",
                c -> c.get("fileId").getAsString().equals(fileId));
        assertEquals(0, changed.getAsJsonArray("messages").size());
        Thread.sleep(300);
        assertEquals(1, e.client.notificationsAfter(mark, "diag.changed").size(), "once");
        // 되돌리면 다시 한 줄
        mark = e.client.mark();
        e.client.call("edit.undo", params("fileId", fileId));
        changed = e.client.awaitNotificationAfter(mark, "diag.changed", c -> c.get("fileId").getAsString()
                .equals(fileId));
        assertEquals(1, changed.getAsJsonArray("messages").size());
    }

    // ---- 동적 진단: 사이클과 Reset ----

    @Test
    void dynamicMessagesComeWithTheirCycleAndGoAtReset() throws Exception {
        open(new File(FAULTS, "dynamic-x-write-data.circ"));
        assertEquals(0, list().size(), "nothing before the clock runs");
        int mark = e.client.mark();
        cycles(4);
        JsonObject changed = e.client.awaitNotificationAfter(mark, "diag.changed",
                c -> c.get("fileId").getAsString().equals(fileId) && c.getAsJsonArray("messages").size() == 1);
        JsonObject m = changed.getAsJsonArray("messages").get(0).getAsJsonObject();
        assertEquals("X_WRITE_DATA", m.get("code").getAsString());
        assertEquals("dynamic", m.get("kind").getAsString());
        assertEquals(0, m.getAsJsonObject("location").get("cycle").getAsInt());
        assertTrue(m.has("appeared"), "where the X was about to be written");
        assertTrue(text(m, "ko").startsWith("사이클 0에 main › PC 부품에 쓸 때 D 입력이"), text(m, "ko"));
        mark = e.client.mark();
        e.client.call("sim.reset", params("fileId", fileId));
        changed = e.client.awaitNotificationAfter(mark, "diag.changed",
                c -> c.get("fileId").getAsString().equals(fileId));
        assertEquals(0, changed.getAsJsonArray("messages").size(), "Reset: the recording starts again");
    }

    // ---- 진동 ----

    @Test
    void oscillationReplacesTheStaticLoopWithItsLoop() throws Exception {
        open(new File(FAULTS, "dynamic-oscillation.circ"));
        JsonArray before = list();
        assertEquals(1, before.size());
        assertEquals("COMBINATIONAL_LOOP", before.get(0).getAsJsonObject().get("code").getAsString());
        int mark = e.client.mark();
        cycles(3);
        JsonObject changed = e.client.awaitNotificationAfter(mark, "diag.changed", c -> c.get("fileId")
                .getAsString().equals(fileId) && c.getAsJsonArray("messages").size() == 1 && c.getAsJsonArray(
                        "messages").get(0).getAsJsonObject().get("code").getAsString().equals("OSCILLATION"));
        JsonObject m = changed.getAsJsonArray("messages").get(0).getAsJsonObject();
        JsonObject loc = m.getAsJsonObject("location");
        assertFalse(loc.getAsJsonArray("components").isEmpty(), "the loop's parts");
        assertFalse(loc.getAsJsonArray("nets").isEmpty(), "the loop's nets");
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        Set<String> nands = new HashSet<>();
        for (JsonObject c : Fixtures.byName(snap.getAsJsonArray("components"), "NAND Gate")) {
            nands.add(c.get("id").getAsString());
        }
        boolean hasNand = false;
        for (JsonElement c : loc.getAsJsonArray("components")) {
            hasNand |= nands.contains(c.getAsString());
        }
        assertTrue(hasNand, loc.toString());
        assertTrue(text(m, "ko").contains("(발진)"), text(m, "ko"));
        // Reset: 발진이 걷히고 정적 조합 루프로 돌아간다
        mark = e.client.mark();
        e.client.call("sim.reset", params("fileId", fileId));
        changed = e.client.awaitNotificationAfter(mark, "diag.changed", c -> c.get("fileId").getAsString()
                .equals(fileId) && c.getAsJsonArray("messages").size() == 1 && c.getAsJsonArray("messages").get(0)
                        .getAsJsonObject().get("code").getAsString().equals("COMBINATIONAL_LOOP"));
        assertNotNull(changed);
    }

    // ---- E/X 출처 ----

    @Test
    void traceOriginFollowsAnXBackToTheInputPin() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component not = b.add("Gates", "NOT Gate", 300, 200);
        b.input("a", 1, 100, 100); // 3상태 기본값: X
        b.tunnel(not, 1, "a");
        Component out = b.add("Wiring", "Pin", 400, 200, "facing", "west", "output", "true", "label", "y");
        b.wire(not.getEnd(0).getLocation(), out.getEnd(0).getLocation());
        b.constant("one", 1, 1, 100, 300);
        Component buf = b.add("Gates", "Buffer", 300, 300);
        b.tunnel(buf, 1, "one");
        b.output("z", 1, 500, 300);
        b.tunnel(buf, 0, "z");
        b.commit();
        File file = tmp.resolve("xpin.circ").toFile();
        CircuitBuilder.save(f, file);
        open(file);
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        String notId = Fixtures.byName(snap.getAsJsonArray("components"), "NOT Gate").get(0).get("id").getAsString();
        String bufId = Fixtures.byName(snap.getAsJsonArray("components"), "Buffer").get(0).get("id").getAsString();
        String pinA = null;
        for (JsonObject p : Fixtures.byName(snap.getAsJsonArray("components"), "Pin")) {
            if (p.getAsJsonObject("attrs").get("label").getAsString().equals("a")) {
                pinA = p.get("id").getAsString();
            }
        }
        String xNet = Fixtures.netOf(snap.getAsJsonArray("nets"), notId, 0);
        String okNet = Fixtures.netOf(snap.getAsJsonArray("nets"), bufId, 0);
        JsonObject r = null;
        long end = System.currentTimeMillis() + Client.TIMEOUT_MS;
        do { // 연 뒤 첫 전파가 끝날 때까지
            r = e.client.callObject("trace.origin", params("fileId", fileId, "circuitId", main, "netId", xNet));
        } while (!r.get("found").getAsBoolean() && System.currentTimeMillis() < end);
        assertTrue(r.get("found").getAsBoolean(), r.toString());
        JsonObject o = r.getAsJsonObject("origin");
        assertEquals("INPUT_PIN", o.get("cause").getAsString());
        assertEquals("x", o.get("value").getAsString());
        assertEquals("main › a 입력 핀의 값이 정해지지 않았습니다.", o.getAsJsonObject("text").get("ko").getAsString());
        assertEquals("input pin main › a has no defined value.", o.getAsJsonObject("text").get("en").getAsString());
        assertEquals(List.of(pinA), strings(o.getAsJsonArray("components")));
        assertEquals(main, o.get("circuitId").getAsString());
        JsonArray chain = r.getAsJsonArray("chain");
        assertTrue(chain.size() >= 2, chain.toString());
        assertEquals(xNet, chain.get(0).getAsJsonObject().get("netId").getAsString(), "starts at the asked net");
        Set<String> nets = ids(snap.getAsJsonArray("nets"));
        for (JsonElement s : chain) {
            assertTrue(nets.contains(s.getAsJsonObject().get("netId").getAsString()), s.toString());
        }
        // 정해진 선: 따라갈 것이 없다
        JsonObject none = e.client.callObject("trace.origin", params("fileId", fileId, "circuitId", main,
                "netId", okNet));
        assertFalse(none.get("found").getAsBoolean());
        assertEquals("이 선의 값은 정해져 있어 따라갈 E·X 값이 없습니다.", none.getAsJsonObject("text").get("ko").getAsString());
        assertNull(none.get("origin"));
        // 없는 넷·경로
        assertEquals(1, e.client.fail("trace.origin", params("fileId", fileId, "circuitId", main, "netId",
                "n9999")).code);
        assertEquals(1, e.client.fail("trace.origin", params("fileId", fileId, "circuitId", main, "netId", xNet,
                "path", new String[] {notId})).code);
    }

    static List<String> strings(JsonArray a) {
        List<String> ret = new ArrayList<>();
        for (JsonElement x : a) {
            ret.add(x.getAsString());
        }
        return ret;
    }

    /** v1 S-30: 게이트의 빈 입력은 프로젝트 옵션 gateUndefined = error일 때만 알린다(원조가 그때만 E를 낸다). */
    @Test
    void emptyGateInputsOnlyWhenTheProjectSaysError() throws Exception {
        for (boolean error : new boolean[] {false, true}) {
            LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
            CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
            Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "5");
            b.constant("a", 1, 1, 100, 100);
            b.constant("b", 1, 1, 100, 150);
            b.output("y", 1, 500, 100);
            b.tunnel(and, 1, "a");
            b.tunnel(and, 2, "b");
            b.tunnel(and, 0, "y");
            b.commit();
            if (error) {
                f.getOptions().getAttributeSet().setValue(com.cburch.logisim.file.Options.ATTR_GATE_UNDEFINED,
                        com.cburch.logisim.file.Options.GATE_UNDEFINED_ERROR);
            }
            File file = tmp.resolve("gate-" + error + ".circ").toFile();
            CircuitBuilder.save(f, file);
            open(file);
            JsonArray l = list();
            if (!error) {
                assertEquals(0, l.size(), l.toString());
            } else {
                assertEquals(1, l.size(), l.toString());
                JsonObject m = l.get(0).getAsJsonObject();
                assertEquals("INPUT_UNCONNECTED", m.get("code").getAsString());
                assertEquals("main › AND #1 부품의 in2, in3, in4 포트가 연결되지 않았습니다.", text(m, "ko"));
            }
            e.client.call("file.close", params("fileId", fileId));
        }
    }

    /**
     * 편집 뒤 정적 검사는 엔진 스레드에서 돈다(D-143): ref-mips(부품 700여 개)에서 한 번이 화면 프레임 몇 개 안.
     * 잰 값은 stderr에 남긴다.
     */
    @Test
    @org.junit.jupiter.api.Tag("timing")
    void staticCheckOfRefMipsTakesAFewFrames() throws Exception {
        open(Fixtures.copyWithSiblings(Fixtures.REF_MIPS, Files.createTempDirectory(tmp, "r")));
        list();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long t = e.onEngine(() -> {
                kr.ac.hallym.hcs.engine.doc.Doc d = e.engine.files().get(fileId);
                long t0 = System.nanoTime();
                e.engine.diags().session(d).set().refreshStatic();
                return System.nanoTime() - t0;
            });
            best = Math.min(best, t);
        }
        System.err.println("[DiagTest] static check of ref-mips: " + best / 1_000_000 + " ms (best of 5)");
        assertTrue(best < 250_000_000L, "static check " + best / 1_000_000 + " ms");
    }

    @Test
    void unknownFileIsError1() {
        assertEquals(1, e.client.fail("diag.list", params("fileId", "f999999")).code);
    }
}
