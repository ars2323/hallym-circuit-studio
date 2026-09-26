/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/**
 * #71: 넷 모델. 부록 A.4의 연결 규칙마다 상수(1)와 출력 핀을 두고, 원조 2.7.1 엔진(-tty)이 핀에 1을 내는지와
 * 넷 모델이 같은 넷이라고 하는지를 대조한다. 탐색은 기대 결과와 비교한다.
 */
class NetlistTest {
    static final File ORIGINAL_JAR = new File(System.getProperty("hcs.logisimJar"));

    @TempDir
    Path tmp;

    Component constant(CircuitBuilder b, int x, int y) {
        return b.add("Wiring", "Constant", x, y, "value", "0x1");
    }

    Component out(CircuitBuilder b, String label, int x, int y, String facing) {
        return b.add("Wiring", "Pin", x, y, "output", "true", "facing", facing, "label", label);
    }

    void wire(CircuitBuilder b, int x0, int y0, int x1, int y1) {
        b.wire(Location.create(x0, y0), Location.create(x1, y1));
    }

    @Test
    void appendixA4RulesMatchTheEngine() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Map<String, Component[]> cases = new LinkedHashMap<>(); // 이름 → {상수, 핀}
        int y = 100;
        // a) 끝점 없는 +자 교차: 연결 안 됨
        Component ca = constant(b, 100, y);
        wire(b, 100, y, 300, y);
        wire(b, 200, y - 40, 200, y + 40);
        cases.put("a_cross", new Component[] {ca, out(b, "a_cross", 200, y + 40, "north")});
        cases.put("a_line", new Component[] {ca, out(b, "a_line", 300, y, "west")});
        y += 120;
        // b) T자: 연결
        Component cb = constant(b, 100, y);
        wire(b, 100, y, 300, y);
        wire(b, 200, y, 200, y + 40);
        cases.put("b_tee", new Component[] {cb, out(b, "b_tee", 200, y + 40, "north")});
        y += 120;
        // c) 두 선 모두 교차점에서 잘림: 연결
        Component cc = constant(b, 100, y);
        wire(b, 100, y, 200, y);
        wire(b, 200, y, 300, y);
        wire(b, 200, y - 40, 200, y);
        wire(b, 200, y, 200, y + 40);
        cases.put("c_both_cut", new Component[] {cc, out(b, "c_both_cut", 200, y + 40, "north")});
        y += 120;
        // d) 한 선만 잘림: 세로선과 연결 안 됨
        Component cd = b.add("Wiring", "Constant", 200, y - 40, "value", "0x1", "facing", "south");
        wire(b, 100, y, 200, y);
        wire(b, 200, y, 300, y);
        wire(b, 200, y - 40, 200, y + 40);
        cases.put("d_one_cut", new Component[] {cd, out(b, "d_one_cut", 300, y, "west")});
        y += 120;
        // d') 같은 모양에서 가로 두 조각은 하나로 합쳐짐
        Component cd2 = constant(b, 100, y);
        wire(b, 100, y, 200, y);
        wire(b, 200, y, 300, y);
        wire(b, 200, y - 40, 200, y + 40);
        cases.put("d_halves", new Component[] {cd2, out(b, "d_halves", 300, y, "west")});
        y += 120;
        // e) 선이 남의 포트 위를 지나감: 연결
        Component ce = constant(b, 100, y);
        wire(b, 100, y, 300, y);
        cases.put("e_over_port", new Component[] {ce, out(b, "e_over_port", 200, y, "north")});
        y += 120;
        // -tty가 한 번 돌고 멈추도록 halt 핀(원조 규칙: 이름이 halt인 출력 핀이 1이면 멈춤, 행에는 안 나옴)
        Component hk = constant(b, 100, y);
        wire(b, 100, y, 200, y);
        out(b, "halt", 200, y, "west");
        b.commit();

        Circuit main = file.getMainCircuit();
        Netlist nl = Netlist.of(main);
        File circ = tmp.resolve("rules.circ").toFile();
        CircuitBuilder.save(file, circ);
        String tty = Engine.current(ORIGINAL_JAR).run(tmp.toFile(), "rules");
        String[] lines = tty.split("\n");
        assertEquals("exit=0", lines[0], tty);
        String[] values = lines[1].trim().split("\t");

