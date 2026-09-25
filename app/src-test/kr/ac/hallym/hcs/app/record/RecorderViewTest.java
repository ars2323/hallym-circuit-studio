/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * C-03 지난 사이클 보기(원조 Simulator 스레드와 함께): 그 스텝으로 회로 상태가 바뀌고, 마지막 스텝으로 돌아오면 원래
 * 상태를 다시 쓰고, 지난 스텝에서 틱하거나 입력을 바꾸면 그 뒤 기록을 버리고 거기서 진행한다. 서브회로 안을 보고
 * 있으면 같은 인스턴스 안에서 시점이 바뀐다.
 */
class RecorderViewTest {
    @TempDir
    Path tmp;

    Project proj;
    Component reg;
    Component stepPin;
    Component incInst;
    Circuit inc;

    static void waitFor(BooleanSupplier ok, String what) throws Exception {
        RecorderTest.waitFor(ok, what);
    }

    /** 8비트 누산기: R ← R + step, 더하기는 서브회로 inc 안. */
    void build() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit main = file.getMainCircuit();
        inc = new Circuit("inc");
        file.addCircuit(inc);
        CircuitBuilder sb = new CircuitBuilder(file, inc);
        sb.input("a", 8, 100, 100);
        sb.input("k", 8, 100, 200);
        Component adder = sb.add("Arithmetic", "Adder", 300, 150, "width", "8");
        sb.tunnel(adder, 0, "a");
        sb.tunnel(adder, 1, "k");
        sb.output("s", 8, 500, 150);
        sb.tunnel(adder, 2, "s");
        sb.commit();
        CircuitBuilder b = new CircuitBuilder(file, main);
        Component clk = b.add("Wiring", "Clock", 100, 400);
        b.tunnel(clk, 0, "clk");
        reg = b.add("Memory", "Register", 400, 200, "width", "8", "label", "R");
        b.tunnel(reg, 0, "q");
        b.tunnel(reg, 1, "next");
        b.tunnel(reg, 2, "clk");
        stepPin = b.add("Wiring", "Pin", 100, 300, "width", "8", "tristate", "false", "label", "step");
        b.tunnel(stepPin, 0, "k");
        incInst = b.addSubcircuit(inc, 600, 400);
        List<Integer> ins = new ArrayList<>();
        int out = -1;
        for (int i = 0; i < incInst.getEnds().size(); i++) {
            if (incInst.getEnd(i).isOutput()) {
                out = i;
            } else {
                ins.add(i);
            }
        }
        ins.sort((x, y) -> incInst.getEnd(x).getLocation().getY() - incInst.getEnd(y).getLocation().getY());
        b.tunnel(incInst, ins.get(0), "q");
        b.tunnel(incInst, ins.get(1), "k");
        b.tunnel(incInst, out, "next");
        b.commit();
        proj = new Project(file);
    }

    Location q() {
        return reg.getEnd(0).getLocation();
    }

    int liveQ() {
        CircuitState s = proj.getCircuitState();
        while (s.getParentState() != null) {
            s = s.getParentState();
        }
        return s.getValue(q()).toIntValue();
    }

    void poke(int v) {
        CircuitState s = proj.getSimulator().getCircuitState();
        InstanceState st = s.getInstanceState(stepPin);
        Pin.FACTORY.setValue(st, Value.createKnown(BitWidth.create(8), v));
        st.fireInvalidated();
        proj.getSimulator().requestPropagate();
    }

    void ticks(Recorder rec, int n) throws Exception {
        Simulator sim = proj.getSimulator();
        for (int i = 0; i < n; i++) {
            int want = rec.current().cursor() + 1;
            sim.tick();
            waitFor(() -> rec.current().last() == want && rec.current().cursor() == want, "tick to " + want);
        }
    }

    @Test
    void viewingAPastCycleAndGoingOnFromThere() throws Exception {
        build();
        Recorder rec = Recorder.of(proj);
        Recorder.requestReset(proj);
        waitFor(() -> rec.current() != null && rec.current().last() == 0 && rec.current().value(q(), 0) != null
                && rec.current().value(q(), 0).isFullyDefined(), "start");
        poke(1); // 리셋은 핀 값도 지우므로 리셋 뒤에 넣는다
        waitFor(() -> rec.current().value(stepPin.getEnd(0).getLocation(), 0).toIntValue() == 1, "step input 1");
        ticks(rec, 20);
        Recording r = rec.current();
        CircuitState present = proj.getCircuitState();
        assertEquals(10, liveQ());

        // 스텝 8(4사이클 뒤)을 본다: 회로 상태가 그때 값으로
        assertEquals(8, rec.view(8));
        assertEquals(8, r.cursor());
        assertTrue(r.isViewingPast());
        assertNotSame(present, proj.getCircuitState());
        assertEquals(4, liveQ());
        assertEquals(20, r.last(), "viewing does not drop the future");
        assertFalse(proj.getSimulator().isTicking());

        // 값이 바뀌지 않은 전파 알림은 기록을 건드리지 않는다
        proj.getSimulator().requestPropagate();
        Thread.sleep(200);
        assertEquals(20, r.last());

        // 마지막으로 돌아오면 원래 상태 객체
        assertEquals(20, rec.view(20));
        assertSame(present, proj.getCircuitState());
        assertEquals(10, liveQ());

        // 서브회로 안을 보고 있으면 같은 인스턴스 안에서 시점이 바뀐다
        CircuitState sub = (CircuitState) present.getData(incInst);
        proj.setCircuitState(sub);
        rec.view(6);
        CircuitState now = proj.getCircuitState();
        assertSame(inc, now.getCircuit(), "still inside inc");
        assertEquals(Collections.singletonList(incInst), Recorder.pathOf(now));
        assertEquals(3, now.getParentState().getValue(q()).toIntValue());
        proj.setCircuitState(now.getParentState());

        // 지난 스텝(6)에서 한 사이클 진행: 7~20을 버리고 거기서 이어간다
        ticks(rec, 2);
        assertEquals(8, r.last());
        assertEquals(8, r.cursor());
        assertFalse(r.isViewingPast());
        assertEquals(4, liveQ());

        // 지난 스텝에서 입력을 바꾸면 그 뒤를 버리고 그 스텝에 다시 적는다
        ticks(rec, 4);
        rec.view(10);
        poke(5);
        waitFor(() -> r.last() == 10 && r.value(stepPin.getEnd(0).getLocation(), 10).toIntValue() == 5,
                "poke at step 10 drops 11..12");
        ticks(rec, 2);
        assertEquals(5 + 5, liveQ(), "R was 5 at step 10, then +5");
    }
}
