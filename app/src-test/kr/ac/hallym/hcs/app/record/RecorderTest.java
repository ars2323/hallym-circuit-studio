/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * C-01 기록기: 실제 원조 Simulator 스레드의 틱을 스텝으로 적는다(틱마다 한 스텝, 한 사이클은 두 스텝). 리셋하면
 * 스텝 0부터, 회로를 고치면 지금 스텝부터 새로 적는다.
 */
public class RecorderTest {
    @TempDir
    Path tmp;

    public static void waitFor(BooleanSupplier ok, String what) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (!ok.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("timed out: " + what);
            }
            Thread.sleep(10);
        }
    }

    @Test
    void recordsTheSimulatorTicks() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component clk = b.add("Wiring", "Clock", 100, 200);
        b.tunnel(clk, 0, "clk");
        Component ctr = b.add("Memory", "Counter", 400, 200, "width", "8", "label", "PC");
        b.tunnel(ctr, 2, "clk");
        b.commit();
        Project proj = new Project(file);
        Simulator sim = proj.getSimulator();
        Recorder rec = Recorder.of(proj);
        assertSame(rec, Recorder.of(proj));
        Recorder.requestReset(proj);
        Location q = ctr.getEnd(0).getLocation();
        // 원조 리셋은 값을 지운 뒤 한 번, 다시 전파한 뒤 한 번 전파 완료를 알린다: 둘째가 스텝 0을 덮어쓴다
        waitFor(() -> rec.current() != null && rec.current().last() == 0
                && rec.current().value(q, 0).isFullyDefined(), "the reset starts at step 0");
        Recording r = rec.current();
        assertEquals(0, r.value(q, 0).toIntValue());

        for (int i = 0; i < 10; i++) {
            sim.tick();
            final int want = i + 1;
            waitFor(() -> r.last() == want, "tick " + want);
        }
        // 한 사이클 = 틱 두 번: 5사이클 뒤 카운터 5
        assertEquals(5, r.value(q, 10).toIntValue());
        assertEquals(2, r.value(q, 4).toIntValue());
        assertNotNull(r.reconstruct(7));

        // 리셋: 스텝 0부터 다시
        Recorder.requestReset(proj);
        waitFor(() -> rec.current().last() == 0
                && rec.current().value(q, 0).isFullyDefined() && rec.current().value(q, 0).toIntValue() == 0,
                "reset again");
        assertEquals(0, rec.current().value(q, 0).toIntValue());
        for (int i = 0; i < 4; i++) {
            sim.tick();
            final int want = i + 1;
            waitFor(() -> rec.current().last() == want, "tick after reset " + want);
        }

        // 회로 편집: 지금 스텝(4)부터 새로 적는다(사이클 번호는 이어진다)
        CircuitBuilder eb = new CircuitBuilder(file, file.getMainCircuit());
        eb.add("Gates", "NOT Gate", 700, 500);
        eb.commit();
        sim.requestPropagate();
        waitFor(() -> rec.current().first() == 4 && rec.current().last() == 4, "restart at step 4 after the edit");
        sim.tick();
        waitFor(() -> rec.current().last() == 5, "then continue at 5");
        assertTrue(rec.current().checkpointCount() >= 1);
    }

    /** 카운터와 클럭 하나: 틱마다 스텝이 하나 는다. */
    private Object[] counter() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component clk = b.add("Wiring", "Clock", 100, 200);
        b.tunnel(clk, 0, "clk");
        Component ctr = b.add("Memory", "Counter", 400, 200, "width", "8", "label", "PC");
        b.tunnel(ctr, 2, "clk");
        b.commit();
        return new Object[] {file, new Project(file)};
    }

    /** 전파를 요청하고 원조 시뮬레이터가 그 전파를 마쳤다고 알릴 때까지 기다린다. */
    static void propagateAndWait(Simulator sim) throws Exception {
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        com.cburch.logisim.circuit.SimulatorListener l = new com.cburch.logisim.circuit.SimulatorListener() {
            public void propagationCompleted(com.cburch.logisim.circuit.SimulatorEvent e) {
                done.incrementAndGet();
            }

            public void tickCompleted(com.cburch.logisim.circuit.SimulatorEvent e) {
            }

            public void simulatorStateChanged(com.cburch.logisim.circuit.SimulatorEvent e) {
            }
        };
        sim.addSimulatorListener(l);
        try {
            sim.requestPropagate();
            waitFor(() -> done.get() > 0, "the requested propagation");
        } finally {
            sim.removeSimulatorListener(l);
        }
    }

    /**
     * 저장(고침 표시가 꺼짐)이나 회로를 고치지 않는 첫 동작(Mark as Register File·Mark as PC: 고침 표시가 켜짐)은 넷을
     * 바꾸지 않는다: 지난 사이클을 버리고 새로 적지 않는다(원조 라이브러리 사건 DIRTY_STATE, RecordTest가 CI에서 가끔
     * 실패).
     */
    @Test
    void savingOrMarkingTheFileDirtyKeepsThePastSteps() throws Exception {
        Object[] fp = counter();
        LogisimFile file = (LogisimFile) fp[0];
        Project proj = (Project) fp[1];
        Simulator sim = proj.getSimulator();
        Recorder rec = Recorder.of(proj);
        Recorder.requestReset(proj);
        waitFor(() -> rec.current() != null && rec.current().last() == 0, "step 0");
        for (int i = 0; i < 4; i++) {
            sim.tick();
            final int want = i + 1;
            waitFor(() -> rec.current().last() == want, "tick " + want);
        }
        Recording r = rec.current();
        int generation = r.generation();
        file.setDirty(true); // 첫 동작
        propagateAndWait(sim);
        file.setDirty(false); // 저장
        propagateAndWait(sim);
        sim.tick();
        waitFor(() -> rec.current().last() == 5, "tick 5");
        assertSame(r, rec.current());
        assertEquals(generation, r.generation(), "not started again");
        assertEquals(0, r.first(), "the past steps are kept");
    }

    /**
     * restartAtNextPropagation(v2 엔진이 파일을 연 요청에서 진단이 붙은 뒤에 부른다, D-143): 원조 리셋 없이 다음 전파에서
     * 스텝 0을 새로 적고, 뒤에 붙은 청취자도 그 스텝 0을 받는다.
     */
    @Test
    void aLaterListenerGetsStepZeroWhenTheRecordingRestarts() throws Exception {
        Object[] fp = counter();
        Project proj = (Project) fp[1];
        Simulator sim = proj.getSimulator();
        Recorder rec = Recorder.of(proj);
        propagateAndWait(sim);
        waitFor(() -> rec.current() != null && rec.current().last() == 0, "step 0 before the listener");
        int generation = rec.current().generation();
        java.util.List<int[]> heard = new java.util.concurrent.CopyOnWriteArrayList<>();
        rec.addListener(x -> heard.add(new int[] {x.first(), x.last(), x.generation()}));
        propagateAndWait(sim);
        assertTrue(heard.isEmpty(), "an unchanged propagation is not a new step");
        rec.restartAtNextPropagation();
        assertTrue(rec.resetPending());
        propagateAndWait(sim);
        waitFor(() -> !heard.isEmpty(), "the listener hears step 0");
        assertEquals(0, heard.get(0)[0]);
        assertEquals(0, heard.get(0)[1]);
        assertEquals(generation + 1, heard.get(0)[2], "a new recording from step 0");
        assertTrue(!rec.resetPending());
    }
}
