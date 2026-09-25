/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

/**
 * C-01 재실행 격리(compat 검토 반영): 지난 스텝을 복제본으로 다시 돌려도 실제 시뮬레이션은 그대로다. 재실행이 MIPS
 * 메모리 등록을 덮어쓰면 실제 Console이 지난 메모리("A")를 읽고, 재실행 안의 exit가 실제 클럭을 멈춘다.
 * tests/record/overwrite-print.s는 buf에 "A"를 쓰고 돈 뒤 "B"로 바꿔 출력한다. 재실행한 Console 글도 그 스텝의 실제
 * 글과 같다(기록 재생 = 실제 재실행).
 */
class ReplayIsolationTest {
    @TempDir
    Path tmp;

    Component console;

    static Object data(CircuitState s, Component c) {
        return s.getData(c);
    }

    static String text(Object consoleState) throws Exception {
        Method m = consoleState.getClass().getDeclaredMethod("text");
        m.setAccessible(true);
        return (String) m.invoke(consoleState);
    }

    static boolean exited(Object consoleState) throws Exception {
        Field f = consoleState.getClass().getDeclaredField("exited");
        f.setAccessible(true);
        return f.getBoolean(consoleState);
    }

    /** lib-mips MemoryRegistry(프로젝트의 실제 등록)에서 addr 바이트. */
    static int liveByte(Project proj, Object anyMipsState, int addr) throws Exception {
        Class<?> reg = anyMipsState.getClass().getClassLoader().loadClass("kr.ac.hallym.hcs.mips.MemoryRegistry");
        Method find = reg.getDeclaredMethod("find", Project.class, int.class);
        find.setAccessible(true);
        Object view = find.invoke(null, proj, addr);
        Method read = view.getClass().getDeclaredMethod("readByte", int.class);
        read.setAccessible(true);
        return (Integer) read.invoke(view, addr);
    }

    static void step(CircuitState s) {
        Propagator p = s.getPropagator();
        p.tick();
        p.propagate();
    }

    @Test
    void replayLeavesTheLiveSimulationAlone() throws Exception {
        LogisimFile file = MipsPrograms.openRefMips(tmp);
        MipsPrograms.load(file, MipsPrograms.program("record/overwrite-print.s"));
        for (Component c : file.getMainCircuit().getNonWires()) {
            if (c.getFactory().getName().equals("Console")) {
                console = c;
            }
        }
        Project proj = new Project(file);
        Simulator sim = proj.getSimulator();
        sim.setIsRunning(false);
        sim.setTickFrequency(0.001); // 틱을 요청하지 않을 만큼 느리게: 클럭 멈춤만 본다

        // 기준: 재실행 없이 돌려 글이 처음 나오는 스텝과 끝을 찾는다
        CircuitState plain = new CircuitState(proj, file.getMainCircuit());
        plain.getPropagator().propagate();
        List<String> texts = new ArrayList<>();
        texts.add(text(data(plain, console)));
        int printAt = -1;
        int exitAt = -1;
        for (int s = 1; s <= 400 && exitAt < 0; s++) {
            step(plain);
            texts.add(text(data(plain, console)));
            if (printAt < 0 && !texts.get(s).isEmpty()) {
                printAt = s;
            }
            if (exited(data(plain, console))) {
                exitAt = s;
            }
        }
        assertTrue(printAt > 20 && exitAt > printAt, "print at " + printAt + ", exit at " + exitAt);
        assertEquals("B", texts.get(printAt));

        // 실제 실행: 출력 바로 앞까지 기록하며 돌고, buf가 "A"이던 스텝들을 재실행한 뒤 출력 스텝을 진행한다
        CircuitState live = new CircuitState(proj, file.getMainCircuit());
        live.getPropagator().propagate();
        Recording r = new Recording(file.getMainCircuit());
        r.restart(live, 0);
        for (int s = 1; s < printAt; s++) {
            step(live);
            r.capture(live, s, false);
        }
        // 마지막 재실행은 buf가 아직 "A"인 스텝: 옛 방식이면 실제 등록이 그 복제본으로 바뀐다
        for (int k : new int[] {printAt - 2, printAt / 2, 20, 12}) {
            CircuitState again = r.reconstruct(k);
            assertNull(RecordingTest.diff(r, again, k));
            assertEquals(texts.get(k), text(data(again, console)), "replayed Console text at " + k);
        }
        assertEquals('B', liveByte(proj, data(live, console), 0x10010000), "the live registry still has buf = B");
        step(live);
        r.capture(live, printAt, false);
        assertEquals("B", text(data(live, console)), "the live Console read the live memory");

        // 끝까지 가서 exit: 실제 exit는 클럭을 멈춘다
        sim.setIsTicking(true);
        for (int s = printAt + 1; s <= exitAt; s++) {
            step(live);
            r.capture(live, s, false);
        }
        assertTrue(exited(data(live, console)));
        assertFalse(sim.isTicking(), "the live exit stops the clock");
        // exit를 지나는 재실행은 실제 클럭을 멈추지 않는다
        sim.setIsTicking(true);
        CircuitState again = r.reconstruct(exitAt);
        assertTrue(exited(data(again, console)), "the replay also reached exit");
        assertEquals(texts.get(exitAt), text(data(again, console)));
        assertTrue(sim.isTicking(), "a replay does not stop the real clock");
        sim.setIsTicking(false);
    }
}
