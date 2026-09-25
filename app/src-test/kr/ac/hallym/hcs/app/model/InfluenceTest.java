/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** P-01 영향 경로(P-07 Signal Flow가 같은 엔진을 쓴다): 고정 기대값. */
class InfluenceTest {
    @TempDir
    Path tmp;

    LogisimFile file;

    CircuitBuilder fresh() throws Exception {
        file = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        return new CircuitBuilder(file, file.getMainCircuit());
    }

    static String name(Component c) {
        Object label = c.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL);
        String l = label == null ? "" : label.toString();
        return l.isEmpty() ? c.getFactory().getName() + "@" + c.getLocation() : l;
    }

    static Set<String> names(java.util.Collection<Component> cs) {
        Set<String> ret = new TreeSet<>();
        for (Component c : cs) {
            if (!Kinds.of(c).factory().equals("Tunnel") && !Kinds.of(c).factory().equals("Pin")) {
                ret.add(name(c));
            }
        }
        return ret;
    }

    /** A → NOT(n1) → AND(a1, 다른 입력 B) → 레지스터 R → NOT(n2) → Y. */
    Component[] chain(CircuitBuilder b) {
        Component a = b.input("A", 1, 100, 100);
        b.input("B", 1, 100, 300);
        Component n1 = b.add("Gates", "NOT Gate", 300, 100, "label", "n1");
        b.tunnel(n1, 1, "A");
        b.tunnel(n1, 0, "na");
        Component a1 = b.add("Gates", "AND Gate", 500, 100, "label", "a1");
        b.tunnel(a1, 1, "na");
        b.tunnel(a1, 2, "B");
        b.tunnel(a1, 0, "d");
        Component r = b.add("Memory", "Register", 700, 100, "width", "1", "label", "R");
        b.tunnel(r, 1, "d");
        b.tunnel(r, 0, "q");
        Component n2 = b.add("Gates", "NOT Gate", 900, 100, "label", "n2");
        b.tunnel(n2, 1, "q");
        b.tunnel(n2, 0, "Y");
        Component y = b.output("Y", 1, 1100, 100);
        b.commit();
        return new Component[] {a, n1, a1, r, n2, y};
    }

    @Test
    void forwardStopsAtTheRegisterAndCrossesItOnRequest() throws Exception {
        CircuitBuilder b = fresh();
        Component[] c = chain(b);
        Circuit main = file.getMainCircuit();
        Influence stop = Influence.of(main, List.of(c[0]), Influence.Mode.FORWARD, false, -1);
        Influence.View v = stop.view(main);
        assertEquals(Set.of("a1", "n1"), names(v.forwardParts));
        assertEquals(Set.of("R"), names(v.stops));
        assertEquals(1, stop.maxDepth() - 1, "A→n1→a1: two parts crossed");

        Influence through = Influence.of(main, List.of(c[0]), Influence.Mode.FORWARD, true, -1);
        assertTrue(names(through.view(main).forwardParts).contains("n2"));
        // 레지스터를 넘은 곳은 다음 사이클
        Trace.Node yNet = null;
        for (Trace.Node n : through.forward().nets) {
            for (Netlist.PortRef p : n.net.ports()) {
                if (p.component == c[5]) {
                    yNet = n;
                }
            }
        }
        assertEquals(1, through.forward().cycle.get(yNet));
    }

    @Test
    void backwardIsSymmetric() throws Exception {
        CircuitBuilder b = fresh();
        Component[] c = chain(b);
        Circuit main = file.getMainCircuit();
        Influence back = Influence.of(main, List.of(c[5]), Influence.Mode.BACKWARD, false, -1);
        assertEquals(Set.of("n2"), names(back.view(main).backwardParts));
        assertEquals(Set.of("R"), names(back.view(main).stops));
    }

    @Test
    void depthLimitShowsOneStepAtATime() throws Exception {
        CircuitBuilder b = fresh();
        Component[] c = chain(b);
        Circuit main = file.getMainCircuit();
        Influence one = Influence.of(main, List.of(c[0]), Influence.Mode.FORWARD, false, 1);
        Set<String> parts = names(one.view(main).forwardParts);
        assertTrue(parts.contains("n1"), parts.toString());
        // a1은 닿았지만(입력) 건너지 않았다: 그 출력 넷은 없다
        for (Trace.Node n : one.forward().nets) {
            for (Netlist.PortRef p : n.net.ports()) {
                assertFalse(p.component == c[3] && p.end == 1, "the register input is two steps away");
            }
        }
    }

    @Test
    void betweenTwoPartsKeepsOnlyThePathBetween() throws Exception {
        CircuitBuilder b = fresh();
        Component[] c = chain(b);
        Circuit main = file.getMainCircuit();
        Influence between = Influence.between(main, c[1], c[3], false);
        Set<String> parts = names(between.view(main).forwardParts);
        assertEquals(Set.of("a1"), parts, "n1 → a1 → R");
    }

    @Test
    void sameNameTunnelsAreLinked() throws Exception {
        CircuitBuilder b = fresh();
        Component a = b.input("A", 1, 100, 100);
        Component n1 = b.add("Gates", "NOT Gate", 400, 100);
        b.tunnel(n1, 1, "A");
        b.tunnel(n1, 0, "Y");
        Component n2 = b.add("Gates", "NOT Gate", 400, 300);
        b.tunnel(n2, 1, "A");
        b.tunnel(n2, 0, "Z");
        b.output("Y", 1, 700, 100);
        b.output("Z", 1, 700, 300);
        b.commit();
        Circuit main = file.getMainCircuit();
        Influence.View v = Influence.of(main, List.of(a), Influence.Mode.FORWARD, false, -1).view(main);
        // A 넷: 핀 옆 터널 + 두 NOT 입력 터널 = 3개. 셋 이상이면 점선 없이 터널만 강조한다
        assertEquals(3, v.tunnels.stream().filter(x -> "A".equals(
                x.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.LABEL))).count());
        // Y·Z 넷은 터널이 둘(NOT 출력 옆, 출력 핀 옆): 점선 하나씩
        assertEquals(2, v.tunnelLinks.size(), v.tunnelLinks.toString());
        for (List<com.cburch.logisim.data.Location> l : v.tunnelLinks) {
            assertEquals(2, l.size());
        }
        assertEquals(2, v.forwardParts.stream().filter(x -> x.getFactory().getName().equals("NOT Gate")).count());
    }

    /** 클럭 넷은 터널이 둘이어도 점선으로 잇지 않는다(데이터 흐름이 아니다, 17c에서 화면을 가로질렀다). */
    @Test
    void clockTunnelsAreNotLinked() throws Exception {
        CircuitBuilder b = fresh();
        Component clk = b.add("Wiring", "Clock", 100, 300);
        b.tunnel(clk, 0, "clk");
        Component d = b.input("D", 1, 100, 100);
        Component r = b.add("Memory", "Register", 400, 100, "width", "1", "label", "R");
        b.tunnel(r, 1, "D");
        b.tunnel(r, 2, "clk");
        b.tunnel(r, 0, "Q");
        b.output("Q", 1, 600, 100);
        b.commit();
        Circuit main = file.getMainCircuit();
        Influence.View v = Influence.of(main, List.of(r), Influence.Mode.BACKWARD, false, -1).view(main);
        for (List<com.cburch.logisim.data.Location> l : v.tunnelLinks) {
            for (com.cburch.logisim.data.Location p : l) {
                assertFalse(p.equals(clk.getEnd(0).getLocation()), "no link on the clock net: " + l);
            }
        }
        assertEquals(1, v.tunnelLinks.size(), "the D net keeps its link: " + v.tunnelLinks);
        assertTrue(d != null);
    }

    /** 8비트 버스를 스플리터로 [3:0]·[7:4]로 나눈다. 비트 1만 따라가면 [3:0] 쪽만 간다. */
    @Test
    void splitterPassesOnlyTheFollowedBits() throws Exception {
        CircuitBuilder b = fresh();
        Component bus = b.input("bus", 8, 100, 100);
        Component sp = b.add("Wiring", "Splitter", 300, 100, "fanout", "2", "incoming", "8", "bit0", "0", "bit1",
                "0", "bit2", "0", "bit3", "0", "bit4", "1", "bit5", "1", "bit6", "1", "bit7", "1");
        b.tunnel(sp, 0, "bus");
        b.tunnel(sp, 1, "lo");
        b.tunnel(sp, 2, "hi");
        Component lo = b.add("Gates", "NOT Gate", 500, 100, "width", "4", "label", "nlo");
        b.tunnel(lo, 1, "lo");
        b.tunnel(lo, 0, "LO");
        Component hi = b.add("Gates", "NOT Gate", 500, 300, "width", "4", "label", "nhi");
        b.tunnel(hi, 1, "hi");
        b.tunnel(hi, 0, "HI");
        b.output("LO", 4, 700, 100);
        b.output("HI", 4, 700, 300);
        b.commit();
        Circuit main = file.getMainCircuit();
        Trace t = new Trace();
        Trace.Options o = new Trace.Options();
        o.startBits = new BitSet();
        o.startBits.set(1);
        Trace.Result r = t.forward(Collections.singletonList(t.node(main, bus, 0)), o);
        assertTrue(r.components.contains(lo), "bit 1 goes to [3:0]");
        assertFalse(r.components.contains(hi), "not to [7:4]");
        // 전체 비트면 둘 다
        Trace.Result all = t.forward(t.node(main, bus, 0), false);
        assertTrue(all.components.contains(lo) && all.components.contains(hi));
    }

    /** 서브회로: y는 a에만, z는 b에만 달렸다. a에서 앞으로 가면 y 쪽으로만 나온다. */
    @Test
    void subcircuitLetsTheFlowOutOnlyWhereItIsConnectedInside() throws Exception {
        CircuitBuilder b = fresh();
        Circuit sub = new Circuit("blk");
        file.addCircuit(sub);
        CircuitBuilder sb = new CircuitBuilder(file, sub);
        sb.input("a", 1, 100, 100);
        sb.input("b", 1, 100, 300);
        Component na = sb.add("Gates", "NOT Gate", 300, 100);
        sb.tunnel(na, 1, "a");
        sb.tunnel(na, 0, "y");
        Component nb = sb.add("Gates", "NOT Gate", 300, 300);
        sb.tunnel(nb, 1, "b");
        sb.tunnel(nb, 0, "z");
        sb.output("y", 1, 500, 100);
        sb.output("z", 1, 500, 300);
        sb.commit();

        Component a = b.input("A", 1, 100, 100);
        b.input("B", 1, 100, 300);
        Component inst = b.addSubcircuit(sub, 400, 200);
        for (int i = 0; i < inst.getEnds().size(); i++) {
            b.tunnel(inst, i, "p" + Kinds.portName(inst, i));
        }
        b.commit();
        Circuit main = file.getMainCircuit();
        // 바깥 A를 서브회로 a 포트 넷에 잇는다
        CircuitBuilder b2 = new CircuitBuilder(file, main);
        for (int i = 0; i < inst.getEnds().size(); i++) {
            String port = Kinds.portName(inst, i);
            if (port.equals("a")) {
                b2.tunnel(a, 0, "pa");
            }
        }
        Component yOut = b2.output("py", 1, 700, 100);
        Component zOut = b2.output("pz", 1, 700, 300);
        b2.commit();
        Influence inf = Influence.of(main, List.of(a), Influence.Mode.FORWARD, false, -1);
        Influence.View v = inf.view(main);
        assertTrue(v.inside.containsKey(inst), v.inside.toString());
        assertEquals(1, v.inside.get(inst), "one NOT inside");
        boolean y = false;
        boolean z = false;
        for (Trace.Node n : inf.forward().nets) {
            if (n.instances.isEmpty()) {
                for (Netlist.PortRef p : n.net.ports()) {
                    y |= p.component == yOut;
                    z |= p.component == zOut;
                }
            }
        }
        assertTrue(y, "out through y");
        assertFalse(z, "never out through z (not connected to a inside)");
        // 서브회로 안을 보면 그 안의 경로
        Influence.View inside = inf.view(sub);
        assertEquals(1, inside.forwardParts.stream().filter(x -> x == na).count());
        // 경계 핀도 닿은 부품이다(흐리게 두면 경로가 경계에서 끊긴 것처럼 보인다)
        assertTrue(inside.forwardParts.stream().anyMatch(x -> "a".equals(Names.label(x))), "input pin a");
        assertFalse(inside.forwardParts.contains(nb));
    }

    /** 조합 고리: OR 출력이 NOT을 거쳐 OR 입력으로 돌아온다. 끝나고, 고리 닫힘을 적는다. */
    @Test
    void combinationalLoopEnds() throws Exception {
        CircuitBuilder b = fresh();
        Component a = b.input("A", 1, 100, 100);
        Component or = b.add("Gates", "OR Gate", 300, 100, "label", "o");
        b.tunnel(or, 1, "A");
        b.tunnel(or, 2, "back");
        b.tunnel(or, 0, "x");
        Component not = b.add("Gates", "NOT Gate", 500, 100, "label", "nx");
        b.tunnel(not, 1, "x");
        b.tunnel(not, 0, "back");
        b.commit();
        Circuit main = file.getMainCircuit();
        Influence inf = Influence.of(main, List.of(a), Influence.Mode.FORWARD, false, -1);
        assertEquals(Set.of("nx", "o"), names(inf.view(main).forwardParts));
        assertFalse(inf.forward().loops.isEmpty(), "loop closure recorded");
    }

    @Test
    void sameInputGivesTheSameStepsEveryTime() throws Exception {
        CircuitBuilder b = fresh();
        Component[] c = chain(b);
        Circuit main = file.getMainCircuit();
        String first = Influence.of(main, List.of(c[0]), Influence.Mode.BOTH, true, -1).forward().steps.toString();
        for (int i = 0; i < 100; i++) {
            assertEquals(first, Influence.of(main, List.of(c[0]), Influence.Mode.BOTH, true, -1).forward().steps
                    .toString());
        }
    }
}
