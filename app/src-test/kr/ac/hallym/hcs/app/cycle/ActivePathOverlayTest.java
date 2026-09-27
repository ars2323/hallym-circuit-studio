/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;

import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/**
 * C-08 활성 경로: demo-datapath의 MemtoReg MUX. 선택이 0이면 0번 입력(ALU Result) 넷만, 1이면 1번 입력(ReadData) 넷만,
 * 선택이 정해지지 않으면 칠하지 않는다. 회로 상태는 읽기만 한다.
 */
class ActivePathOverlayTest {
    @TempDir
    Path tmp;

    @Test
    void selectedMuxInputOnly() throws Exception {
        LogisimFile file = RecordingTestSupport.openCirc(tmp, "demo-datapath.circ");
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        Circuit main = file.getMainCircuit();
        CircuitState state = new CircuitState(proj, main);
        state.getPropagator().propagate();
        Component mux = null;
        for (Component c : main.getNonWires()) {
            if (c.getFactory().getName().equals("Multiplexer")) {
                mux = c;
            }
        }
        assertNotNull(mux);
        Netlist nl = Netlist.of(main);
        Set<Wire> in0 = Set.copyOf(nl.netOf(mux, 0).wires());
        Set<Wire> in1 = Set.copyOf(nl.netOf(mux, 1).wires());
        assertFalse(in0.isEmpty());
        assertFalse(in1.isEmpty());
        // 선택은 입력 핀 MemtoReg가 준다(핀 값을 바꾸는 것은 원조 Poke와 같다)
        Component pin = null;
        for (Component c : main.getNonWires()) {
            if (c.getFactory() == Pin.FACTORY && "MemtoReg".equals(c.getAttributeSet().getValue(StdAttr.LABEL))) {
                pin = c;
            }
        }
        assertNotNull(pin, "MemtoReg input pin");
        // 떠 있는 선택을 줄 수 있게 이 테스트의 메모리 속 회로에서만 핀을 3상태로
        pin.getAttributeSet().setValue(Pin.ATTR_TRISTATE, Boolean.TRUE);
        state = new CircuitState(proj, main);
        state.getPropagator().propagate();
        InstanceState ps = state.getInstanceState(pin);

        Pin.FACTORY.setValue(ps, Value.FALSE);
        ps.fireInvalidated();
        state.getPropagator().propagate();
        java.util.List<Location[]> a = ActivePathOverlay.selected(main, state);
        assertTrue(onWires(a, in0) && !onWires(a, in1), "select 0 → input 0: " + text(a));
        // V-04: 넷 전체가 아니라 ALU Result 출력에서 MUX 입력 0까지의 가지만. Data Memory Addr로 가는 가지는 없다
        Component dmem = null;
        for (Component c : main.getNonWires()) {
            if (c.getFactory().getName().equals("Data Memory")) {
                dmem = c;
            }
        }
        assertNotNull(dmem);
        Location addr = dmem.getEnd(0).getLocation();
        assertTrue(nl.netOf(mux, 0) == nl.netOf(dmem, 0), "Addr shares the ALU Result net");
        for (Location[] seg : a) {
            assertFalse(seg[0].equals(addr) || seg[1].equals(addr), "no segment reaches Addr: " + text(a));
        }
        assertTrue(text(a).length() < text(nl.netOf(mux, 0).wires().stream()
                .map(w -> new Location[] {w.getEnd0(), w.getEnd1()}).collect(java.util.stream.Collectors.toList()))
                .length(), "fewer segments than the whole net");
        assertEquals(EXPECTED_SELECT_0, text(a), "fixed branch for the demo circuit (D-099)");

        Pin.FACTORY.setValue(ps, Value.TRUE);
        ps.fireInvalidated();
        state.getPropagator().propagate();
        java.util.List<Location[]> b = ActivePathOverlay.selected(main, state);
        assertTrue(onWires(b, in1) && !onWires(b, in0), "select 1 → input 1: " + text(b));
        assertEquals(EXPECTED_SELECT_1, text(b));

        Pin.FACTORY.setValue(ps, Value.UNKNOWN);
        ps.fireInvalidated();
        state.getPropagator().propagate();
        assertEquals(java.util.List.of(), ActivePathOverlay.selected(main, state), "undetermined select: nothing");
    }

