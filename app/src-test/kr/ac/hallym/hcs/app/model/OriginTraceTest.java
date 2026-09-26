/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.record.Recording;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * D-01 E·X 출처 추적(PLAN.md 4.3): 구동자 없는 선, 두 구동자 충돌, 입력은 정해졌는데 출력이 X인 부품, 서브회로 경계
 * 안팎, MUX는 고른 입력만, 레지스터는 기록으로 그 값이 들어온 에지 직전으로. 정해진 값에서는 추적하지 않는다.
 */
class OriginTraceTest {
    @TempDir
    Path tmp;

    LogisimFile fresh() throws Exception {
        return CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
    }

    static CircuitState run(LogisimFile f) {
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        CircuitState s = new CircuitState(proj, f.getMainCircuit());
        s.getPropagator().propagate();
        return s;
    }

    static boolean has(Trace.Node n, Component c) {
        for (Netlist.PortRef p : n.net.ports()) {
            if (p.component == c) {
                return true;
            }
        }
        return false;
    }

    @Test
    void undrivenWireBehindAGate() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "2");
        b.tunnel(and, 1, "u"); // 아무도 구동하지 않는 이름
        Component u = null;
        b.constant("one", 1, 1, 100, 300);
        b.tunnel(and, 2, "one");
        Component out = b.output("y", 1, 500, 100);
        b.tunnel(and, 0, "y");
        b.commit();
        CircuitState s = run(f);
        OriginTrace t = new OriginTrace(f.getMainCircuit(), OriginTrace.live(s));
        OriginTrace.Origin o = t.find(t.node(out, 0), 0);
        assertNotNull(o);
        assertEquals(OriginTrace.Cause.UNDRIVEN, o.cause);
        assertTrue(has(o.node, and), "the undriven net is the AND input");
        assertEquals(null, o.component);
        assertTrue(o.chain.size() >= 2, "output net, then the input net");
    }

    @Test
    void twoConstantsFightOnOneWire() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component k0 = b.constant("w", 1, 0, 100, 100);
        Component k1 = b.constant("w", 1, 1, 100, 200);
        Component not = b.add("Gates", "NOT Gate", 300, 300);
        b.tunnel(not, 1, "w");
        b.tunnel(not, 0, "y");
        Component out = b.output("y", 1, 500, 100);
        b.commit();
        CircuitState s = run(f);
        OriginTrace t = new OriginTrace(f.getMainCircuit(), OriginTrace.live(s));
        OriginTrace.Origin o = t.find(t.node(out, 0), 0);
        assertEquals(OriginTrace.Cause.CONFLICT, o.cause);
        assertEquals(new HashSet<>(Arrays.asList(k0, k1)), new HashSet<>(o.drivers));
        assertTrue(o.isError());
    }

    @Test
    void definedValueHasNoOrigin() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.constant("a", 1, 1, 100, 100);
        Component not = b.add("Gates", "NOT Gate", 300, 300);
        b.tunnel(not, 1, "a");
        b.tunnel(not, 0, "y");
        Component out = b.output("y", 1, 500, 100);
        b.commit();
        CircuitState s = run(f);
        OriginTrace t = new OriginTrace(f.getMainCircuit(), OriginTrace.live(s));
        assertNull(t.find(t.node(out, 0), 0));
    }

    @Test
    void acrossASubcircuitInAndOut() throws Exception {
        LogisimFile f = fresh();
        Circuit inv = new Circuit("inv");
        f.addCircuit(inv);
        CircuitBuilder ib = new CircuitBuilder(f, inv);
        ib.input("a", 1, 100, 100);
        Component not = ib.add("Gates", "NOT Gate", 300, 100);
        ib.tunnel(not, 1, "a");
        ib.tunnel(not, 0, "y");
        ib.output("y", 1, 500, 100);
        ib.commit();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component sub = b.addSubcircuit(inv, 400, 300);
        b.tunnel(sub, 0, "nobody"); // 서브회로 입력 a: 구동자 없음
        b.tunnel(sub, 1, "y");
        Component out = b.output("y", 1, 600, 100);
        b.commit();
        CircuitState s = run(f);
        OriginTrace t = new OriginTrace(f.getMainCircuit(), OriginTrace.live(s));
        OriginTrace.Origin o = t.find(t.node(out, 0), 0);
        assertEquals(OriginTrace.Cause.UNDRIVEN, o.cause);
        assertEquals(Collections.emptyList(), o.node.instances, "back out in main");
        assertTrue(has(o.node, sub), "the net at the subcircuit input");
        boolean inside = false;
        for (Trace.Node n : o.chain) {
            inside |= !n.instances.isEmpty();
        }
        assertTrue(inside, "went through the NOT inside inv");
    }

    @Test
    void muxFollowsOnlyWhatItSelects() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component mux = b.add("Plexers", "Multiplexer", 400, 300, "width", "8", "enable", "false");
        b.tunnel(mux, 0, "x0"); // 입력 0: 구동자 없음(X)
        b.constant("k", 8, 5, 100, 100);
        b.tunnel(mux, 1, "k");
        b.tunnel(mux, 2, "sel");
        b.tunnel(mux, 3, "y");
        Component sel = b.input("sel", 1, 100, 200);
        Component out = b.output("y", 8, 600, 100);
        b.commit();
        CircuitState s = run(f);
        // 입력 핀은 3상태 기본값이라 처음에는 X: 선택 입력을 따라가 그 핀에서 멈춘다
        OriginTrace t0 = new OriginTrace(f.getMainCircuit(), OriginTrace.live(s));
        OriginTrace.Origin o0 = t0.find(t0.node(out, 0), 0);
        assertEquals(OriginTrace.Cause.INPUT_PIN, o0.cause);
        assertEquals(sel, o0.component);
        com.cburch.logisim.std.wiring.Pin.FACTORY.setValue(s.getInstanceState(sel),
                com.cburch.logisim.data.Value.FALSE); // sel = 0: 입력 0(X)을 고른다
        s.getInstanceState(sel).fireInvalidated();
        s.getPropagator().propagate();
        OriginTrace t = new OriginTrace(f.getMainCircuit(), OriginTrace.live(s));
        OriginTrace.Origin o = t.find(t.node(out, 0), 0);
        assertEquals(OriginTrace.Cause.UNDRIVEN, o.cause);
        assertTrue(has(o.node, mux) && o.node.net == t.node(mux, 0).net, "the selected input 0");
        // 선택 1이면 출력이 정해져 추적할 것이 없다
        com.cburch.logisim.std.wiring.Pin.FACTORY.setValue(s.getInstanceState(sel),
                com.cburch.logisim.data.Value.TRUE);
        s.getInstanceState(sel).fireInvalidated();
        s.getPropagator().propagate();
        assertNull(t.find(t.node(out, 0), 0));
    }

    @Test
    void recordedValuesTraceAPastStep() throws Exception {
        // 기록값으로 지난 스텝을 추적한다(D-03이 쓰기 시도 직전 스텝에 쓴다): 스텝 1까지는 u가 떠 있고 그 뒤 정해진다
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "2", "width", "8");
        b.tunnel(and, 1, "u");
        b.constant("ff", 8, 0xff, 100, 100);
        b.tunnel(and, 2, "ff");
        b.tunnel(and, 0, "d");
        Component u = b.input("u", 8, 100, 300);
        Component out = b.output("d", 8, 700, 100);
        b.commit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        CircuitState root = new CircuitState(proj, f.getMainCircuit());
        root.getPropagator().propagate();
        Recording r = new Recording(f.getMainCircuit());
        r.restart(root, 0); // 입력 핀은 3상태 기본값이라 X
        com.cburch.logisim.std.wiring.Pin.FACTORY.setValue(root.getInstanceState(u),
                com.cburch.logisim.data.Value.createKnown(com.cburch.logisim.data.BitWidth.create(8), 7));
        root.getInstanceState(u).fireInvalidated();
        root.getPropagator().propagate();
        r.capture(root, 1, false);
        OriginTrace past = new OriginTrace(f.getMainCircuit(), r.originValues());
        OriginTrace.Origin o = past.find(past.node(out, 0), 0);
        assertEquals(OriginTrace.Cause.INPUT_PIN, o.cause);
        assertEquals(u, o.component);
        assertEquals(0, o.step);
        assertNull(past.find(past.node(out, 0), 1), "defined at step 1");
    }
}
