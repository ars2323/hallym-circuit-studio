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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.sim.StatusModel;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.mips.LibMips;
import kr.ac.hallym.hcs.engine.mips.MemoryTable;
import kr.ac.hallym.hcs.engine.mips.MipsParts;
import kr.ac.hallym.hcs.mips.image.AssemblySource;
import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.HmxParser;

/**
 * MIPS 프로그램(N-16, D-147, docs/engine-api.md "mips"): 명세 v2.4.0 골든 일곱을 엔진으로 ref-mips에 불러와 부품에서
 * 되읽기, 요약과 사실, 고르기, 실패하면 올라가 있던 것을 그대로 두기(불러오기와 자동 다시 불러오기), 파일을 연 때·Reset 때의
 * 다시 불러오기, .s 경로의 옛 파일, 디스어셈블과 기호, Console 출력 흐름.
 */
class ProgramsTest {
    static final File TESTS = Fixtures.REF_MIPS.getParentFile().getParentFile();
    static final File GOLDEN = new File(TESTS, "hmx/hallym-mips-v2.4.0");
    static final String[] GOLDENS = {"branches", "data", "main-later", "pseudo", "no-data", "space-gap", "no-handler"};
    static final String MIPS = "kr.ac.hallym.hcs.mips.MipsLibrary";

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

    /** ref-mips.circ와 골든(.hmx, .s)을 tmp에: tmp/ref-mips.circ, tmp/prog/*. */
    File workspace() throws Exception {
        Path prog = Files.createDirectories(tmp.resolve("prog"));
        for (File f : GOLDEN.listFiles()) {
            if (f.getName().endsWith(".hmx") || f.getName().endsWith(".s")) {
                Files.copy(f.toPath(), prog.resolve(f.getName()));
            }
        }
        Path circ = tmp.resolve("ref-mips.circ");
        Files.copy(Fixtures.REF_MIPS.toPath(), circ);
        return circ.toFile();
    }

    File prog(String name) {
        return tmp.resolve("prog").resolve(name + ".hmx").toFile();
    }

    static ExecutableImage image(File f) throws Exception {
        ExecutableImage img = HmxParser.read(f).image;
        assertNotNull(img, f.getName());
        return img;
    }

    String[] open(File f) {
        JsonObject o = e.client.callObject("file.open", params("path", f.getPath()));
        return new String[] {o.get("fileId").getAsString(), o.get("main").getAsString()};
    }

    String component(String fileId, String circuitId, String name, int index) {
        JsonArray comps = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", circuitId))
                .getAsJsonArray("components");
        return Fixtures.byName(comps, name).get(index).get("id").getAsString();
    }

    JsonObject load(String fileId, File hmx, Object... more) {
        Object[] kv = new Object[4 + more.length];
        kv[0] = "fileId";
        kv[1] = fileId;
        kv[2] = "path";
        kv[3] = hmx.getPath();
        System.arraycopy(more, 0, kv, 4, more.length);
        return e.client.callObject("mips.load", params(kv));
    }

    /** 메모리 부품의 초기 내용(엔진 스레드에서 lib-mips로 읽는다). */
    SortedMap<Long, Integer> contents(String componentId) throws Exception {
        return e.onEngine(() -> {
            for (Doc d : e.engine.files().all()) {
                Component c = d.ids().component(componentId);
                if (c != null) {
                    return new TreeMap<>(LibMips.contents(c));
                }
            }
            return null;
        });
    }

    /** mips.disasm이 내는 워드(주소 → 워드). */
    Map<Long, Integer> disasmWords(String fileId, String componentId) {
        JsonObject r = e.client.callObject("mips.disasm", params("fileId", fileId, "componentId", componentId,
                "count", 4096));
        Map<Long, Integer> out = new TreeMap<>();
        for (JsonElement l : r.getAsJsonArray("lines")) {
            JsonObject o = l.getAsJsonObject();
            out.put(Long.parseLong(o.get("addr").getAsString().substring(2), 16),
                    (int) Long.parseLong(o.get("word").getAsString().substring(2), 16));
        }
        return out;
    }

    JsonObject facts(String fileId) {
        return e.client.callObject("mips.facts", params("fileId", fileId));
    }

