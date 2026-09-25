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
}
