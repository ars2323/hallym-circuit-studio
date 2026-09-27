/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cburch.logisim.comp.Component;

import kr.ac.hallym.hcs.mips.image.ExecutableImage;

/**
 * 실행 이미지를 합친 Data Memory(D-140)에 넣는다: .data는 데이터 영역에, {@code reg $sp}는 스택 영역의 깊이 기준으로.
 * 스택 내용은 파일에 없으므로 0이다. 넣은 뒤 부품에서 워드·바이트 단위로 되읽어 이미지와 같은지 본다(명세 골든은 다른
 * 작업이 tests/hmx/hallym-mips-v2.4.0/에 들인다. 그 전까지는 같은 모양의 tests/hmx 파일: 데이터(example),
 * 0 구간(zero-runs, 명세의 space-gap과 같은 .data 빈칸), 홀수 길이(data-odd)).
 */
class MergedLoadTest {
    static final Path HMX = AssemblerIntegrationTest.TESTS.resolve("hmx");

    /** Instruction Memory와 새 Data Memory(두 영역) 하나. 반환: {imem, dmem}. */
    static Component[] cpu(InProcessSim sim) {
        Component im = sim.b.add(sim.mips, "Instruction Memory", 600, 100);
        Component dm = sim.b.add(sim.mips, "Data Memory", 600, 400);
        sim.b.commit();
        return new Component[] {im, dm};
    }

    static ProgramLoader.Plan load(InProcessSim sim, String name) {
        ProgramLoader.Loaded l = ProgramLoader.readImage(HMX.resolve(name).toFile());
        assertEquals(List.of(), l.errors, name);
        ProgramLoader.Plan plan = ProgramLoader.plan(l, sim.file.getCircuits(), null, null, name);
        AssemblerIntegrationTest.apply(plan);
        return plan;
    }

    /** example.hmx(reg $sp 0x7ffff000): .data와 깊이 기준이 한 부품의 contents 하나에 들어간다. */
    @Test
    void dataAndRegSpGoToTheOneDataMemory() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component[] c = cpu(sim);
        ProgramLoader.Plan plan = load(sim, "example.hmx");
        assertEquals(List.of(), plan.errors);
        long contentsChanges = plan.changes.stream()
                .filter(ch -> ch.target.component == c[1] && ch.attr == MemoryFactory.CONTENTS).count();
        assertEquals(1, contentsChanges, "one contents change carries the .data words and the depth base");
        assertEquals(1, plan.stackBase.size());
        assertTrue(plan.stackBase.get(0).component == c[1]);
        String all = String.join("\n", plan.notes);
        assertTrue(all.contains("$sp 0x7ffff000: Stack depth base of main › Data Memory (10000000-100fffff),"
                + " stack 7ffc0000-7fffffff"), all);
        WordImage contents = c[1].getAttributeSet().getValue(MemoryFactory.CONTENTS);
        assertEquals(0x7ffff000L, (long) contents.initialSp());
        assertEquals(3, contents.size(), "12 bytes of .data = 3 words, nothing on the stack");

