/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SimulatorEvent;
import com.cburch.logisim.circuit.SimulatorListener;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * D-091: 시뮬레이션이 꺼져 있으면 틱하지 않고 알린다(꺼진 시뮬레이터에 틱을 요청하면 원조 전파 스레드가 끝없이 돈다).
 * 켜져 있으면 보통처럼 틱한다.
 */
class TickGuardTest {
    @TempDir
    Path tmp;

    static int propagations(Simulator sim, Runnable act, long waitMs) throws Exception {
        AtomicInteger n = new AtomicInteger();
        SimulatorListener l = new SimulatorListener() {
            public void propagationCompleted(SimulatorEvent e) {
                n.incrementAndGet();
            }

            public void tickCompleted(SimulatorEvent e) {
            }

            public void simulatorStateChanged(SimulatorEvent e) {
            }
        };
        sim.addSimulatorListener(l);
        try {
            act.run();
            Thread.sleep(waitMs);
        } finally {
            sim.removeSimulatorListener(l);
        }
        return n.get();
    }

    @Test
    void refusesToTickWhileOffAndTicksWhileOn() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.add("Wiring", "Clock", 100, 100);
        b.commit();
        Project proj = new Project(f);
        Simulator sim = proj.getSimulator();
        sim.setIsRunning(true);
        Thread.sleep(200);
        assertTrue(propagations(sim, () -> assertTrue(TickGuard.tick(proj)), 300) >= 1, "a tick propagates");

        sim.setIsRunning(false);
        Thread.sleep(200);
        int spins = propagations(sim, () -> assertFalse(TickGuard.tick(proj)), 300);
        assertEquals(0, spins, "no tick, no propagation loop while off");
        assertEquals(kr.ac.hallym.hcs.app.Messages.get("bar.simOff"), SimControls.lastNotice(proj));
        assertFalse(TickGuard.canTick(sim));
        SimControls.runCycles(proj, 3); // 창 없는 경로도 거절한다
        assertEquals(0, propagations(sim, () -> { }, 200));
    }
}
