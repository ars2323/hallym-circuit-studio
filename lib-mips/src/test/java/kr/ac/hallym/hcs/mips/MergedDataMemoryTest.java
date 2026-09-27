/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static kr.ac.hallym.hcs.mips.MemoryComponentsTest.clock;
import static kr.ac.hallym.hcs.mips.MemoryComponentsTest.counter;
import static kr.ac.hallym.hcs.mips.MemoryComponentsTest.hexRows;
import static kr.ac.hallym.hcs.mips.MemoryComponentsTest.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * Data Memory 하나가 데이터와 스택 영역을 함께 맡는다(사용자 결정, D-140). 영역은 SPIM 9.1.24 소스 값이고, 경계 안은 읽고
 * 쓰며 밖은 출력하지 않는다. 새로 놓은 부품은 {@code base}·{@code stacksize}를 .circ에 적어 원조 2.7.1 + 이 jar에서
 * 열고 저장해도 바이트가 같고, 속성이 적히지 않은 옛 Data Memory는 v1 영역 그대로다.
 */
class MergedDataMemoryTest {
    static final long[] DATA = {0x10000000L, 0x10100000L};
    static final long[] STACK = {0x7FFC0000L, 0x80000000L};
    static final LogisimVersion V271 = LogisimVersion.get(2, 7, 1);

    @TempDir
    Path tmp;

    // ---- 영역: SPIM 9.1.24 소스에서 ----

    /**
     * SPIM 9.1.24 소스의 메모리 배치 값(tests/spim-oracle/memory-layout.txt). vendor/spim이 있는 동안 이 시험이 소스의
     * {@code #define}과 식을 직접 읽어 대조했고, 지우기 전에 그 값과 자리(파일:줄)를 굳혀 두었다(D-140, D-141).
     */
    static long spimLayout(String name) throws Exception {
        Path file = Path.of(System.getProperty("hcs.testsDir"), "spim-oracle", "memory-layout.txt");
        for (String line : Files.readAllLines(file)) {
            String[] t = line.trim().split("\\s+");
            if (!line.startsWith("#") && t.length >= 3 && t[0].equals(name)) {
                assertTrue(t[1].startsWith("0x") && t[2].matches("[a-z-]+\\.(h|cpp):[0-9,]+"), line);
                return Long.parseLong(t[1].substring(2), 16);
            }
        }
        throw new AssertionError(name + " not in " + file);
    }

    @Test
    void newPartRegionsAreSpimDataAndStackSegments() throws Exception {
        long dataBot = spimLayout("DATA_BOT");
        long dataLimit = spimLayout("DATA_LIMIT");
        long stackTop = spimLayout("STACK_TOP");
        long stackLimit = spimLayout("STACK_LIMIT");
        assertEquals(0x10000000L, dataBot);
        assertEquals(1L << 20, dataLimit);
        assertEquals(0x80000000L, stackTop);
        assertEquals(256L << 10, stackLimit);

        AttributeSet as = new DataMemory().createAttributeSet();
        assertArrayEquals(new long[] {dataBot, dataBot + dataLimit}, MemoryFactory.dataRegion(as));
        assertArrayEquals(new long[] {stackTop - stackLimit, stackTop}, MemoryFactory.stackRegion(as));
        assertArrayEquals(DATA, MemoryFactory.dataRegion(as));
        assertArrayEquals(STACK, MemoryFactory.stackRegion(as));
        assertEquals(2, MemoryFactory.regions(as).length);
        assertArrayEquals(DATA, MemoryFactory.region(as), ".data goes to the data region");

        // 초기 $sp(spim-utils.cpp initialize_registers): STACK_TOP − BYTES_PER_WORD − 4096
        assertEquals(stackTop - 4 - 4096, spimLayout("INITIAL_SP"));
        assertEquals(stackTop - 4 - 4096, DataMemory.SPIM_INITIAL_SP);
        // $gp와 사용자 .data 시작(data.cpp data_begins_at_point): DATA_BOT + 32K, DATA_BOT + 64K
        assertEquals(dataBot + 32 * 1024, spimLayout("GP"));
        assertEquals(dataBot + 64 * 1024, spimLayout("USER_DATA"));
        assertEquals(0x10008000L, dataBot + 32 * 1024);
        assertEquals(0x10010000L, dataBot + 64 * 1024);
    }

