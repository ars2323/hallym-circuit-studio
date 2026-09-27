/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.sim.CyclePacer;
import kr.ac.hallym.hcs.app.sim.SimControls;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 부하에서 틱이 빠지지 않는지(D-123). 원조 엔진은 처리 못 한 틱을 16개까지만 쌓고 나머지를 버린다. 틱 완료 알림에서
 * 잠깐 쉬는 청취자로 엔진을 느리게 하고(느린 회로와 같다) CPU를 바쁘게 하는 스레드를 함께 돌린다.
 * <ul>
 * <li>N Cycles: 100·1000 사이클을 요청하면 정확히 그만큼 돈다(v1.0.3까지는 창이 없으면 한꺼번에, 창이 있으면 5ms마다
 * 요청해 엔진이 밀리면 빠졌다).</li>
 * <li>연속 실행(Ticks Enabled, 높은 Tick Frequency): 엔진이 따라가지 못해 버린 틱은 돌지 않은 틱이다. 돈 틱은 모두
 * 기록되고 사이클 번호가 이어지며 Console 글도 빠지지 않는다.</li>
 * </ul>
 */
class TickLoadTest {
    @TempDir
    Path tmp;

    Project proj;
    Component counter;

    /** 클럭과 16비트 카운터: 사이클 n 뒤 카운터 값 n. */
    Recorder counterCircuit() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component clk = b.add("Wiring", "Clock", 100, 200);
        b.tunnel(clk, 0, "clk");
        counter = b.add("Memory", "Counter", 400, 200, "width", "16", "max", "0xffff", "label", "PC");
        b.tunnel(counter, 2, "clk");
        b.commit();
        proj = new Project(file);
        Recorder rec = Recorder.of(proj);
        Recorder.requestReset(proj);
        Location q = counter.getEnd(0).getLocation();
        RecorderTest.waitFor(() -> rec.current() != null && rec.current().last() == 0
                && rec.current().value(q, 0).isFullyDefined(), "reset");
        return rec;
    }

    int counterValue() {
        return proj.getSimulator().getCircuitState().getValue(counter.getEnd(0).getLocation()).toIntValue();
    }

    void nCycles(int n, int slowMs) throws Exception {
        Recorder rec = counterCircuit();
        Simulator sim = proj.getSimulator();
        sim.addSimulatorListener(TickLoadTestSupport.slow(slowMs));
        AtomicReference<CyclePacer> pacer = new AtomicReference<>();
        try (TickLoadTestSupport.Burner b = new TickLoadTestSupport.Burner(4)) {
            SwingUtilities.invokeAndWait(() -> pacer.set(SimControls.runCycles(proj, n)));
            long end = System.currentTimeMillis() + 120_000;
            while (!pacer.get().isFinished() && System.currentTimeMillis() < end) {
                Thread.sleep(20);
            }
        }
        assertTrue(pacer.get().isFinished(), "N Cycles finished");
        RecorderTest.waitFor(() -> rec.current().last() == 2 * n, "recorded " + 2 * n + " steps, got "
                + rec.current().last());
        assertEquals(2 * n, pacer.get().requested());
        assertEquals(n, counterValue(), "counter after " + n + " cycles");
        assertEquals(n, rec.current().value(counter.getEnd(0).getLocation(), 2 * n).toIntValue());
    }

    /**
     * 원인 확인: 틱 200개를 한꺼번에 요청하면(v1.0.3까지 창 없는 Next Cycle이 이렇게 했다) 엔진이 느릴 때 16개를 넘는
     * 요청은 버려져 200틱이 돌지 않는다. 엔진 동작이 바뀌면(버리지 않으면) 이 테스트가 알려 준다.
     */
    @Test
    void theEngineDropsTicksRequestedFasterThanItRuns() throws Exception {
        Recorder rec = counterCircuit();
        Simulator sim = proj.getSimulator();
        java.util.concurrent.atomic.AtomicInteger ran = new java.util.concurrent.atomic.AtomicInteger();
        sim.addSimulatorListener(TickLoadTestSupport.counting(ran));
        sim.addSimulatorListener(TickLoadTestSupport.slow(5));
        for (int i = 0; i < 200; i++) {
            sim.tick();
        }
        Thread.sleep(1500);
        RecorderTest.waitFor(() -> rec.current().last() == stable(rec.current()), "the queue drained");
        assertTrue(ran.get() < 200, "the engine ran " + ran.get() + " of 200 ticks");
        assertEquals(ran.get(), rec.current().last(), "what ran is what was recorded");
    }

    @Test
    void hundredCyclesAreExactlyHundredUnderLoad() throws Exception {
        nCycles(100, 8);
    }

    @Test
    void thousandCyclesAreExactlyThousandUnderLoad() throws Exception {
        nCycles(1000, 6);
    }

    /** 기록된 스텝이 0부터 빈틈없이 이어지고 스텝 2k의 카운터가 k: 돈 틱은 모두 한 줄씩 기록됐다(두 사이클이 한 줄로 합쳐지지 않았다). */
    static void assertConsecutive(Recording r, Location q) {
        int last = r.last();
        for (int s = r.first(); s <= last; s++) {
            int v = r.value(q, s).toIntValue();
            if (s % 2 == 0) {
                assertEquals(s / 2, v, "counter at step " + s);
            } else {
                assertTrue(v == s / 2 || v == s / 2 + 1, "counter at odd step " + s + " is " + v);
            }
        }
    }

    @Test
    void runningTheClockFastRecordsEveryTickTheEngineRan() throws Exception {
        Recorder rec = counterCircuit();
        Simulator sim = proj.getSimulator();
        java.util.concurrent.atomic.AtomicInteger ran = new java.util.concurrent.atomic.AtomicInteger();
        SimulatorListener count = TickLoadTestSupport.counting(ran);
        sim.addSimulatorListener(count);
        sim.addSimulatorListener(TickLoadTestSupport.slow(2));
        sim.setTickFrequency(4096);
        try (TickLoadTestSupport.Burner b = new TickLoadTestSupport.Burner(4)) {
            sim.setIsTicking(true);
            Thread.sleep(1500);
            sim.setIsTicking(false);
        }
        Thread.sleep(300); // 쌓인 틱(16개까지)이 다 돈다
        Recording r = rec.current();
        RecorderTest.waitFor(() -> r.last() == stable(r), "the queue drained");
        assertTrue(r.last() > 100, "ran " + r.last() + " ticks");
        assertEquals(r.first(), 0);
        assertConsecutive(r, counter.getEnd(0).getLocation());
        // 엔진이 돈 틱 수 = 기록한 스텝 수, 지금 카운터 = 기록 끝 스텝의 값: 돈 틱이 모두 기록에 있다
        assertEquals(ran.get(), r.last(), "every tick the engine ran is a recorded step");
        assertEquals(r.value(counter.getEnd(0).getLocation(), r.last()).toIntValue(), counterValue());
        // 4096 Hz를 1.5초 요청했으니 엔진이 버린 틱이 있다(이 테스트가 실제로 밀린 상태를 만든다)
        assertTrue(ran.get() < 4096, "the engine fell behind: ran " + ran.get());
    }

    static int stable(Recording r) {
        int a = r.last();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return a;
    }

    /** ref-mips + factorial.s를 빠른 클럭으로 끝까지(exit) 돌린 Console 글이 한 틱씩 돌린 글과 같다. */
    @Test
    @Tag("timing") // 제한 시간 안에 끝까지 돌아야 한다: 상수 identity hash 실행에서는 뺀다(D-129)
    void consoleOutputIsCompleteWhenTheClockRunsFast() throws Exception {
        LogisimFile file = RecordingTestSupport.openRefMips(tmp);
        RecordingTestSupport.load(file, RecordingTestSupport.program("mips/factorial.s"));
        Component console = null;
        for (Component c : file.getMainCircuit().getNonWires()) {
            if (c.getFactory().getName().equals("Console")) {
                console = c;
            }
        }
        proj = new Project(file);
        Simulator sim = proj.getSimulator();
        // 기준: 한 틱씩 직접 돌린 글
        CircuitState plain = new CircuitState(proj, file.getMainCircuit());
        plain.getPropagator().propagate();
        String want = null;
        for (int s = 1; s <= 4000; s++) {
            ReplayIsolationTest.step(plain);
            if (ReplayIsolationTest.exited(ReplayIsolationTest.data(plain, console))) {
                want = ReplayIsolationTest.text(ReplayIsolationTest.data(plain, console));
                break;
            }
        }
        assertTrue(want != null && !want.isEmpty(), "the program prints and exits");

        Recorder rec = Recorder.of(proj);
        Recorder.requestReset(proj);
        RecorderTest.waitFor(() -> rec.current() != null && rec.current().last() == 0, "reset");
        Thread.sleep(300);
        java.util.concurrent.atomic.AtomicInteger ran = new java.util.concurrent.atomic.AtomicInteger();
        SimulatorListener count = TickLoadTestSupport.counting(ran);
        sim.addSimulatorListener(count);
        sim.addSimulatorListener(TickLoadTestSupport.slow(1));
        sim.setTickFrequency(4096);
        final Component con = console;
        try (TickLoadTestSupport.Burner b = new TickLoadTestSupport.Burner(4)) {
            sim.setIsTicking(true); // Console은 exit에서 클럭을 멈춘다
            RecorderTest.waitFor(() -> {
                try {
                    return ReplayIsolationTest.exited(ReplayIsolationTest.data(sim.getCircuitState(), con));
                } catch (Exception e) {
                    return false;
                }
            }, "the program exits");
        }
        sim.setIsTicking(false);
        assertEquals(want, ReplayIsolationTest.text(ReplayIsolationTest.data(sim.getCircuitState(), console)));
        Thread.sleep(300);
        Recording r = rec.current();
        assertEquals(0, r.first());
        assertEquals(ran.get(), r.last(), "every tick the engine ran is a recorded step");
    }
}