        sim.start();
        DataMemory.State st = (DataMemory.State) sim.data(c[1]);
        ExecutableImage img = plan.image;
        for (Map.Entry<Long, Integer> w : img.dataWords().entrySet()) {
            assertEquals((int) w.getValue(), st.readWord((int) (long) w.getKey()), Long.toHexString(w.getKey()));
        }
        assertEquals('H', st.readByte(0x10010000));
        assertEquals('\n', st.readByte(0x1001000a));
        // 스택 영역: 파일에 내용이 없어 0이다(Hallym MIPS가 인자를 둔 $sp 위도)
        for (long a = 0x7ffff000L; a < 0x80000000L; a += 4) {
            assertEquals(0, st.readWord((int) a), Long.toHexString(a));
        }
        assertEquals(3, st.dataWords());
    }

    /** 깊이는 파일의 $sp에서 잰다(0x7FFFEFFC 규칙보다 먼저): 그 아래 두 번째 워드를 읽으면 8바이트. */
    @Test
    void theStackDepthIsMeasuredFromTheFilesSp() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component im = sim.b.add(sim.mips, "Instruction Memory", 600, 100);
        Component dm = sim.b.add(sim.mips, "Data Memory", 600, 400);
        sim.b.constant("addr", 32, 0x7fffeff8, 100, 400);
        sim.b.tunnel(dm, DataMemory.ADDR, "addr");
        sim.b.constant("one", 1, 1, 100, 500);
        sim.b.tunnel(dm, DataMemory.MEM_READ, "one");
        sim.b.constant("zero", 1, 0, 100, 560);
        sim.b.tunnel(dm, DataMemory.MEM_WRITE, "zero");
        Component clk = sim.b.add("Wiring", "Clock", 100, 620);
        sim.b.tunnel(clk, 0, "clk");
        sim.b.tunnel(dm, DataMemory.CLK, "clk");
        sim.b.commit();
        assertTrue(im != null);
        load(sim, "example.hmx");
        sim.start();
        sim.cycle();
        DataMemory.State st = (DataMemory.State) sim.data(dm);
        assertEquals(0x7ffff000L, st.depthBase());
        assertEquals(8, st.stackPeak());
        assertNull(st.problem);
    }

    /** 0 구간(.data 빈칸)과 홀수 길이 .data: 부품에서 워드·바이트가 이미지와 같다. reg $sp가 없으면 기준을 지운다. */
    @Test
    void zeroGapsAndOddDataReadBackWordByWordAndByteByByte() throws Exception {
        for (String name : new String[] {"zero-runs.hmx", "data-odd.hmx"}) {
            InProcessSim sim = new InProcessSim();
            Component[] c = cpu(sim);
            ProgramLoader.Plan plan = load(sim, name);
            assertTrue(plan.stackBase.isEmpty(), name);
            assertNull(c[1].getAttributeSet().getValue(MemoryFactory.CONTENTS).initialSp(), name);
            sim.start();
            DataMemory.State st = (DataMemory.State) sim.data(c[1]);
            ExecutableImage img = plan.image;
            for (Map.Entry<Long, Integer> w : img.dataWords().entrySet()) {
                assertEquals((int) w.getValue(), st.readWord((int) (long) w.getKey()),
                        name + " " + Long.toHexString(w.getKey()));
                for (int b = 0; b < 4; b += 1) {
                    int a = (int) (long) w.getKey() + b;
                    assertEquals((w.getValue() >>> (8 * b)) & 0xff, st.readByte(a), name + " byte " + Integer.toHexString(a));
                }
            }
        }
        // zero-runs.hmx: 01, 0×6, 02 03, 0×7 → 16바이트
        InProcessSim sim = new InProcessSim();
        Component[] c = cpu(sim);
        load(sim, "zero-runs.hmx");
        sim.start();
        DataMemory.State st = (DataMemory.State) sim.data(c[1]);
        int[] bytes = {1, 0, 0, 0, 0, 0, 0, 2, 3, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < bytes.length; i += 1) {
            assertEquals(bytes[i], st.readByte(0x10010000 + i), "byte " + i);
        }
        assertEquals(4, st.dataWords(), "the zero gaps are words of .data too");
    }

    /** 다시 불러오면 옛 깊이 기준을 지운다(reg $sp 없는 이미지), 스택 영역이 $sp 아래 워드를 담지 않아도 지운다. */
    @Test
    void theDepthBaseFollowsTheLastImage() throws Exception {
        InProcessSim sim = new InProcessSim();
        Component[] c = cpu(sim);
        load(sim, "example.hmx");
        assertEquals(0x7ffff000L, (long) c[1].getAttributeSet().getValue(MemoryFactory.CONTENTS).initialSp());
        load(sim, "zero-runs.hmx");
        assertNull(c[1].getAttributeSet().getValue(MemoryFactory.CONTENTS).initialSp());

        InProcessSim small = new InProcessSim();
        Component im = small.b.add(small.mips, "Instruction Memory", 600, 100);
        Component dm = small.b.add(small.mips, "Data Memory", 600, 400, "stacktop", "0x7fff0ffc", "stacksize",
                "0x1000");
        small.b.commit();
        assertTrue(im != null);
        ProgramLoader.Plan plan = load(small, "example.hmx");
        assertTrue(plan.stackBase.isEmpty(), "0x7fffeffc is not in 7fff0000-7fff0fff");
        assertNull(dm.getAttributeSet().getValue(MemoryFactory.CONTENTS).initialSp());
    }

    /** 옛 Data Memory(스택 영역 없음)는 .data만 받고 깊이 기준을 갖지 않는다. 옛 Stack이 받는다(전과 같다). */
    @Test
    void anOldDataMemoryAndStackLoadAsBefore() throws Exception {
        InProcessSim sim = new InProcessSim();
        sim.b.add(sim.mips, "Instruction Memory", 600, 100);
        Component dm = sim.b.add(sim.mips, "Data Memory", 600, 400, StackRegionTest.OLD_DM);
        Component st = sim.b.add(sim.mips, "Stack", 600, 700);
        sim.b.commit();
        ProgramLoader.Plan plan = load(sim, "example.hmx");
        assertEquals(1, plan.stackBase.size());
        assertTrue(plan.stackBase.get(0).component == st);
        assertNull(dm.getAttributeSet().getValue(MemoryFactory.CONTENTS).initialSp());
        assertEquals(0x7ffff000L, (long) st.getAttributeSet().getValue(MemoryFactory.CONTENTS).initialSp());
        assertEquals(3, dm.getAttributeSet().getValue(MemoryFactory.CONTENTS).size());
    }
}