    /** 저장 기준값은 v1 Data Memory 그대로다: 속성이 적히지 않은 옛 부품은 전과 같은 데이터 영역 하나다. */
    @Test
    void savedBaselineIsTheV1DataMemory() {
        DataMemory dm = new DataMemory();
        assertEquals(0x10010000, dm.getDefaultAttributeValue(MemoryFactory.BASE, V271));
        assertEquals(0x00100000, dm.getDefaultAttributeValue(MemoryFactory.SIZE, V271));
        assertEquals(0x7FFFFFFC, dm.getDefaultAttributeValue(MemoryFactory.STACK_TOP, V271));
        assertEquals(0, dm.getDefaultAttributeValue(MemoryFactory.STACK_SIZE, V271));
        AttributeSet old = dm.createAttributeSet();
        for (com.cburch.logisim.data.Attribute<?> a : old.getAttributes()) {
            @SuppressWarnings("unchecked")
            com.cburch.logisim.data.Attribute<Object> o = (com.cburch.logisim.data.Attribute<Object>) a;
            old.setValue(o, dm.getDefaultAttributeValue(a, V271));
        }
        assertArrayEquals(new long[] {0x10010000L, 0x10110000L}, MemoryFactory.dataRegion(old));
        assertNull(MemoryFactory.stackRegion(old));

        StackMemory st = new StackMemory(); // 옛 Stack은 새로 놓는 값과 기준값이 같다
        for (com.cburch.logisim.data.Attribute<?> a : st.createAttributeSet().getAttributes()) {
            assertEquals(st.createAttributeSet().getValue(a), st.getDefaultAttributeValue(a, V271), a.getName());
        }
        assertEquals(List.of("top", "size", "contents", "source", "label", "labelfont"), names(st));
        assertEquals(List.of("base", "size", "stacktop", "stacksize", "contents", "source", "label", "labelfont"),
                names(dm));
    }

    static List<String> names(MemoryFactory f) {
        List<String> out = new ArrayList<>();
        for (com.cburch.logisim.data.Attribute<?> a : f.createAttributeSet().getAttributes()) {
            out.add(a.getName());
        }
        return out;
    }

    // ---- 경계와 영역 밖(원조 엔진, 테스트 JVM 안) ----

    /** 한 주소를 읽는 새 Data Memory 하나(쓰기 없음). */
    static Component probe(InProcessSim sim, long addr, String... attrs) {
        CircuitBuilder b = sim.b;
        b.constant("addr", 32, (int) addr, 80, 100);
        b.constant("one", 1, 1, 80, 200);
        b.constant("zero", 1, 0, 80, 240);
        Component dm = b.add(sim.mips, "Data Memory", 600, 300, attrs);
        b.tunnel(dm, DataMemory.ADDR, "addr");
        b.tunnel(dm, DataMemory.MEM_READ, "one");
        b.tunnel(dm, DataMemory.MEM_WRITE, "zero");
        sim.start();
        sim.cycle();
        return dm;
    }

