/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static kr.ac.hallym.hcs.engine.Client.xy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.mips.MemoryTable;
import kr.ac.hallym.hcs.engine.mips.MipsParts;
import kr.ac.hallym.hcs.mips.image.ExecutableImage;
import kr.ac.hallym.hcs.mips.image.HmxParser;
import kr.ac.hallym.hcs.regress.CircNormalizer;

/**
 * 엔진에서 본 합친 Data Memory(D-140): 새로 놓는 목록에 Stack이 없고, 옛 Stack 파일은 전처럼 열려 저장되며 사실 한 줄을
 * 준다(진단이 아님). 엔진이 놓은 Data Memory는 두 영역을 적는다. 도는 참조 CPU의 메모리를 Memory 표로 읽는다.
 */
class MergedDataMemoryEngineTest {
    static final String MIPS = "kr.ac.hallym.hcs.mips.MipsLibrary";
    static final File TESTS = Fixtures.REF_MIPS.getParentFile().getParentFile();
    static final File V1_STACK = new File(TESTS, "mips/ref-mips-v1-stack.circ");
    static final String FACT_KO = "이 회로는 따로 된 Stack 부품을 씁니다. 새 Data Memory는 스택 영역을 함께 맡습니다.";

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

    JsonObject open(File f) {
        return e.client.callObject("file.open", params("path", f.getPath()));
    }

    List<String> mipsTools(String fileId) {
        List<String> out = new ArrayList<>();
        for (JsonElement le : e.client.call("model.library", params("fileId", fileId)).getAsJsonArray()) {
            JsonObject l = le.getAsJsonObject();
            if (!l.get("lib").isJsonNull() && l.get("lib").getAsString().equals(MIPS)) {
                for (JsonElement t : l.getAsJsonArray("tools")) {
                    out.add(t.getAsJsonObject().get("name").getAsString());
                }
            }
        }
        return out;
    }

    JsonArray facts(String fileId) {
        return e.client.callObject("mips.facts", params("fileId", fileId)).getAsJsonArray("facts");
    }

    @Test
    void theLibraryOffersOneDataMemoryAndNoStack() throws Exception {
        JsonObject created = e.client.callObject("file.new", params());
        assertEquals(List.of("Instruction Memory", "Data Memory", "Console", "Radix Probe"),
                mipsTools(created.get("fileId").getAsString()), "Stack is not offered for new placement");
        JsonObject ref = open(Fixtures.REF_MIPS);
        assertFalse(mipsTools(ref.get("fileId").getAsString()).contains("Stack"));
        assertEquals(0, facts(ref.get("fileId").getAsString()).size(), "the reference CPU uses one Data Memory");
    }

