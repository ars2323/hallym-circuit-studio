/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 엔진 스레드의 편집 동안 원조 시뮬레이터 스레드를 전파 밖에 세워 둔다(D-143). */
class SimGateTest {
    @TempDir
    Path tmp;

    Simulator sim;
    final AtomicInteger propagations = new AtomicInteger();

    @BeforeEach
    void ticking() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component clk = b.add("Wiring", "Clock", 100, 200);
        b.tunnel(clk, 0, "clk");
        Component ctr = b.add("Memory", "Counter", 400, 200, "width", "8");
        b.tunnel(ctr, 2, "clk");
        b.commit();
        sim = new Project(file).getSimulator();
        sim.addSimulatorListener(new SimulatorListener() {
            public void propagationCompleted(SimulatorEvent e) {
                propagations.incrementAndGet();
            }

            public void tickCompleted(SimulatorEvent e) {
            }

            public void simulatorStateChanged(SimulatorEvent e) {
            }
        });
        sim.setTickFrequency(4096);
        sim.setIsTicking(true);
        waitFor(() -> propagations.get() > 20, "the clock ticking");
    }

    @AfterEach
    void stop() {
        sim.setIsTicking(false);
        sim.shutDown();
    }

    static void waitFor(BooleanSupplier ok, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (!ok.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < end, "timed out: " + what);
            Thread.sleep(5);
        }
    }

    /** 원조 시뮬레이터 스레드(PropagationManager)의 스택들. */
    static List<String> propagationThreads() {
        List<String> stacks = new ArrayList<>();
        for (Map.Entry<Thread, StackTraceElement[]> t : Thread.getAllStackTraces().entrySet()) {
            StringBuilder b = new StringBuilder();
            for (StackTraceElement f : t.getValue()) {
                b.append(f).append('\n');
            }
            if (b.indexOf("Simulator$PropagationManager.run") >= 0) {
                stacks.add(b.toString());
            }
        }
        return stacks;
    }

    @Test
    void theSimulatorThreadWaitsOutsidePropagationWhileTheBodyRuns() throws Exception {
        SimGate gate = new SimGate(sim);
        try {
            for (int i = 0; i < 20; i++) {
                int during = gate.hold(() -> {
                    int before = propagations.get();
                    // 4096 Hz 클럭이면 이 동안 전파가 수십 번 돈다
                    Thread.sleep(20);
                    int parked = 0;
                    for (String s : propagationThreads()) {
                        if (s.contains("SimGate.park")) {
                            parked++;
                            assertTrue(!s.contains("Propagator.propagate"), "parked inside a propagation:\n" + s);
                        }
                    }
                    assertEquals(1, parked, "the simulator thread is parked in the gate");
                    return propagations.get() - before;
                });
                assertEquals(0, during, "propagations while held");
            }
            int after = propagations.get();
            waitFor(() -> propagations.get() > after + 20, "propagations after holding");
        } finally {
            gate.close();
        }
    }

    @Test
    void aNestedHoldRunsAtOnceInsideTheOuterOne() throws Exception {
        SimGate gate = new SimGate(sim);
        try {
            long t0 = System.currentTimeMillis();
            int during = gate.hold(() -> {
                int before = propagations.get();
                // body 안의 편집이 다시 세우려 해도 기다리지 않는다(시뮬레이터 스레드는 이미 서 있다)
                gate.hold(() -> {
                    Thread.sleep(20);
                    return null;
                });
                return propagations.get() - before;
            });
            assertEquals(0, during);
            assertTrue(System.currentTimeMillis() - t0 < SimGate.ARRIVE_MS, "the nested hold waited");
        } finally {
            gate.close();
        }
    }

    @Test
    void aFailingBodyReleasesTheSimulator() throws Exception {
        SimGate gate = new SimGate(sim);
        try {
            assertThrows(IllegalStateException.class, () -> gate.hold(() -> {
                throw new IllegalStateException("edit failed");
            }));
            int after = propagations.get();
            waitFor(() -> propagations.get() > after + 20, "propagations after a failed body");
        } finally {
            gate.close();
        }
    }

    @Test
    void aStoppedSimulatorDoesNotBlockTheBodyForever() throws Exception {
        SimGate gate = new SimGate(sim, 200);
        try {
            sim.setIsTicking(false);
            sim.shutDown(); // 시뮬레이터 스레드가 끝나 알림에 오지 않는다
            Thread.sleep(300);
            long t0 = System.currentTimeMillis();
            assertEquals("done", gate.hold(() -> "done"));
            long ms = System.currentTimeMillis() - t0;
            assertTrue(ms >= 150 && ms < 5_000, "waited " + ms + " ms");
        } finally {
            gate.close();
        }
    }
}