    @Test
    void boundaryWordsAreInsideAndServeTheirWord() throws Exception {
        for (long addr : new long[] {0x10000000L, 0x100FFFFCL, 0x10010000L, 0x7FFC0000L, 0x7FFFFFFCL, 0x7FFFEFFCL}) {
            InProcessSim sim = new InProcessSim();
            Component dm = probe(sim, addr);
            DataMemory.State st = (DataMemory.State) sim.data(dm);
            assertNull(st.problem, Long.toHexString(addr));
            assertTrue(sim.port(dm, DataMemory.READ_DATA).isFullyDefined(), "driven at " + Long.toHexString(addr));
            assertEquals(0, sim.port(dm, DataMemory.READ_DATA).toIntValue(), "never written: 0 like SPIM");
        }
        // 0x100FFFFF: 데이터 영역의 마지막 바이트. 워드 접근이라 정렬 안 된 주소로 알린다(영역 안이라 워드는 낸다)
        InProcessSim sim = new InProcessSim();
        Component dm = probe(sim, 0x100FFFFFL);
        assertEquals(DataMemory.Problem.UNALIGNED, ((DataMemory.State) sim.data(dm)).problem);
        assertTrue(sim.port(dm, DataMemory.READ_DATA).isFullyDefined());
    }

    @Test
    void addressesOutsideBothRegionsFloatAndAreReported() throws Exception {
        Object[][] cases = {
            {0x0FFFFFFCL, DataMemory.Problem.NOT_IN_ANY_REGION}, // DATA_BOT 바로 아래
            {0x10100000L, DataMemory.Problem.NOT_IN_ANY_REGION}, // 데이터 한계(1MB) 바로 위
            {0x20000000L, DataMemory.Problem.NOT_IN_ANY_REGION},
            {0x7FFBFFFCL, DataMemory.Problem.STACK_LIMIT}, // 스택 한계(256KB) 바로 아래
            {0x7FF80000L, DataMemory.Problem.STACK_LIMIT}, // 한계 폭만큼 아래까지
            {0x7FF7FFFCL, DataMemory.Problem.NOT_IN_ANY_REGION},
            {0x80000000L, DataMemory.Problem.NOT_IN_ANY_REGION}, // STACK_TOP(제외)
        };
        for (Object[] c : cases) {
            long addr = (Long) c[0];
            InProcessSim sim = new InProcessSim();
            Component dm = probe(sim, addr);
            DataMemory.State st = (DataMemory.State) sim.data(dm);
            assertEquals(c[1], st.problem, Long.toHexString(addr));
            assertFalse(sim.port(dm, DataMemory.READ_DATA).isFullyDefined(), "floating at " + Long.toHexString(addr));
            if (c[1] == DataMemory.Problem.STACK_LIMIT) {
                assertEquals("Stack use exceeds its limit (256KB)", st.problemText());
            }
        }
    }

    /** 원조 jar -tty: 네 경계 워드에 쓰고 다시 읽으면 값이 나오고, 바로 바깥 네 주소는 쓰지도 내지도 않는다. */
    @Test
    void originalLogisimStoresAtTheBoundariesAndFloatsOutside() throws Exception {
        long[] addrs = {0x10000000L, 0x100FFFFCL, 0x7FFC0000L, 0x7FFFFFFCL,
            0x0FFFFFFCL, 0x10100000L, 0x7FFBFFFCL, 0x80000000L};
        OriginalLogisim o = new OriginalLogisim(tmp);
        CircuitBuilder b = o.b;
        clock(b);
        counter(b, 4, "k", "3", "p", "1");
        Component rom = b.add("Memory", "ROM", 600, 300, "addrWidth", "3", "dataWidth", "32",
                "contents", StackRegionTest.rom(32, addrs));
        b.tunnel(rom, 0, "addr");
        b.tunnel(rom, 1, "k");
        b.tunnel(rom, 2, "one");
        Component xor = b.add("Gates", "XOR Gate", 1000, 700, "width", "32", "inputs", "2");
        b.tunnel(xor, 0, "wdata");
        b.tunnel(xor, 1, "addr");
        b.tunnel(xor, 2, "pattern");
        b.constant("pattern", 32, 0x5A5A5A5A, 800, 760);
        Component not = b.add("Gates", "NOT Gate", 1000, 800);
        b.tunnel(not, 0, "we");
        b.tunnel(not, 1, "p");
        Component dm = b.add(o.mips, "Data Memory", 1400, 300);
        b.tunnel(dm, DataMemory.ADDR, "addr");
        b.tunnel(dm, DataMemory.WRITE_DATA, "wdata");
        b.tunnel(dm, DataMemory.MEM_WRITE, "we");
        b.tunnel(dm, DataMemory.MEM_READ, "p");
        b.tunnel(dm, DataMemory.CLK, "clk");
        b.tunnel(dm, DataMemory.READ_DATA, "rdata");
        b.output("n", 4, 1800, 100);
        b.output("addr", 32, 1800, 180);
        b.output("rdata", 32, 1800, 260);
        b.output("halt", 1, 1800, 340);

        List<String> expected = new ArrayList<>();
        for (int n = 0; n < 16; n += 1) {
            long addr = addrs[n & 7];
            boolean inside = (n & 7) < 4;
            expected.add(row(n, addr, n >= 8 && inside ? addr ^ 0x5A5A5A5AL : null));
        }
        assertEquals(expected, hexRows(o.run("boundaries")));
    }

