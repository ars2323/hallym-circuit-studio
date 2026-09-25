/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.comp.Component;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** Data Memory는 위로, Stack은 아래로 자라고, 동작하지 않는 경우만 알린다(요구사항 6, D-018). */
class StackRegionTest {
    @TempDir
    Path tmp;

    static final long[] ADDRS = {0x7FFFFFFCL, 0x7FFFFFF8L, 0x7FFFFFF4L, 0x7FFFFFF0L,
        0x7FFFFFF4L, 0x7FFFFFF8L, 0x7FFFFFFCL, 0x7FFFFFFCL};

    static String rom(int width, long[] values) {
        StringBuilder sb = new StringBuilder("addr/data: 3 " + width + "\n");
        for (long v : values) {
            sb.append(Long.toHexString(v)).append(' ');
        }
        return sb.toString().trim() + "\n";
    }

    @Test
    void defaultRegionsGrowFromTheirStart() {
        assertArrayEquals(new long[] {0x10010000L, 0x10110000L},
                MemoryFactory.region(new DataMemory().createAttributeSet()));
        assertArrayEquals(new long[] {0x7FF00000L, 0x80000000L},
                MemoryFactory.region(new StackMemory().createAttributeSet()));
    }

    /** 네 번 밀어 넣고(쓰기) 세 번 꺼낸다(읽기). 깊이는 맨 위에서 마지막 접근 주소까지다. */
    @Test
    void stackDepthFollowsPushesAndPops() throws Exception {
        InProcessSim sim = new InProcessSim();
        CircuitBuilder b = sim.b;
        Component clock = b.add("Wiring", "Clock", 80, 40);
        b.tunnel(clock, 0, "clk");
        b.constant("zero", 1, 0, 80, 80);
        b.constant("one", 1, 1, 80, 120);
        Component c = b.add("Memory", "Counter", 300, 60, "width", "3", "max", "0x7");
        b.tunnel(c, 0, "n");
        b.tunnel(c, 2, "clk");
        b.tunnel(c, 3, "zero");
        b.tunnel(c, 4, "zero");
        b.tunnel(c, 5, "one");
        String[][] roms = {
            {"addr", rom(32, ADDRS), "32"},
            {"we", rom(1, new long[] {1, 1, 1, 1, 0, 0, 0, 0}), "1"},
            {"re", rom(1, new long[] {0, 0, 0, 0, 1, 1, 1, 1}), "1"},
        };
        for (int i = 0; i < roms.length; i += 1) {
            Component r = b.add("Memory", "ROM", 600, 200 + 150 * i, "addrWidth", "3",
                    "dataWidth", roms[i][2], "contents", roms[i][1]);
            b.tunnel(r, 0, roms[i][0]);
            b.tunnel(r, 1, "n");
            b.tunnel(r, 2, "one");
        }
        Component stack = b.add(sim.mips, "Stack", 1000, 300);
        b.tunnel(stack, DataMemory.ADDR, "addr");
        b.tunnel(stack, DataMemory.WRITE_DATA, "addr"); // 자기 주소를 쓴다
        b.tunnel(stack, DataMemory.MEM_WRITE, "we");
        b.tunnel(stack, DataMemory.MEM_READ, "re");
        b.tunnel(stack, DataMemory.CLK, "clk");
        sim.start();
        long[] depth = {4, 8, 12, 16, 12, 8, 4};
        long[] max = {4, 8, 12, 16, 16, 16, 16};
        for (int n = 0; n < 7; n += 1) {
            if (n >= 4) {
                assertEquals((int) ADDRS[n], sim.port(stack, DataMemory.READ_DATA).toIntValue());
            }
            sim.cycle(); // 상승 에지에 n번째 접근이 기록된다
            DataMemory.State st = (DataMemory.State) sim.data(stack);
            assertEquals(depth[n], st.depth(), "depth after step " + n);
            assertEquals(max[n], st.maxDepth(), "max after step " + n);
            assertNull(st.problem);
        }
    }

    /** 한 주소만 읽는 Data Memory와 Stack. 반환: {data, stack}. */
    static Component[] probe(InProcessSim sim, long addr, String... stackAttrs) {
        CircuitBuilder b = sim.b;
        b.constant("addr", 32, (int) addr, 80, 100);
        b.constant("one", 1, 1, 80, 200);
        b.constant("zero", 1, 0, 80, 240);
        Component data = b.add(sim.mips, "Data Memory", 600, 200);
        Component stack = b.add(sim.mips, "Stack", 600, 500, stackAttrs);
        for (Component m : new Component[] {data, stack}) {
            b.tunnel(m, DataMemory.ADDR, "addr");
            b.tunnel(m, DataMemory.MEM_READ, "one");
            b.tunnel(m, DataMemory.MEM_WRITE, "zero");
        }
        sim.start();
        sim.cycle(); // 두 부품이 서로를 알게 한 번 더 전파
        return new Component[] {data, stack};
    }