        // -tty table은 출력 핀을 위→아래(같으면 왼쪽→오른쪽) 순서로 낸다
        List<Component> pins = new ArrayList<>();
        for (Component[] c : cases.values()) {
            if (!pins.contains(c[1])) {
                pins.add(c[1]);
            }
        }
        pins.sort((p, q) -> p.getLocation().getY() != q.getLocation().getY()
                ? p.getLocation().getY() - q.getLocation().getY() : p.getLocation().getX() - q.getLocation().getX());
        assertEquals(pins.size(), values.length, tty);
        Map<String, Boolean> expected = new LinkedHashMap<>();
        expected.put("a_cross", false);
        expected.put("a_line", true);
        expected.put("b_tee", true);
        expected.put("c_both_cut", true);
        expected.put("d_one_cut", false);
        expected.put("d_halves", true);
        expected.put("e_over_port", true);
        for (Map.Entry<String, Component[]> e : cases.entrySet()) {
            Component k = e.getValue()[0];
            Component pin = e.getValue()[1];
            boolean engine = values[pins.indexOf(pin)].trim().equals("1");
            boolean model = nl.netOf(k, 0) == nl.netOf(pin, 0);
            assertEquals(expected.get(e.getKey()), engine, e.getKey() + " (engine)");
            assertEquals(engine, model, e.getKey() + ": the net model must agree with the engine");
        }
    }

    @Test
    void tunnelsMergeAndSplitterLinksBits() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component src = b.add("Wiring", "Pin", 100, 100, "width", "4", "label", "src");
        b.tunnel(src, 0, "bus");
        Component sp = b.add("Wiring", "Splitter", 300, 100, "incoming", "4", "fanout", "2");
        b.tunnel(sp, 0, "bus");
        Component lo = b.add("Wiring", "Pin", 500, 60, "width", "2", "output", "true", "facing", "west", "label", "lo");
        Component hi = b.add("Wiring", "Pin", 500, 90, "width", "2", "output", "true", "facing", "west", "label", "hi");
        b.tunnel(sp, 1, "arm0");
        b.tunnel(lo, 0, "arm0");
        b.tunnel(sp, 2, "arm1");
        b.tunnel(hi, 0, "arm1");
        b.commit();
        Netlist nl = Netlist.of(file.getMainCircuit());
        assertSame(nl.netOf(src, 0), nl.netOf(sp, 0), "same tunnel label is one net");
        assertSame(nl.netOf(lo, 0), nl.netOf(sp, 1));
        assertFalse(nl.netOf(sp, 0) == nl.netOf(sp, 1), "a splitter does not merge nets");
        assertEquals(4, nl.bitLinks().size());
        int[] arms = Netlist.splitterArms(sp);
        assertEquals(Arrays.toString(new int[] {0, 0, 1, 1}), Arrays.toString(arms), "bits 0-1 → arm0 (top)");
        for (Netlist.BitLink l : nl.bitLinks()) {
            assertEquals(l.bit % 2, l.armBit);
            assertSame(l.armIndex == 0 ? nl.netOf(lo, 0) : nl.netOf(hi, 0), l.arm);
        }
        assertEquals(1, nl.netOf(src, 0).drivers().size(), "the input pin drives the bus");
    }

    /** 앞/뒤 탐색: 레지스터에서 멈추고 옵션으로 넘는다. 서브회로를 들어갔다 나온다. 스플리터를 건넌다. */
    @Test
    void traceStopsAtStateAndCrossesSubcircuits() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit inv = new Circuit("inv");
        file.addCircuit(inv);
        CircuitBuilder ib = new CircuitBuilder(file, inv);
        ib.input("a", 1, 100, 100);
        Component not = ib.add("Gates", "NOT Gate", 300, 100);
        ib.tunnel(not, 1, "a");
        ib.tunnel(not, 0, "y");
        ib.output("y", 1, 500, 100);
        ib.commit();

        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component x = b.input("x", 1, 100, 100);
        Component and = b.add("Gates", "AND Gate", 300, 100);
        b.tunnel(and, 1, "x");
        b.tunnel(and, 2, "x");
        b.tunnel(and, 0, "d");
        Component reg = b.add("Memory", "Register", 500, 100, "width", "1", "label", "R");
        b.tunnel(reg, 1, "d");
        b.tunnel(reg, 0, "q");
        Component or = b.add("Gates", "OR Gate", 700, 100);
        b.tunnel(or, 1, "q");
        b.tunnel(or, 2, "q");
        b.tunnel(or, 0, "z0");
        Component sub = b.addSubcircuit(inv, 900, 100);
        b.tunnel(sub, 0, "z0");
        b.tunnel(sub, 1, "z");
        Component z = b.output("z", 1, 1100, 100);
        b.commit();
        Circuit main = file.getMainCircuit();
        assertEquals("a", Kinds.portName(sub, 0));

        Trace t = new Trace();
        Trace.Node start = t.node(main, x, 0);
        Trace.Result stop = t.forward(start, false);
        assertTrue(stop.components.contains(and));
        assertTrue(stop.stoppedAt.contains(reg), "stops at the register");
        assertFalse(stop.components.contains(or));

        Trace.Result through = t.forward(start, true);
        assertTrue(through.components.contains(or));
        Netlist top = t.netlist(main);
        boolean reachedZ = false;
        boolean wentInside = false;
        for (Trace.Node n : through.nets) {
            reachedZ |= n.circuit == main && n.net == top.netOf(z, 0);
            wentInside |= n.circuit == inv && n.instances.size() == 1 && n.instances.get(0) == sub;
        }
        assertTrue(wentInside, "went into the subcircuit");
        assertTrue(reachedZ, "came back out to z");

        Trace.Result back = t.backward(t.node(main, z, 0), false);
        assertTrue(back.components.contains(sub) || back.nets.stream().anyMatch(n -> n.circuit == inv));
        assertTrue(back.stoppedAt.contains(reg), "backward also stops at the register");
        assertFalse(back.components.contains(and));
        assertNotNull(Trace.describe(start));
    }

    /**
     * V-04: 넷 안의 가지. 상수 → T자 갈림 → 핀 둘. 핀 B까지의 가지는 갈림 뒤 A 쪽 조각을 담지 않고, 긴 선의 일부만
     * 지나면 그 조각만 나온다. 터널 짝은 길이 0으로 건너고 선분을 내지 않는다.
     */
    @Test
    void branchFollowsOnlyTheWayToThePort() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component k = constant(b, 100, 100); // 출력 자리 (100,100)
        Component a = out(b, "A", 300, 100, "west");
        Component bb = out(b, "B", 200, 200, "west");
        wire(b, 100, 100, 300, 100); // 긴 선 하나: (200,100)에서 갈림
        wire(b, 200, 100, 200, 200);
        // 터널 짝: 상수 → t ... t → 핀 C
        Component t1 = b.add("Wiring", "Tunnel", 100, 300, "label", "t", "facing", "west");
        wire(b, 100, 100, 100, 300);
        Component t2 = b.add("Wiring", "Tunnel", 400, 300, "label", "t", "facing", "east");
        Component c = out(b, "C", 500, 300, "west");
        wire(b, 400, 300, 500, 300);
        b.commit();
        Netlist nl = Netlist.of(file.getMainCircuit());
        Netlist.Net net = nl.netOf(k, 0);
        assertEquals(net, nl.netOf(bb, 0));
        assertEquals(net, nl.netOf(c, 0), "tunnels merge into one net");
        Location from = k.getEnd(0).getLocation();
        assertEquals("[(100,100)-(200,100), (200,100)-(200,200)]", segs(Netlist.branch(net, from, bb.getEnd(0).getLocation())),
                "to B: the first half of the long wire, then down; not the piece to A");
        assertEquals("[(100,100)-(200,100), (200,100)-(300,100)]", segs(Netlist.branch(net, from, a.getEnd(0).getLocation())));
        assertEquals("[(100,100)-(100,300), (400,300)-(500,300)]", segs(Netlist.branch(net, from, c.getEnd(0).getLocation())),
                "through the tunnel pair without a segment for the jump");
        assertEquals("[]", segs(Netlist.branch(net, from, Location.create(900, 900))), "unreachable: empty");
        assertEquals(t1.getFactory(), t2.getFactory());
    }

    static String segs(java.util.List<Location[]> list) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Location[] s : list) {
            out.add(s[0] + "-" + s[1]);
        }
        return out.toString();
    }
}