    /** 새 Data Memory 옆에 옛 Stack을 두면 스택 영역이 겹친다: 어느 쪽이 답할지 정할 수 없어 알린다. */
    @Test
    void anOldStackNextToANewDataMemoryOverlaps() throws Exception {
        InProcessSim sim = new InProcessSim();
        CircuitBuilder b = sim.b;
        b.constant("addr", 32, 0x10010000, 80, 100);
        b.constant("one", 1, 1, 80, 200);
        b.constant("zero", 1, 0, 80, 240);
        Component dm = b.add(sim.mips, "Data Memory", 600, 200);
        Component stack = b.add(sim.mips, "Stack", 600, 500);
        for (Component m : new Component[] {dm, stack}) {
            b.tunnel(m, DataMemory.ADDR, "addr");
            b.tunnel(m, DataMemory.MEM_READ, "one");
            b.tunnel(m, DataMemory.MEM_WRITE, "zero");
        }
        sim.start();
        sim.cycle();
        DataMemory.State st = (DataMemory.State) sim.data(dm);
        assertEquals(DataMemory.Problem.OVERLAP, st.problem);
        assertEquals(0x7FFC0000L, st.problemAddr);
        assertEquals("Memory regions overlap at 7ffc0000", st.problemText());
        assertEquals(DataMemory.Problem.OVERLAP, ((DataMemory.State) sim.data(stack)).problem);
    }

