/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.record.Recorder;
import kr.ac.hallym.hcs.app.record.RecorderTest;
import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.app.record.RecordingTestSupport;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * C-04 Run Until(C-10 조건별 테스트): 실제 원조 Simulator 스레드로 돌리고 기록으로 멈출 곳을 정한다. PC 값, 다음
 * 명령어 종류, 줄 값 바뀜, E·X 발생, halt·exit, 최대 사이클 수.
 */
class RunUntilTest {
    @TempDir
    Path tmp;

    Project proj;
    CycleModel model;

    void refMips(String program) throws Exception {
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        RecordingTestSupport.load(file, RecordingTestSupport.program(program));
        start(file);
    }

    void start(LogisimFile file) throws Exception {
        proj = new Project(file);
        Recorder rec = Recorder.of(proj);
        Recorder.requestReset(proj);
        RecorderTest.waitFor(() -> rec.current() != null && rec.current().last() == 0, "reset");
        Thread.sleep(300);
        Recording r = rec.current();
        model = new CycleModel(r.circuit(), r, CycleModel.findCpu(r.circuit()), ProgramSource.EMPTY);
    }

    RunUntilRunner.Outcome run(RunUntil until) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RunUntilRunner.Outcome> out = new AtomicReference<>();
        RunUntilRunner.start(proj, model, until, o -> {
            out.set(o);
            latch.countDown();
        });
        assertTrue(latch.await(60, TimeUnit.SECONDS), "run until finished");
        return out.get();
    }

    Component tunnel(String name) {
        for (Component c : model.recording().circuit().getNonWires()) {
            if (c.getFactory().getName().equals("Tunnel") && name.equals(Names.label(c))) {
                return c;
            }
        }
        return null;
    }

    @Test
    void untilPcAndInstructionAndExit() throws Exception {
        refMips("mips/factorial.s");
        // fact 라벨 주소(hcs-asm): 0x00400034
        RunUntilRunner.Outcome o = run(RunUntil.pc(0x00400034, 1000));
        assertEquals(RunUntil.Result.MET, o.result);
        assertEquals(0x00400034, model.pc(o.cycle).toIntValue());
        for (int c = 1; c < o.cycle; c++) {
            assertNotEquals(0x00400034, model.pc(c).toIntValue(), "the first time, at cycle " + c);
        }
        assertEquals(CycleModel.stepOf(o.cycle), model.recording().last(), "stopped right there");

        // 다음 jr: 거기서부터 처음 만나는 jr
        int from = o.cycle;
        o = run(RunUntil.instruction("jr", 1000));
        assertEquals(RunUntil.Result.MET, o.result);
        assertEquals("jr", MipsText.mnemonic(model.instruction(o.cycle).toIntValue()));
        for (int c = from + 1; c < o.cycle; c++) {
            assertNotEquals("jr", MipsText.mnemonic(model.instruction(c).toIntValue()));
        }

        // exit까지(Console Exit = 1)
        o = run(RunUntil.halt(2000));
        assertEquals(RunUntil.Result.MET, o.result);
        assertTrue(RunUntil.halted(model, o.cycle));
        assertFalse(RunUntil.halted(model, o.cycle - 1), "the first cycle after exit");
    }

    @Test
    void untilARowChangesAndTheLimit() throws Exception {
        refMips("record/busy-loop.s");
        Component rw = tunnel("regWrite");
        assertNotNull(rw);
        CycleModel.Signal row = CycleModel.signalFor(model.recording().circuit(),
                Collections.<Component>emptyList(), model.recording().circuit(), rw.getEnd(0).getLocation());
        RunUntilRunner.Outcome o = run(RunUntil.rowChanges(row, 1000));
        assertEquals(RunUntil.Result.MET, o.result);
        assertTrue(model.changed(row, o.cycle));
        // 없는 PC: 최대 사이클 수에서 멈춘다
        int from = o.cycle;
        o = run(RunUntil.pc(0x12345678, 25));
        assertEquals(RunUntil.Result.LIMIT, o.result);
        assertEquals(from + 25, o.cycle);
    }

    /** 카운터 비트 2가 1이 되면 켜지는 버퍼가 상수 0과 같은 넷을 1로 몬다: 그 사이클에 E가 생긴다. */
    @Test
    void untilAnErrorAppears() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit main = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, main);
        Component clk = b.add("Wiring", "Clock", 100, 100);
        b.tunnel(clk, 0, "clk");
        Component ctr = b.add("Memory", "Counter", 400, 100, "width", "3");
        b.tunnel(ctr, 0, "count");
        b.tunnel(ctr, 2, "clk");
        Component split = b.add("Wiring", "Splitter", 600, 100, "incoming", "3", "fanout", "3");
        b.tunnel(split, 0, "count");
        b.tunnel(split, 3, "b2");
        b.constant("one", 1, 1, 100, 300);
        Component buf = b.add("Gates", "Controlled Buffer", 400, 300);
        b.tunnel(buf, 0, "fight");
        b.tunnel(buf, 1, "one");
        b.tunnel(buf, 2, "b2");
        b.constant("fight", 1, 0, 100, 400); // 같은 넷을 상수 0이 늘 몬다
        b.commit();
        start(file);
        RunUntilRunner.Outcome o = run(RunUntil.errorOrX(100));
        assertEquals(RunUntil.Result.MET, o.result);
        assertEquals(4, o.cycle, "count reaches 4 (bit 2 = 1) after four cycles");
        assertFalse(model.recording().problemSteps(CycleModel.stepOf(o.cycle) - 1, CycleModel.stepOf(o.cycle))
                .isEmpty());
        assertTrue(model.recording().problemSteps(0, CycleModel.stepOf(o.cycle) - 2).isEmpty(), "nothing before");
    }
}