    /** 30장면 회로(demo-datapath)의 고정 기대값: 선분 목록 "(x1,y1)-(x2,y2)" 공백 구분. */
    static final String EXPECTED_SELECT_0 = "(1100,240)-(1140,240) (1140,240)-(1140,210) (1140,210)-(1490,210) (1490,210)-(1490,420) (1490,420)-(1510,420)";
    static final String EXPECTED_SELECT_1 = "(1440,440)-(1510,440)"; // ReadData에서 입력 1까지 한 선분

    static String text(java.util.List<Location[]> segs) {
        StringBuilder sb = new StringBuilder();
        for (Location[] s : segs) {
            sb.append(sb.length() > 0 ? " " : "").append(s[0]).append('-').append(s[1]);
        }
        return sb.toString();
    }

    /** 선분이 모두 이 선들 위에 있는가(선분 하나라도 있어야 참). */
    static boolean onWires(java.util.List<Location[]> segs, Set<Wire> wires) {
        if (segs.isEmpty()) {
            return false;
        }
        for (Location[] s : segs) {
            boolean on = false;
            for (Wire w : wires) {
                on |= w.contains(s[0]) && w.contains(s[1]);
            }
            if (!on) {
                return false;
            }
        }
        return true;
    }

    /**
     * D-129: 넷 목록 캐시는 부품·선의 정체로 다시 쓸지 정한다. MUX를 같은 자리의 새 MUX로 바꾸면(부품 수는 같다) 새
     * 넷 목록을 만든다. identity hash 합 서명은 -XX:hashCode=2 JVM에서 옛 목록을 다시 써서 새 MUX의 가지를 못 그렸다.
     */
    @Test
    void replacingTheMuxRebuildsTheNetlist() throws Exception {
        LogisimFile f = kr.ac.hallym.hcs.regress.CircuitBuilder.newFile(new com.cburch.logisim.file.Loader(null),
                java.nio.file.Files.createTempDirectory(tmp, "r").toFile());
        kr.ac.hallym.hcs.regress.CircuitBuilder b = new kr.ac.hallym.hcs.regress.CircuitBuilder(f, f.getMainCircuit());
        Component mux = b.add("Plexers", "Multiplexer", 400, 200);
        Location in0 = mux.getEnd(0).getLocation();
        b.add("Wiring", "Constant", in0.getX() - 60, in0.getY(), "value", "0x1");
        b.wire(Location.create(in0.getX() - 60, in0.getY()), in0);
        b.constant("sel", 1, 0, 100, 400);
        b.tunnel(mux, 2, "sel");
        b.tunnel(mux, mux.getEnds().size() - 1, "y");
        b.output("y", 1, 700, 200);
        b.commit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);
        Circuit main = f.getMainCircuit();
        CircuitState st = new CircuitState(proj, main);
        st.getPropagator().propagate();
        assertFalse(ActivePathOverlay.selected(main, st).isEmpty(), "select 0: the wired input 0");
        Netlist before = ActivePathOverlay.netlist(main);
        assertTrue(before == ActivePathOverlay.netlist(main), "unchanged: the cached netlist");
        com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(main);
        Component fresh = mux.getFactory().createComponent(mux.getLocation(),
                (com.cburch.logisim.data.AttributeSet) mux.getAttributeSet().clone());
        m.replace(mux, fresh);
        m.execute();
        Netlist after = ActivePathOverlay.netlist(main);
        assertTrue(before != after, "a new MUX object: a new netlist");
        assertNotNull(after.netOf(fresh, 0));
        st = new CircuitState(proj, main);
        st.getPropagator().propagate();
        assertFalse(ActivePathOverlay.selected(main, st).isEmpty(), "the new MUX's selected input is shown");
    }
}