    /** 옛 Stack 파일: 전처럼 열리고(Stack 부품이 그대로), 사실 한 줄을 주며, 엔진이 저장해도 원래 파일과 같다(D-006 기준). */
    @Test
    void anOldStackFileOpensGivesTheFactAndSavesUnchanged() throws Exception {
        JsonObject opened = open(V1_STACK);
        String fileId = opened.get("fileId").getAsString();
        assertEquals(0, opened.getAsJsonArray("messages").size(), opened.toString());
        JsonObject s = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId",
                opened.get("main").getAsString()));
        List<JsonObject> stacks = Fixtures.byName(s.getAsJsonArray("components"), "Stack");
        assertEquals(1, stacks.size());
        JsonObject stack = stacks.get(0);
        assertEquals(MIPS, stack.get("lib").getAsString());
        assertEquals("0x7ffffffc", stack.getAsJsonObject("attrs").get("top").getAsString());
        JsonObject dm = Fixtures.byName(s.getAsJsonArray("components"), "Data Memory").get(0);
        assertEquals("0x10010000", dm.getAsJsonObject("attrs").get("base").getAsString(), "v1 region kept");
        assertEquals("0x0", dm.getAsJsonObject("attrs").get("stacksize").getAsString(), "no stack region");

        JsonArray facts = facts(fileId);
        assertEquals(1, facts.size());
        JsonObject f = facts.get(0).getAsJsonObject();
        assertEquals("separateStack", f.get("id").getAsString());
        assertEquals(FACT_KO, f.get("ko").getAsString());
        assertEquals("This circuit uses a separate Stack part. New Data Memory parts also hold the stack region.",
                f.get("en").getAsString());
        assertEquals(List.of(stack.get("id").getAsString()), List.of(f.getAsJsonArray("components").get(0)
                .getAsString()));

        File out = tmp.resolve("resaved.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", out.getPath()));
        String before = new String(Files.readAllBytes(V1_STACK.toPath()), StandardCharsets.UTF_8);
        String after = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertEquals(CircNormalizer.normalize(before), CircNormalizer.normalize(after));
    }

    /** 엔진이 놓은 Data Memory: 두 영역이 .circ에 적히고(base, stacksize) MIPS 라이브러리 아래 도구 속성은 적히지 않는다. */
    @Test
    void aDataMemoryPlacedByTheEngineSavesBothRegions() throws Exception {
        JsonObject created = e.client.callObject("file.new", params());
        String fileId = created.get("fileId").getAsString();
        String main = created.get("main").getAsString();
        JsonObject r = e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", MIPS,
                "name", "Data Memory", "loc", xy(600, 300)));
        JsonObject c = Fixtures.byId(e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main))
                .getAsJsonArray("components"), r.get("id").getAsString());
        assertEquals("0x10000000", c.getAsJsonObject("attrs").get("base").getAsString());
        assertEquals("0x40000", c.getAsJsonObject("attrs").get("stacksize").getAsString());
        assertEquals("0x7ffffffc", c.getAsJsonObject("attrs").get("stacktop").getAsString());
        assertEquals(0, facts(fileId).size());
        File out = tmp.resolve("dm.circ").toFile();
        e.client.call("file.save", params("fileId", fileId, "path", out.getPath()));
        String xml = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml.contains("    <comp lib=\"7\" loc=\"(600,300)\" name=\"Data Memory\">\n"
                + "      <a name=\"base\" val=\"0x10000000\"/>\n"
                + "      <a name=\"stacksize\" val=\"0x40000\"/>\n"
                + "    </comp>\n"), xml);
        assertTrue(xml.contains("<lib desc=\"jar#hcs-mips.jar#" + MIPS + "\" name=\"7\"/>"), "no <tool>: " + xml);
    }

    /** 참조 CPU(합친 Data Memory)로 factorial을 몇 사이클 돌린 뒤의 Memory 표: .data(라벨 msg)와 스택($sp 표시). */
    @Test
    void theMemoryTableReadsTheRunningReferenceCpu() throws Exception {
        ExecutableImage img = HmxParser.read(new File(TESTS, "hmx/mips/factorial.hmx")).image;
        assertNotNull(img);
        JsonObject opened = open(Fixtures.REF_MIPS);
        String fileId = opened.get("fileId").getAsString();
        String main = opened.get("main").getAsString();
        JsonArray comps = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main))
                .getAsJsonArray("components");
        setContents(fileId, main, Fixtures.byName(comps, "Instruction Memory").get(0), img.textWords());
        setContents(fileId, main, Fixtures.byName(comps, "Data Memory").get(0), img.dataWords());
        int mark = e.client.mark();
        e.client.call("sim.reset", params("fileId", fileId));
        e.client.awaitNotificationAfter(mark, "sim.state", st -> st.get("cycle").getAsLong() == 0
                && st.get("fileId").getAsString().equals(fileId));
        int m2 = e.client.mark();
        e.client.call("sim.cycles", params("fileId", fileId, "n", 30));
        e.client.awaitNotificationAfter(m2, "sim.state", st -> st.get("cycle").getAsLong() == 30
                && st.get("fileId").getAsString().equals(fileId));

        Map<Long, List<String>> labels = new TreeMap<>();
        for (Map.Entry<String, Long> sym : img.symbols().entrySet()) {
            if (sym.getValue() >= 0x10000000L && sym.getValue() < 0x7FFFFFFFL) {
                labels.put(sym.getValue(), List.of(sym.getKey()));
            }
        }
        Object[] got = e.onEngine(() -> {
            Doc d = e.engine.files().get(fileId);
            Circuit root = d.file().getMainCircuit();
            CircuitState state = d.project().getCircuitState(root);
            long sp = -1;
            for (Component x : root.getNonWires()) {
                if ("$29".equals(x.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL))) {
                    Value v = state.getValue(x.getEnd(0).getLocation());
                    sp = v.toIntValue() & 0xffffffffL;
                }
            }
            Map<String, Long> ptr = new LinkedHashMap<>();
            ptr.put("$sp", sp);
            List<MemoryTable.Part> parts = MipsParts.collect(root, state);
            return new Object[] {parts.size(), sp, MemoryTable.build(parts, labels, ptr)};
        });
        assertEquals(1, got[0], "one Data Memory holds data and stack");
        long sp = (Long) got[1];
        assertTrue(sp < 0x7FFFEFFCL && sp > 0x7FFFEF00L, "the recursion is under way: " + Long.toHexString(sp));
        @SuppressWarnings("unchecked")
        List<MemoryTable.Row> rows = (List<MemoryTable.Row>) got[2];
        List<String> text = new ArrayList<>();
        rows.forEach(r -> text.add(r.toString()));
        assertEquals("[DATA Data Memory 10010000-100fffff]", text.get(0));
        assertEquals("10010000 3d202136 00000020 00000000 00000000 msg@10010000", text.get(1), text.toString());
        int stackAt = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).kind == MemoryTable.Kind.SECTION && rows.get(i).section == MemoryTable.Section.STACK) {
                stackAt = i;
            }
        }
        assertTrue(stackAt > 1, text.toString());
        MemoryTable.Row head = rows.get(stackAt);
        assertEquals(0x7FFFEFFCL, head.base);
        assertEquals(0x7FFFEFFCL - sp, head.depth);
        assertEquals(0x80000000L, head.end);
        MemoryTable.Row last = rows.get(rows.size() - 1);
        assertEquals(sp, (long) last.pointers.get("$sp"), "the $sp mark on the lowest row: " + text);
        // 첫 프레임(main의 jal fact): 0x7fffeff4 = $a0 6, 0x7fffeff8 = $ra(main의 jal 다음 워드). 스택 영역에 쓴 값이다
        MemoryTable.Row first = null;
        for (MemoryTable.Row r : rows) {
            if (r.kind == MemoryTable.Kind.WORDS && r.addr == 0x7FFFEFF0L) {
                first = r;
            }
        }
        assertNotNull(first, text.toString());
        assertEquals(6, first.words[1]);
        assertEquals(img.symbols().get("main").intValue() + 16, first.words[2], "jal fact at main+12");
        for (int i = stackAt + 2; i < rows.size(); i++) {
            assertTrue(rows.get(i).addr < rows.get(i - 1).addr, "stack rows go from the top down");
        }
    }

    void setContents(String fileId, String circuitId, JsonObject comp, Map<Long, Integer> words) {
        StringBuilder sb = new StringBuilder("hcs-words 1\n");
        for (Map.Entry<Long, Integer> w : words.entrySet()) {
            sb.append(String.format("%08x %08x%n", w.getKey(), w.getValue() & 0xffffffffL));
        }
        e.client.call("edit.setAttr", params("fileId", fileId, "circuitId", circuitId, "ids",
                List.of(comp.get("id").getAsString()), "attr", "contents", "value", sb.toString()));
    }
}
