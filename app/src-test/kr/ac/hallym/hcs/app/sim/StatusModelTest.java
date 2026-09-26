/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #77: 한 사이클 = 원조 틱 두 번(카운터가 딱 1 오른다), 상태 표시줄 값. */
class StatusModelTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));

    @TempDir
    Path tmp;

    @Test
    void oneCycleIsTwoTicks() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component clk = b.add("Wiring", "Clock", 100, 200);
        b.tunnel(clk, 0, "clk");
        Component ctr = b.add("Memory", "Counter", 400, 200, "width", "8", "label", "PC");
        b.tunnel(ctr, 2, "clk");
        b.commit();
        Project proj = new Project(file);
        CircuitState state = proj.getCircuitState();
        state.getPropagator().propagate();
        assertEquals("0x00000000", StatusModel.pc(state));
        for (int cycle = 1; cycle <= 3; cycle++) {
            StatusModel.oneCycle(state.getPropagator());
            assertEquals(String.format("0x%08x", cycle), StatusModel.pc(state), "cycle " + cycle);
        }
        assertEquals(3, StatusModel.cycles(7));
    }

    /** 도구 모음의 N 사이클 버튼이 쓰는 Run: n 사이클 = 틱 2n번, 그 뒤로는 부르지 않는다. */
    @Test
    void runStepsTwoTicksPerCycle() {
        for (int n : new int[] {1, 5}) {
            StatusModel.Run run = new StatusModel.Run(n);
            int[] ticks = {0};
            while (run.step(() -> ticks[0]++)) {
                // 타이머 한 번에 한 틱
            }
            assertEquals(2 * n, ticks[0]);
            assertEquals(n, StatusModel.cycles(ticks[0]));
            assertEquals(false, run.step(() -> ticks[0]++));
            assertEquals(2 * n, ticks[0]);
        }
    }

    @Test
    void programAndMissingValues() throws Exception {
        Loader loader = new Loader(null);
        LogisimFile file = CircuitBuilder.newFile(loader, tmp.toFile());
        Path jar = tmp.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        Library mips = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        file.addLibrary(mips);
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        assertNull(StatusModel.program(file.getMainCircuit()));
        b.add(mips, "Instruction Memory", 400, 200, "source", "/home/s/lab/sum.s");
        b.commit();
        assertEquals("sum.s", StatusModel.program(file.getMainCircuit()));
        assertNull(StatusModel.pc(null));
    }

    /** V-08 (D-103): PC는 표시 → 라벨 PC(대소문자 무관, 터널보다 부품 먼저) → Instruction Memory Addr 순으로 찾는다. */
    @Test
    void pcIsFoundByMarkLabelOrInstructionMemory() throws Exception {
        Loader loader = new Loader(null);
        LogisimFile file = CircuitBuilder.newFile(loader, tmp.toFile());
        Path jar = tmp.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        Library mips = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        file.addLibrary(mips);
        com.cburch.logisim.circuit.Circuit main = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, main);
        com.cburch.logisim.comp.Component imem = b.add(mips, "Instruction Memory", 400, 200);
        com.cburch.logisim.comp.Component k = b.constant("addr", 32, 0x400008, 100, 100);
        b.tunnel(imem, 0, "addr");
        b.commit();
        assertEquals(imem, StatusModel.pcComponent(file, main), "no PC label: the Instruction Memory's Addr");
        com.cburch.logisim.circuit.CircuitState state = new com.cburch.logisim.circuit.CircuitState(
                new com.cburch.logisim.proj.Project(file), main);
        state.getPropagator().propagate();
        assertEquals("0x00400008", StatusModel.pc(state));

        CircuitBuilder b2 = new CircuitBuilder(file, main);
        com.cburch.logisim.comp.Component t = b2.add("Wiring", "Tunnel", 100, 400, "label", "pc", "width", "32");
        b2.commit();
        assertEquals(t, StatusModel.pcComponent(file, main), "a tunnel named pc (any case) beats the memory");
        CircuitBuilder b3 = new CircuitBuilder(file, main);
        com.cburch.logisim.comp.Component reg = b3.add("Memory", "Register", 600, 400, "width", "32", "label", "Pc");
        b3.commit();
        assertEquals(reg, StatusModel.pcComponent(file, main), "a part labelled PC beats a tunnel");

        // Mark as PC: 라벨과 상관없이 표시한 레지스터가 먼저. 해제하면 되돌아간다
        CircuitBuilder b4 = new CircuitBuilder(file, main);
        com.cburch.logisim.comp.Component other = b4.add("Memory", "Register", 600, 600, "width", "32", "label", "next");
        b4.commit();
        com.cburch.logisim.proj.Project proj = new com.cburch.logisim.proj.Project(file);
        proj.doAction(PcMark.action(file, main, other, true));
        assertEquals(other, PcMark.marked(file, main));
        assertEquals(other, StatusModel.pcComponent(file, main), "the marked register wins");
        assertTrue(PcMark.markable(other) && !PcMark.markable(t));
        proj.undoAction();
        assertNull(PcMark.marked(file, main));
        assertEquals(reg, StatusModel.pcComponent(file, main));
        proj.doAction(PcMark.action(file, main, other, true));
        // 저장 형식: hcs:ext 안 kind="pc" at="(600,600)" 하나, 원조 요소는 그대로
        java.io.File out = tmp.resolve("pc.circ").toFile();
        CircuitBuilder.save(file, out);
        kr.ac.hallym.hcs.app.ext.CircExtensions.afterSave(file, out);
        String xml = new String(Files.readAllBytes(out.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.contains("<hcs:pc at=\"(600,600)\"/>"), "one pc item in the extension block: " + xml);
        // 부품이 사라지면 표시도 지워진다
        com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(main);
        m.remove(other);
        proj.doAction(m.toAction(com.cburch.logisim.util.StringUtil.constantGetter("delete")));
        PcMark.PRUNER.prune(file, kr.ac.hallym.hcs.app.ext.CircExtensions.of(file));
        assertNull(PcMark.marked(file, main));
    }

    /** V-08: 우리 참조 회로(ref-mips)에서 반드시 PC가 나온다(pc 터널). */
    @Test
    void refMipsShowsItsPc() throws Exception {
        LogisimFile file = kr.ac.hallym.hcs.app.record.RecordingTestSupport.openRefMips(tmp);
        com.cburch.logisim.circuit.Circuit main = file.getMainCircuit();
        com.cburch.logisim.comp.Component pc = StatusModel.pcComponent(file, main);
        assertNotNull(pc, "ref-mips has a pc");
        com.cburch.logisim.proj.Project proj = new com.cburch.logisim.proj.Project(file);
        proj.getSimulator().setIsRunning(false);
        com.cburch.logisim.circuit.CircuitState state = new com.cburch.logisim.circuit.CircuitState(proj, main);
        state.getPropagator().propagate();
        assertEquals("0x00400000", StatusModel.pc(state), "reset PC of the reference datapath");
    }
}