    static JsonObject fact(JsonObject facts, String id) {
        for (JsonElement f : facts.getAsJsonArray("facts")) {
            if (f.getAsJsonObject().get("id").getAsString().equals(id)) {
                return f.getAsJsonObject();
            }
        }
        return null;
    }

    /** 회로의 PC(상태 표시줄의 규칙, D-103)가 정해질 때까지 기다린다(Reset 뒤 전파). */
    String settledPc(String fileId) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            String pc = e.onEngine(() -> {
                Doc d = e.engine.files().get(fileId);
                CircuitState st = d.project().getCircuitState();
                while (st.getParentState() != null) {
                    st = st.getParentState();
                }
                return StatusModel.pc(st);
            });
            if (pc != null) {
                return pc;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("the PC never settled");
    }

    /** 돌고 있는 Data Memory(부품 상태)가 이미지의 .data 워드를 낼 때까지 기다린다(Reset 뒤 전파). */
    MemoryTable.Part runningDataMemory(String fileId, Map<Long, Integer> want) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        String seen = "no part";
        while (System.currentTimeMillis() < end) {
            MemoryTable.Part part = e.onEngine(() -> {
                Doc d = e.engine.files().get(fileId);
                Circuit root = d.file().getMainCircuit();
                List<MemoryTable.Part> parts = MipsParts.collect(root, d.project().getCircuitState(root));
                return parts.size() == 1 ? parts.get(0) : null;
            });
            if (part != null && want.entrySet().stream().allMatch(w -> part.word(w.getKey()) == w.getValue())) {
                return part;
            }
            seen = part == null ? "no part" : "other words";
            Thread.sleep(20);
        }
        throw new AssertionError("the Data Memory never took the image: " + seen);
    }

    void awaitCycle(String fileId, int mark, long cycle) {
        e.client.awaitNotificationAfter(mark, "sim.state", st -> st.get("fileId").getAsString().equals(fileId)
                && st.get("cycle").getAsLong() == cycle);
    }

    // ---- 골든 일곱을 ref-mips에 ----

    @Test
    void everyHallymMipsGoldenLoadsIntoTheReferenceCpuAndReadsBack() throws Exception {
        String[] f = open(workspace());
        String fileId = f[0];
        String im = component(fileId, f[1], "Instruction Memory", 0);
        String dm = component(fileId, f[1], "Data Memory", 0);
        for (String name : GOLDENS) {
            ExecutableImage img = image(prog(name));
            int mark = e.client.mark();
            JsonObject r = load(fileId, prog(name));
            assertTrue(r.get("loaded").getAsBoolean(), r.toString());
            assertEquals("prog/" + name + ".hmx", r.get("source").getAsString(), "the track A attribute: relative");
            JsonObject s = r.getAsJsonObject("summary");
            assertEquals(ExecutableImage.hex(img.entry()), s.get("entry").getAsString(), name);
            assertEquals(img.segments().size(), s.getAsJsonArray("segments").size(), name);
            assertEquals(img.regs().size(), s.getAsJsonArray("regs").size());
            assertEquals("$sp", s.getAsJsonArray("regs").get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("same", s.getAsJsonObject("source").get("status").getAsString(), name + ": the .s is beside");
            assertTrue(s.getAsJsonArray("instructions").size() > 0);
            assertEquals(name.equals("no-handler"), s.getAsJsonArray("facts").toString().contains("noHandler"), name);
            JsonObject seg0 = s.getAsJsonArray("segments").get(0).getAsJsonObject();
            assertEquals("text", seg0.get("kind").getAsString());
            assertEquals(im, seg0.get("componentId").getAsString());
            assertEquals(img.segments(ExecutableImage.Kind.TEXT).get(0).count, seg0.get("words").getAsLong());
            awaitCycle(fileId, mark, 0); // 불러오면 처음부터(Reset)
            e.client.awaitNotificationAfter(mark, "model.changed", c -> c.get("fileId").getAsString().equals(fileId));

            // .text: 부품 내용 = 이미지 워드(시작 코드 포함, 파일 주소 그대로), 디스어셈블도 같은 워드
            SortedMap<Long, Integer> text = img.words(img.segments(ExecutableImage.Kind.TEXT));
            assertEquals(text, contents(im), name);
            assertEquals(text, disasmWords(fileId, im), name);
            // .data: 돌고 있는 Data Memory에서 되읽기(구간이 주지 않은 칸은 0), reg $sp는 깊이 기준(내용의 주소만 있는 줄)
            MemoryTable.Part part = runningDataMemory(fileId, img.dataWords());
            for (Map.Entry<Long, Integer> w : img.dataWords().entrySet()) {
                assertEquals((int) w.getValue(), part.word(w.getKey()), name + " @" + Long.toHexString(w.getKey()));
            }
            assertEquals(0, part.word(0x10010000L + 0x2000L * 4), "memory the image does not give is 0");
            assertEquals(img.dataWords(), contents(dm), name);
            JsonArray comps = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", f[1]))
                    .getAsJsonArray("components");
            String dmText = Fixtures.byId(comps, dm).getAsJsonObject("attrs").get("contents").getAsString();
            assertTrue(List.of(dmText.split("\n")).contains("7fffffe4"), name + ": the depth base line: " + dmText);

            // 사실: 사이클 0에 PC ≠ entry면 한 줄(진단이 아님). ref-mips의 PC 시작은 0x00400024
            assertEquals("0x00400024", settledPc(fileId));
            JsonObject facts = facts(fileId);
            JsonObject pc = fact(facts, "pcEntry");
            if (img.entry() == 0x00400024L) {
                assertNull(pc, name + ": " + facts);
            } else {
                assertNotNull(pc, name + ": " + facts);
                assertEquals("PC 0x00400024 · 실행 이미지 진입점 " + ExecutableImage.hex(img.entry()),
                        pc.get("ko").getAsString());
                assertEquals("PC 0x00400024 · executable image entry " + ExecutableImage.hex(img.entry()),
                        pc.get("en").getAsString());
                assertEquals(im, pc.getAsJsonArray("components").get(0).getAsString());
            }
            JsonObject prog = facts.getAsJsonObject("program");
            assertEquals(name + ".hmx", prog.get("name").getAsString());
            assertEquals(ExecutableImage.hex(img.entry()), prog.get("entry").getAsString());
            assertTrue(prog.get("failure").isJsonNull());
            assertEquals(r.get("loadedAt").getAsLong(), prog.get("loadedAt").getAsLong());
            JsonObject mem = prog.getAsJsonArray("memories").get(0).getAsJsonObject();
            assertEquals(im, mem.get("componentId").getAsString());
            ExecutableImage.Segment t = img.segments(ExecutableImage.Kind.TEXT).get(0);
            assertEquals(ExecutableImage.count(t.count, "word") + " (" + t.range() + "), entry "
                    + ExecutableImage.hex(img.entry()), mem.get("text").getAsString(), "the Instruction Memory body");
        }
        // 한 번 되돌리면 앞 프로그램(space-gap)으로, 되돌리기는 불러오기마다 한 단계
        e.client.call("edit.undo", params("fileId", fileId));
        assertEquals(image(prog("space-gap")).words(image(prog("space-gap")).segments(ExecutableImage.Kind.TEXT)),
                contents(im));
    }

    // ---- 요약의 사실과 디스어셈블 ----

    @Test
    void theSummaryAndTheDisassemblyUseTheImageSymbols() throws Exception {
        String[] f = open(workspace());
        String im = component(f[0], f[1], "Instruction Memory", 0);
        JsonObject r = load(f[0], prog("data"));
        JsonObject s = r.getAsJsonObject("summary");
        assertEquals("entry 0x00400024 (main)", s.get("entryLine").getAsString());
        assertEquals("0x7fffffe4", s.getAsJsonArray("regs").get(0).getAsJsonObject().get("value").getAsString());
        JsonObject data = s.getAsJsonArray("segments").get(1).getAsJsonObject();
        assertEquals("data", data.get("kind").getAsString());
        assertEquals(28, data.get("bytes").getAsLong());
        assertEquals(7, data.get("words").getAsLong());
        assertEquals("28 bytes = 7 words (0x10010000–0x1001001b)", data.get("text").getAsString());
        assertEquals(1, s.getAsJsonArray("stackBase").size(), "reg $sp: the merged Data Memory's depth base");
        assertEquals("원본 파일 data.s: 내보낸 때와 같음.",
                s.getAsJsonObject("source").getAsJsonObject("text").get("ko").getAsString());
        assertFalse(s.getAsJsonObject("source").get("warn").getAsBoolean());
        assertEquals("Hallym MIPS 2.4.0", s.get("producedBy").getAsString());

        JsonObject d = e.client.callObject("mips.disasm", params("fileId", f[0], "componentId", im));
        assertTrue(d.get("symbols").getAsBoolean());
        assertEquals(27, d.get("words").getAsInt());
        assertEquals("0x00400024", d.get("entry").getAsString());
        JsonArray lines = d.getAsJsonArray("lines");
        assertEquals("jal 0x00400024 [main]", lines.get(5).getAsJsonObject().get("text").getAsString(),
                "the start-up code's jal main, with the symbol");
        JsonObject main = lines.get(9).getAsJsonObject();
        assertEquals("0x00400024", main.get("addr").getAsString());
        assertEquals("[\"main\"]", main.getAsJsonArray("labels").toString());
        assertTrue(main.get("entry").getAsBoolean());
        assertEquals("bne $17, $0, -16 [next-0x00400048]", lines.get(18).getAsJsonObject().get("text").getAsString());
        // 한 곳부터 몇 줄
        JsonObject part = e.client.callObject("mips.disasm", params("fileId", f[0], "componentId", im, "from",
                "0x00400024", "count", 2));
        assertEquals(2, part.getAsJsonArray("lines").size());
        assertEquals("0x00400028", part.getAsJsonArray("lines").get(1).getAsJsonObject().get("addr").getAsString());
        assertEquals(-32602, e.client.fail("mips.disasm", params("fileId", f[0], "componentId", im, "count", 0)).code);
        assertEquals(1, e.client.fail("mips.disasm", params("fileId", f[0], "componentId", f[1])).code);

        // 처리기 없이 어셈블한 이미지: 사실 줄, jr $ra 줄은 없음
        JsonObject nh = load(f[0], prog("no-handler")).getAsJsonObject("summary");
        assertEquals(1, nh.getAsJsonArray("facts").size(), nh.toString());
        assertEquals("noHandler", nh.getAsJsonArray("facts").get(0).getAsJsonObject().get("id").getAsString());
        assertTrue(nh.get("emptied").isJsonObject(), "no .data: the program's Data Memory is emptied");
    }

    // ---- 고르기 ----

    @Test
    void severalMemoriesGiveTheCandidatesAndAPickLoads() throws Exception {
        workspace();
        JsonObject nf = e.client.callObject("file.new", params());
        String fileId = nf.get("fileId").getAsString();
        String main = nf.get("main").getAsString();
        String a = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", MIPS,
                "name", "Instruction Memory", "loc", Client.xy(400, 200))).get("id").getAsString();
        String b = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", MIPS,
                "name", "Instruction Memory", "loc", Client.xy(400, 500))).get("id").getAsString();
        e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", MIPS, "name",
                "Data Memory", "loc", Client.xy(800, 300)));
        JsonObject asked = load(fileId, prog("data"));
        assertFalse(asked.get("loaded").getAsBoolean());
        JsonObject choose = asked.getAsJsonObject("choose");
        assertEquals("text", choose.get("kind").getAsString());
        assertTrue(choose.get("segment").getAsString().startsWith(".text 0x00400000"), choose.toString());
        assertEquals(2, choose.getAsJsonArray("candidates").size());
        List<String> ids = new ArrayList<>();
        for (JsonElement c : choose.getAsJsonArray("candidates")) {
            ids.add(c.getAsJsonObject().get("componentId").getAsString());
            assertTrue(c.getAsJsonObject().get("name").getAsString().startsWith("main › Instruction Memory ("), c.toString());
        }
        assertEquals(new java.util.TreeSet<>(List.of(a, b)), new java.util.TreeSet<>(ids));
        assertTrue(contents(a).isEmpty() && contents(b).isEmpty(), "asking changes nothing");

        JsonObject picked = load(fileId, prog("data"), "picks", Map.of("text", b));
        assertTrue(picked.get("loaded").getAsBoolean(), picked.toString());
        assertTrue(contents(a).isEmpty());
        assertEquals(27, contents(b).size());
        assertEquals(prog("data").getPath(), picked.get("source").getAsString(), "a new file: absolute");

        // 고른 부품(우클릭): 그 부품이 모두 담는다
        JsonObject target = load(fileId, prog("pseudo"), "target", a);
        assertTrue(target.get("loaded").getAsBoolean());
        assertEquals(34, contents(a).size());
        assertEquals(-32602, e.client.fail("mips.load", params("fileId", fileId, "path", prog("data").getPath(),
                "picks", Map.of("data", a))).code, "a pick of the wrong kind");
        assertEquals(1, e.client.fail("mips.load", params("fileId", fileId, "path", prog("data").getPath(),
                "target", main)).code);
    }

    // ---- 실패하면 올라가 있던 것을 그대로 ----

    @Test
    void aFailedLoadOrReloadKeepsTheLoadedProgramAndTheSimulation() throws Exception {
        String[] f = open(workspace());
        String fileId = f[0];
        String im = component(fileId, f[1], "Instruction Memory", 0);
        JsonObject first = load(fileId, prog("data"));
        long loadedAt = first.get("loadedAt").getAsLong();
        SortedMap<Long, Integer> data = contents(im);

        // 틀린 파일을 고르면 줄 번호와 할 일, 아무것도 바뀌지 않는다
        File broken = new File(TESTS, "hmx/truncated.hmx");
        JsonObject bad = load(fileId, broken);
        assertFalse(bad.get("loaded").getAsBoolean());
        JsonObject p0 = bad.getAsJsonArray("problems").get(0).getAsJsonObject();
        assertTrue(p0.get("line").getAsInt() > 0, bad.toString());
        assertTrue(p0.getAsJsonObject("text").get("ko").getAsString().startsWith(p0.get("line").getAsInt() + "번째 줄: "),
                bad.toString());
        assertEquals(data, contents(im));

        // 몇 사이클 돈 뒤 .hmx가 깨지면: 다시 불러오지 못함, 올라가 있던 것과 사이클 그대로, 마지막으로 불러온 시각
        int mark = e.client.mark();
        e.client.call("sim.cycles", params("fileId", fileId, "n", 3));
        awaitCycle(fileId, mark, 3);
        Files.copy(broken.toPath(), prog("data").toPath(), StandardCopyOption.REPLACE_EXISTING);
        assertTrue(prog("data").setLastModified(System.currentTimeMillis() + 5000));
        int m2 = e.client.mark();
        e.onEngine(() -> {
            e.engine.programs().watchNow();
            return null;
        });
        JsonObject failed = e.client.awaitNotificationAfter(m2, "mips.reloaded", x -> true);
        assertFalse(failed.get("ok").getAsBoolean());
        assertEquals("changed", failed.get("reason").getAsString());
        assertEquals("data.hmx", failed.get("file").getAsString());
        assertEquals(loadedAt, failed.getAsJsonObject("kept").get("loadedAt").getAsLong());
        assertEquals(data, contents(im));
        assertEquals(3, e.client.callObject("sim.state", params("fileId", fileId)).get("cycle").getAsLong(),
                "the simulation goes on as it was");
        JsonObject kept = facts(fileId).getAsJsonObject("program");
        assertEquals(loadedAt, kept.get("loadedAt").getAsLong());
        JsonObject failure = kept.getAsJsonObject("failure");
        assertEquals("data.hmx", failure.get("file").getAsString());
        assertTrue(failure.getAsJsonArray("problems").size() > 0);
        assertEquals("0x00400024", kept.get("entry").getAsString(), "the loaded program's entry is still known");

        // 고친 파일(다른 프로그램): 다시 불러오고 처음부터, 실패가 걷힌다
        Files.copy(prog("pseudo").toPath(), prog("data").toPath(), StandardCopyOption.REPLACE_EXISTING);
        assertTrue(prog("data").setLastModified(System.currentTimeMillis() + 10_000));
        int m3 = e.client.mark();
        e.onEngine(() -> {
            e.engine.programs().watchNow();
            return null;
        });
        JsonObject ok = e.client.awaitNotificationAfter(m3, "mips.reloaded", x -> true);
        assertTrue(ok.get("ok").getAsBoolean(), ok.toString());
        awaitCycle(fileId, m3, 0);
        ExecutableImage pseudo = image(prog("pseudo"));
        assertEquals(pseudo.words(pseudo.segments(ExecutableImage.Kind.TEXT)), contents(im));
        JsonObject after = facts(fileId).getAsJsonObject("program");
        assertTrue(after.get("failure").isJsonNull());
        assertTrue(after.get("loadedAt").getAsLong() >= loadedAt);
        // 다시 불러오기는 되돌리기 한 단계
        e.client.call("edit.undo", params("fileId", fileId));
        assertEquals(data, contents(im));
        // 파일이 그대로면 아무것도 하지 않는다
        JsonObject manual = e.client.callObject("mips.reload", params("fileId", fileId));
        assertTrue(manual.toString().contains("\"ok\":true"), manual.toString());
    }

    // ---- 파일을 연 때와 Reset 때 ----

    @Test
    void anExportedAgainImageLoadsWhenTheFileOpensAndAtReset() throws Exception {
        File circ = workspace();
        String[] f = open(circ);
        load(f[0], prog("data"));
        e.client.call("file.save", params("fileId", f[0]));
        e.client.call("file.close", params("fileId", f[0]));
        // 앱이 꺼져 있는 동안 다시 내보냄
        Files.copy(prog("branches").toPath(), prog("data").toPath(), StandardCopyOption.REPLACE_EXISTING);
        int mark = e.client.mark();
        String[] g = open(circ);
        String im = component(g[0], g[1], "Instruction Memory", 0);
        e.onEngine(() -> {
            e.engine.programs().watchNow();
            return null;
        });
        JsonObject opened = e.client.awaitNotificationAfter(mark, "mips.reloaded", x -> true);
        assertTrue(opened.get("ok").getAsBoolean());
        assertEquals("open", opened.get("reason").getAsString());
        ExecutableImage branches = image(prog("branches"));
        assertEquals(branches.words(branches.segments(ExecutableImage.Kind.TEXT)), contents(im));
        assertTrue(e.client.callObject("file.dirty", params("fileId", g[0])).get("dirty").getAsBoolean());
        assertNull(facts(g[0]).getAsJsonObject("program").get("failure").isJsonNull() ? null : "failure");

        // Reset: 감시가 아직 못 본 변경도 먼저 넣는다
        Files.copy(prog("pseudo").toPath(), prog("data").toPath(), StandardCopyOption.REPLACE_EXISTING);
        assertTrue(prog("data").setLastModified(System.currentTimeMillis() + 5000));
        e.client.call("sim.reset", params("fileId", g[0]));
        ExecutableImage pseudo = image(prog("pseudo"));
        assertEquals(pseudo.words(pseudo.segments(ExecutableImage.Kind.TEXT)), contents(im));
    }

    @Test
    void aMissingImageWhenTheFileOpensIsAFailureThatKeepsTheSavedProgram() throws Exception {
        File circ = workspace();
        String[] f = open(circ);
        load(f[0], prog("data"));
        e.client.call("file.save", params("fileId", f[0]));
        e.client.call("file.close", params("fileId", f[0]));
        Files.delete(prog("data").toPath());
        String[] g = open(circ);
        int mark = e.client.mark();
        e.onEngine(() -> {
            e.engine.programs().watchNow();
            return null;
        });
        JsonObject r = e.client.awaitNotificationAfter(mark, "mips.reloaded", x -> true);
        assertFalse(r.get("ok").getAsBoolean());
        assertTrue(r.getAsJsonObject("kept").get("loadedAt").isJsonNull(), "kept: the program saved in the .circ");
        assertEquals("실행 이미지 파일이 그 자리에 없습니다. Hallym MIPS에서 다시 내보내거나 Load Program… 단추로 파일을 고르세요.",
                r.getAsJsonArray("problems").get(0).getAsJsonObject().getAsJsonObject("text").get("ko").getAsString());
        assertFalse(e.client.callObject("file.dirty", params("fileId", g[0])).get("dirty").getAsBoolean());
        assertEquals(27, contents(component(g[0], g[1], "Instruction Memory", 0)).size());
    }

    // ---- .s 경로의 옛 파일(D-141) ----

    @Test
    void anAssemblySourceGivesItsFactAndLoadingAnImageReplacesIt() throws Exception {
        File circ = workspace();
        String text = new String(Files.readAllBytes(circ.toPath()), StandardCharsets.UTF_8);
        String imTag = "<comp lib=\"7\" loc=\"(6400,400)\" name=\"Instruction Memory\"/>";
        assertTrue(text.contains(imTag));
        text = text.replace(imTag, "<comp lib=\"7\" loc=\"(6400,400)\" name=\"Instruction Memory\">\n"
                + "      <a name=\"source\" val=\"prog/data.s\"/>\n    </comp>");
        Files.write(circ.toPath(), text.getBytes(StandardCharsets.UTF_8));
        String[] f = open(circ);
        assertNotNull(fact(facts(f[0]), "assemblySource"));
        // .s를 넘기면 이미지 없이 그 문장 하나
        JsonObject s = load(f[0], tmp.resolve("prog/data.s").toFile());
        assertFalse(s.get("loaded").getAsBoolean());
        assertEquals(AssemblySource.FACT.ko, s.getAsJsonArray("problems").get(0).getAsJsonObject()
                .getAsJsonObject("text").get("ko").getAsString());
        // 감시는 .s 경로를 보지 않는다
        int mark = e.client.mark();
        e.onEngine(() -> {
            e.engine.programs().watchNow();
            return null;
        });
        assertTrue(e.client.notificationsAfter(mark, "mips.reloaded").isEmpty());
        // .hmx를 넣으면 source가 그 .hmx로, 사실이 없어진다
        assertTrue(load(f[0], prog("data")).get("loaded").getAsBoolean());
        assertNull(fact(facts(f[0]), "assemblySource"));
        JsonArray comps = e.client.callObject("model.circuit", params("fileId", f[0], "circuitId", f[1]))
                .getAsJsonArray("components");
        assertEquals("prog/data.hmx", Fixtures.byName(comps, "Instruction Memory").get(0).getAsJsonObject("attrs")
                .get("source").getAsString());
    }

    @Test
    void aReadOnlyFileIsNotLoaded() throws Exception {
        File circ = workspace();
        JsonObject o = e.client.callObject("file.open", params("path", circ.getPath(), "readOnly", true));
        Client.Failure fail = e.client.fail("mips.load", params("fileId", o.get("fileId").getAsString(), "path",
                prog("data").getPath()));
        assertEquals(3, fail.code);
        assertEquals("readOnly", fail.reason());
    }

    // ---- Console ----

    @Test
    void theConsoleStreamsTheProgramOutputAndResetClearsIt() throws Exception {
        String[] f = open(workspace());
        String fileId = f[0];
        load(fileId, prog("data"));
        int mark = e.client.mark();
        e.client.call("sim.cycles", params("fileId", fileId, "n", 80));
        awaitCycle(fileId, mark, 80);
        e.client.awaitNotificationAfter(mark, "mips.console", c -> c.toString().contains("\"exited\":true"));
        StringBuilder out = new StringBuilder();
        for (JsonObject c : e.client.notificationsAfter(mark, "mips.console")) {
            JsonObject con = c.getAsJsonArray("consoles").get(0).getAsJsonObject();
            if (con.has("text")) {
                out.setLength(0);
                out.append(con.get("text").getAsString());
            } else {
                out.append(con.get("append").getAsString());
            }
        }
        assertEquals("sum = 14", out.toString(), "the streamed pieces make the program's output (data.regs)");
        JsonObject full = e.client.callObject("mips.console", params("fileId", fileId));
        JsonObject con = full.getAsJsonArray("consoles").get(0).getAsJsonObject();
        assertEquals("Console", con.get("name").getAsString());
        assertEquals("sum = 14", con.get("text").getAsString());
        assertTrue(con.get("exited").getAsBoolean());
        int m2 = e.client.mark();
        e.client.call("sim.reset", params("fileId", fileId));
        JsonObject cleared = e.client.awaitNotificationAfter(m2, "mips.console", x -> true);
        JsonObject c2 = cleared.getAsJsonArray("consoles").get(0).getAsJsonObject();
        assertEquals("", c2.get("text").getAsString(), "Reset clears the Console (v1)");
        assertFalse(c2.get("exited").getAsBoolean());
    }

    @Test
    void aFileWithoutMipsPartsHasNoProgram() throws Exception {
        String[] f = open(new File(Fixtures.CIRC_DIR, "gates.circ"));
        JsonObject facts = facts(f[0]);
        assertTrue(facts.get("program").isJsonNull());
        assertEquals(0, facts.getAsJsonArray("facts").size());
        assertEquals(0, e.client.callObject("mips.console", params("fileId", f[0])).getAsJsonArray("consoles").size());
    }
}
