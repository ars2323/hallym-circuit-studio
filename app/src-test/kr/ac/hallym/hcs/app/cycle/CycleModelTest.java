/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/**
 * C-02 사이클 표 모델: ref-mips가 tests/record/busy-loop.s를 도는 기록에서 열마다 PC와 명령어 글(학생이 쓴 .s 줄,
 * 없으면 디스어셈블), 고른 신호의 값·바뀜을 읽는다. Instruction Memory는 따로 지정하지 않아도 찾는다.
 */
class CycleModelTest {
    @TempDir
    Path tmp;

    @Test
    void columnsShowPcAndTheSourceLine() throws Exception {
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        Path s = RecordingTestSupport.program("record/busy-loop.s");
        RecordingTestSupport.load(file, s);
        Circuit main = file.getMainCircuit();
        CycleModel.Cpu cpu = CycleModel.findCpu(main);
        assertNotNull(cpu, "Instruction Memory found without marking it");
        assertTrue(cpu.path.isEmpty());

        // .s를 .circ 옆에 두고 Instruction Memory의 source 속성에 상대 경로로(메뉴의 .s 불러오기와 같다)
        File circ = file.getLoader().getMainFile();
        File copy = new File(circ.getParentFile(), "busy-loop.s");
        Files.copy(s, copy.toPath());
        @SuppressWarnings("unchecked")
        Attribute<Object> src = (Attribute<Object>) cpu.imem.getAttributeSet().getAttribute("source");
        CircuitMutation m = new CircuitMutation(main);
        m.set(cpu.imem, src, "busy-loop.s");
        m.execute();
        assertEquals(copy.getCanonicalFile(), CycleModel.sourceFile(cpu, circ).getCanonicalFile());

        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        CircuitState root = new CircuitState(proj, main);
        root.getPropagator().propagate();
        Recording r = new Recording(main);
        r.restart(root, 0);
        for (int step = 1; step <= 60; step++) {
            Propagator p = root.getPropagator();
            p.tick();
            p.propagate();
            r.capture(root, step, false);
        }
        CycleModel withSource = new CycleModel(main, r, cpu, ProgramSource.of(CycleModel.sourceFile(cpu, circ)));
        assertEquals(0, withSource.firstCycle());
        assertEquals(30, withSource.lastCycle());
        assertEquals("0x00400000", withSource.pcText(0));
        assertEquals("main: li $t0, 0", withSource.instructionText(0), "the line as written, label included");
        assertEquals("0x00400004", withSource.pcText(1));
        assertEquals("outer: li $t1, 0", withSource.instructionText(1));
        // la arr(0x10010000)는 아래 절반이 0이라 SPIM이 lui 하나로 바꾼다: 그 워드도 원래 줄로 보인다
        assertEquals("la $s0, arr", withSource.instructionText(2));
        assertEquals("inner: sw $t1, 0($s0)", withSource.instructionText(3));
        // 분기 뒤: bne가 inner로 돌아간다(PC가 줄어든다)
        int back = -1;
        for (int c = 1; c <= 30; c++) {
            if (withSource.pc(c).toIntValue() < withSource.pc(c - 1).toIntValue()) {
                back = c;
                break;
            }
        }
        assertTrue(back > 0, "the loop branches back");
        assertEquals("inner: sw $t1, 0($s0)", withSource.instructionText(back));

        // .s가 없으면 디스어셈블
        CycleModel bare = new CycleModel(main, r, cpu, null);
        assertEquals("ori $t0, $zero, 0", bare.instructionText(0));
        assertEquals("lui $s0, 0x1001", bare.instructionText(2));

        // 신호 줄: 터널 pc의 넷. 이름은 터널 이름, 값은 열마다 PC와 같고 바뀐 열을 안다
        Component pcTunnel = null;
        for (Component c : main.getNonWires()) {
            if (c.getFactory().getName().equals("Tunnel") && "pc".equals(Names.label(c))) {
                pcTunnel = c;
            }
        }
        CycleModel.Signal sig = CycleModel.signalFor(main, Collections.<Component>emptyList(), main,
                pcTunnel.getEnd(0).getLocation());
        assertEquals("pc", sig.name);
        assertEquals(32, sig.width);
        assertTrue(withSource.add(sig));
        assertFalse(withSource.add(sig), "no duplicate rows");
        // 같은 이름의 다른 터널(같은 넷)도 같은 줄이다
        for (Component c : main.getNonWires()) {
            if (c != pcTunnel && c.getFactory().getName().equals("Tunnel") && "pc".equals(Names.label(c))) {
                CycleModel.Signal other = CycleModel.signalFor(main, Collections.<Component>emptyList(), main,
                        c.getEnd(0).getLocation());
                assertEquals(sig, other, "same net, same row");
                assertFalse(withSource.add(other));
            }
        }
        for (int c = 0; c <= 30; c++) {
            assertEquals(withSource.pc(c), withSource.value(sig, CycleModel.stepOf(c)));
        }
        assertTrue(withSource.changed(sig, 1));
        // 한 열이 한 사이클(D-074): 앞 절반(상승 에지 뒤)은 clk 1, 뒤 절반(다음 상승 에지 앞)은 clk 0이고, 두 절반의
        // PC가 그 열 머리의 PC와 같다
        Component clkTunnel = null;
        for (Component c : main.getNonWires()) {
            if (c.getFactory().getName().equals("Tunnel") && "clk".equals(Names.label(c))) {
                clkTunnel = c;
            }
        }
        CycleModel.Signal clk = CycleModel.signalFor(main, Collections.<Component>emptyList(), main,
                clkTunnel.getEnd(0).getLocation());
        for (int c = 1; c <= 30; c++) {
            int[] half = withSource.halfSteps(c);
            assertEquals(CycleModel.stepOf(c) - 1, half[0]);
            assertEquals(CycleModel.stepOf(c), half[1]);
            assertEquals(com.cburch.logisim.data.Value.TRUE, withSource.value(clk, half[0]), "clk high after the edge");
            assertEquals(com.cburch.logisim.data.Value.FALSE, withSource.value(clk, half[1]), "clk low before the next");
            assertEquals(withSource.pc(c), withSource.value(sig, half[0]), "same PC in both halves of column " + c);
            assertEquals(withSource.pc(c), withSource.value(sig, half[1]));
        }
        int[] first = withSource.halfSteps(0);
        assertEquals(0, first[0], "the first column starts at the first recorded step");
        assertFalse(withSource.changed(sig, 0), "the first column has nothing before it");
    }
}