    /** 한 부품 안의 두 영역은 한 메모리다: 학생이 겹치게 두어도 문제로 보지 않는다(같은 워드를 읽고 쓴다). */
    @Test
    void ownRegionsMayOverlapWithoutAProblem() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component dm = probe(sim, 0x7FFFFFF0L, "base", "0x7fff0000", "size", "0x10000");
        assertNull(((DataMemory.State) sim.data(dm)).problem);
    }

    /** 몸체: 두 영역의 범위, 쓰임 줄(데이터 워드 수, 스택 최대 깊이). */
    @Test
    void bodyShowsBothRangesAndTheUsage() {
        DataMemory.State st = new DataMemory.State(WordImage.parse("hcs-words 1\n10010000 00000001 00000002\n"));
        st.data = DATA.clone();
        st.stack = STACK.clone();
        assertEquals(2, st.dataWords());
        assertEquals("data 2 words, stack peak 0 B", DataMemory.mergedUsageLine(st));
        st.memory.write(0x10010010, 7); // 실행 중 새 칸에 sw
        st.memory.write(0x7FFFEFF8, 9); // 스택 칸은 데이터 워드가 아니다
        st.accessed(0x7FFFEFF8);
        st.accessed(0x10010010); // 데이터 접근은 스택 깊이에 세지 않는다
        assertEquals(3, st.dataWords());
        assertEquals(4, st.stackPeak());
        assertEquals(0x7FFFEFF8L, st.lowestAccess());
        assertEquals("data 3 words, stack peak 4 B", DataMemory.mergedUsageLine(st));
        String[] idle = DataMemory.mergedLines(null, DATA, STACK, null);
        assertArrayEquals(new String[] {"data  10000000-100fffff", "stack 7ffc0000-7fffffff"}, idle);
    }

    // ---- 저장(원조 2.7.1 XmlWriter·XmlReader, 이 jar) ----

    static Component find(LogisimFile f, String factory) {
        for (Circuit c : f.getCircuits()) {
            for (Component x : c.getNonWires()) {
                if (x.getFactory().getName().equals(factory)) {
                    return x;
                }
            }
        }
        return null;
    }

    /**
     * 새로 놓은 Data Memory는 base·stacksize만 적힌다. 도구를 쓴 뒤에도(원조 AddTool은 놓을 때 도구 속성을 만든다)
     * {@code <lib>} 아래 {@code <tool>}이 생기지 않는다. 원조 2.7.1 + 이 jar로 다시 열면 같은 두 영역이고, 다시 저장하면
     * 바이트가 같다.
     */
    @Test
    void newPartSavesBaseAndStackSizeAndRoundTripsByteForByte() throws Exception {
        OriginalLogisim o = new OriginalLogisim(tmp);
        Tool tool = o.mips.getTool("Data Memory");
        assertTrue(tool instanceof PlacementTool, tool.getClass().getName());
        assertEquals(0x10000000, tool.getAttributeSet().getValue(MemoryFactory.BASE)); // 도구 속성을 만든다
        o.b.add(o.mips, "Data Memory", 600, 300);
        o.b.commit();
        Path first = tmp.resolve("new.circ");
        CircuitBuilder.save(o.file, first.toFile());
        String xml = Files.readString(first);
        assertTrue(xml.contains("<comp lib=\"7\" loc=\"(600,300)\" name=\"Data Memory\">\n"
                + "      <a name=\"base\" val=\"0x10000000\"/>\n"
                + "      <a name=\"stacksize\" val=\"0x40000\"/>\n"
                + "    </comp>"), xml);
        assertTrue(xml.contains("<lib desc=\"jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary\" name=\"7\"/>"),
                "no <tool> under the MIPS library: " + xml);
        assertFalse(xml.contains("stacktop"), "stack top equals the saved default");

        LogisimFile again = new Loader(null).openLogisimFile(first.toFile());
        Component dm = find(again, "Data Memory");
        assertArrayEquals(DATA, MemoryFactory.dataRegion(dm.getAttributeSet()));
        assertArrayEquals(STACK, MemoryFactory.stackRegion(dm.getAttributeSet()));
        Path second = tmp.resolve("again.circ");
        CircuitBuilder.save(again, second.toFile());
        assertEquals(xml, Files.readString(second));
    }

    /** 원조 AddTool이었다면 도구 속성을 만든 뒤 새로 놓는 값이 도구 기본값으로 적혔을 것이다(PlacementTool이 막는 것). */
    @Test
    void placementToolReportsThePlacementValuesAsToolDefaults() {
        AddTool plain = new AddTool(new DataMemory());
        assertEquals(0x10000000, plain.getAttributeSet().getValue(MemoryFactory.BASE));
        assertEquals(0x10010000, plain.getDefaultAttributeValue(MemoryFactory.BASE, V271), "would be written");
        PlacementTool tool = new PlacementTool(new DataMemory());
        for (com.cburch.logisim.data.Attribute<?> a : tool.getAttributeSet().getAttributes()) {
            assertEquals(tool.getAttributeSet().getValue(a), tool.getDefaultAttributeValue(a, V271), a.getName());
        }
        Tool copy = tool.cloneTool();
        assertTrue(copy instanceof PlacementTool);
        assertEquals(0x40000, copy.getAttributeSet().getValue(MemoryFactory.STACK_SIZE));
    }

    /** 도구 모음에 둔 Data Memory: 속성 없이 적히고, 다시 열어도 새로 놓는 값의 도구다. */
    @Test
    void dataMemoryOnTheToolbarRoundTrips() throws Exception {
        OriginalLogisim o = new OriginalLogisim(tmp);
        o.file.getOptions().getToolbarData().addTool(o.mips.getTool("Data Memory").cloneTool());
        o.b.commit();
        Path first = tmp.resolve("toolbar.circ");
        CircuitBuilder.save(o.file, first.toFile());
        String xml = Files.readString(first);
        assertTrue(xml.contains("<tool lib=\"7\" name=\"Data Memory\"/>"), xml);
        LogisimFile again = new Loader(null).openLogisimFile(first.toFile());
        Tool t = null;
        for (Object x : again.getOptions().getToolbarData().getContents()) {
            if (x instanceof Tool && ((Tool) x).getName().equals("Data Memory")) {
                t = (Tool) x;
            }
        }
        assertNotNull(t);
        assertTrue(t instanceof PlacementTool);
        assertEquals(0x10000000, t.getAttributeSet().getValue(MemoryFactory.BASE));
        Path second = tmp.resolve("toolbar2.circ");
        CircuitBuilder.save(again, second.toFile());
        assertEquals(xml, Files.readString(second));
    }

    /** 속성이 적히지 않은 Data Memory(v1 파일)는 v1 영역 하나로 열린다. v1 값으로 둔 새 부품도 속성 없이 적힌다. */
    @Test
    void anUnattributedDataMemoryOpensAsTheV1Part() throws Exception {
        OriginalLogisim o = new OriginalLogisim(tmp);
        o.b.add(o.mips, "Data Memory", 600, 300, StackRegionTest.OLD_DM);
        o.b.add(o.mips, "Stack", 600, 700);
        o.b.commit();
        Path f = tmp.resolve("v1.circ");
        CircuitBuilder.save(o.file, f.toFile());
        String xml = Files.readString(f);
        assertTrue(xml.contains("<comp lib=\"7\" loc=\"(600,300)\" name=\"Data Memory\"/>"), xml);
        assertTrue(xml.contains("<comp lib=\"7\" loc=\"(600,700)\" name=\"Stack\"/>"), xml);
        LogisimFile again = new Loader(null).openLogisimFile(f.toFile());
        Component dm = find(again, "Data Memory");
        assertArrayEquals(new long[] {0x10010000L, 0x10110000L}, MemoryFactory.dataRegion(dm.getAttributeSet()));
        assertNull(MemoryFactory.stackRegion(dm.getAttributeSet()));
        assertArrayEquals(new long[] {0x7FF00000L, 0x80000000L},
                MemoryFactory.stackRegion(find(again, "Stack").getAttributeSet()));
    }

    /**
     * 부품 목록: Stack은 옛 회로용 이름으로 v1 자리에 남는다(원조가 옛 파일의 Stack을 이 목록에서 찾고, 목록 순서가
     * 옛 파일을 다시 저장할 때의 {@code <tool>} 순서다).
     */
    @Test
    void toolListKeepsTheOldStackInItsV1PlaceForOldCircuits() {
        MipsLibrary lib = new MipsLibrary();
        List<String> names = new ArrayList<>();
        for (Tool t : lib.getTools()) {
            names.add(t.getName());
        }
        assertEquals(List.of("Instruction Memory", "Data Memory", "Stack", "Console", "Radix Probe"), names);
        AddTool stack = (AddTool) lib.getTool("Stack");
        assertEquals("Stack (old circuits)", stack.getDisplayName());
        assertEquals("Stack", ((MemoryFactory) stack.getFactory()).title().get(), "body title unchanged");
        assertEquals("Data Memory", lib.getTool("Data Memory").getDisplayName());
    }
}