    static DataMemory.Problem problem(InProcessSim sim, Component c) {
        return ((DataMemory.State) sim.data(c)).problem;
    }

    @Test
    void addressJustBelowTheStackLimitIsReportedAsStackOverflow() throws Exception {
        for (long addr : new long[] {0x7FFFFFECL, 0x7FFFFFE0L}) {
            InProcessSim sim = new InProcessSim();
            Component[] m = probe(sim, addr, "size", "0x10"); // Stack 영역 7FFFFFF0-7FFFFFFF
            assertEquals(DataMemory.Problem.STACK_LIMIT, problem(sim, m[1]), Long.toHexString(addr));
            assertEquals(DataMemory.Problem.STACK_LIMIT, problem(sim, m[0]));
            assertEquals(null, sim.port(m[1], DataMemory.READ_DATA).isFullyDefined() ? "driven" : null);
            String text = DataMemory.describe((DataMemory.State) sim.data(m[1]), sim.state(m[1]));
            assertEquals(true, text.contains("16B"), text);
        }
    }

    @Test
    void addressInNoRegionIsReported() throws Exception {
        for (long addr : new long[] {0x7FFFFFDCL, 0x20000000L, 0x10110000L}) {
            InProcessSim sim = new InProcessSim();
            Component[] m = probe(sim, addr, "size", "0x10");
            assertEquals(DataMemory.Problem.NOT_IN_ANY_REGION, problem(sim, m[0]), Long.toHexString(addr));
            assertEquals(DataMemory.Problem.NOT_IN_ANY_REGION, problem(sim, m[1]));
        }
    }

    @Test
    void addressInsideARegionIsFine() throws Exception {
        for (long addr : new long[] {0x10010000L, 0x7FFFFFFCL, 0x7FFFEFFCL}) {
            InProcessSim sim = new InProcessSim();
            Component[] m = probe(sim, addr);
            assertNull(problem(sim, m[0]));
            assertNull(problem(sim, m[1]));
        }
    }

    @Test
    void overlappingRegionsAreReported() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component[] m = probe(sim, 0x10010000L, "top", "0x1001003c"); // Stack이 Data 위에 겹침
        assertEquals(DataMemory.Problem.OVERLAP, problem(sim, m[0]));
        assertEquals(DataMemory.Problem.OVERLAP, problem(sim, m[1]));
    }

    /** 새 속성 top·size가 원조 2.7.1에서 저장되고 불러와져 같은 영역으로 동작한다. */
    @Test
    void stackTopAttributeWorksInTheOriginalLogisim() throws Exception {
        OriginalLogisim o = new OriginalLogisim(tmp);
        CircuitBuilder b = o.b;
        b.constant("addr", 32, 0x0FFFFFFC, 80, 100);
        b.constant("one", 1, 1, 80, 200);
        b.constant("zero", 1, 0, 80, 240);
        Component stack = b.add(o.mips, "Stack", 600, 300, "top", "0x0ffffffc", "size", "0x100",
                "contents", "hcs-words 1\n0ffffffc cafebabe\n");
        b.tunnel(stack, DataMemory.ADDR, "addr");
        b.tunnel(stack, DataMemory.MEM_READ, "one");
        b.tunnel(stack, DataMemory.MEM_WRITE, "zero");
        b.tunnel(stack, DataMemory.READ_DATA, "rd");
        b.output("rd", 32, 900, 100);
        b.constant("halt", 1, 1, 80, 400);
        b.output("halt", 1, 900, 200);
        List<String[]> rows = o.run("stacktop");
        assertEquals(0xCAFEBABEL, (long) OriginalLogisim.value(rows.get(0)[0]));
    }

    /** #134: SPIM 시작 $sp(0x7FFFEFFC) 아래만 쓰면 깊이는 그 $sp에서 잰다(위 4KB 제외). 그 위를 쓰면 영역 맨 위에서. */
    @Test
    void depthStartsAtSpimInitialStackPointer() {
        DataMemory.State st = new DataMemory.State(WordImage.EMPTY);
        st.region = new long[] {0x7FF00000L, 0x80000000L};
        st.growsDown = true;
        st.accessed(0x7FFFEFF8); // addi $sp,$sp,-4; sw $ra,0($sp)
        assertEquals(4, st.depth());
        st.accessed(0x7FFFEFF0);
        assertEquals(12, st.depth());
        assertEquals(12, st.maxDepth());
        st.accessed(0x7FFFEFF8);
        assertEquals(4, st.depth());
        assertEquals(12, st.maxDepth());

        DataMemory.State top = new DataMemory.State(WordImage.EMPTY);
        top.region = new long[] {0x7FF00000L, 0x80000000L};
        top.growsDown = true;
        top.accessed(0x7FFFFFFC); // 영역 맨 위부터 쓰는 회로(학생 설정): 예전처럼
        assertEquals(4, top.depth());
    }
}
