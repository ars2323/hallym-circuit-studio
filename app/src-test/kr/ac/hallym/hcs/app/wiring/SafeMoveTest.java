/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.move.MoveGesture;
import com.cburch.logisim.tools.move.MoveResult;

import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #81: 따라오는 배선·선분 평행 이동은 다른 넷을 바꾸지 않고, 새 선은 부록 A.4 규칙을 지키며, 되돌리기 한 번에 취소된다. */
class SafeMoveTest {
    @TempDir
    Path tmp;

    Project proj;
    Selection sel;

    LogisimFile fresh() throws Exception {
        return CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
    }

    void open(LogisimFile f) {
        proj = new Project(f);
        Canvas canvas = new Canvas(proj);
        sel = canvas.getSelection();
    }

    /** 선택을 옮긴다: 원조처럼 연결 유지 계산을 하고 SafeMove에 맡긴다. */
    SafeMove.Outcome move(Collection<Component> comps, int dx, int dy) {
        sel = new Canvas(proj).getSelection(); // 빈 선택에서 시작
        sel.addAll(comps);
        MoveGesture g = new MoveGesture((gesture, x, y) -> { }, proj.getCurrentCircuit(),
                sel.getAnchoredComponents());
        MoveResult r = g.forceRequest(dx, dy);
        return SafeMove.move(proj, sel, dx, dy, r);
    }

    /** 포트를 이름@위치로 적은 넷리스트(옮긴 부품은 옮기기 전 위치로 되돌려 적는다). */
    static Set<Set<String>> netlist(Circuit c, Collection<Component> moved, int dx, int dy) {
        Set<Set<String>> ret = new HashSet<>();
        for (Netlist.Net n : Netlist.of(c).nets()) {
            Set<String> ports = new TreeSet<>();
            for (Netlist.PortRef p : n.ports()) {
                Location at = p.component.getLocation();
                if (moved.contains(p.component)) {
                    at = at.translate(-dx, -dy);
                }
                ports.add(p.component.getFactory().getName() + at + "#" + p.end);
            }
            ret.add(ports);
        }
        return ret;
    }

    static Set<String> geometry(Circuit c) {
        Set<String> ret = new TreeSet<>();
        for (Wire w : c.getWires()) {
            ret.add("W" + w.getEnd0() + w.getEnd1());
        }
        for (Component x : c.getNonWires()) {
            ret.add(x.getFactory().getName() + x.getLocation());
        }
        return ret;
    }

    /** 새로 생긴 선(옮기기 전에 없던 것). */
    static List<Wire> newWires(Circuit c, Set<String> before) {
        List<Wire> ret = new ArrayList<>();
        for (Wire w : c.getWires()) {
            if (!before.contains("W" + w.getEnd0() + w.getEnd1())) {
                ret.add(w);
            }
        }
        return ret;
    }

    /** 공통 확인: 옮긴 부품을 뺀 넷리스트 그대로, 새 선은 A.4 규칙, 되돌리기 한 번에 원래대로. */
    void checkMove(Collection<Component> comps, int dx, int dy) {
        Circuit c = proj.getCurrentCircuit();
        Set<Set<Netlist.PortRef>> others = NetSignature.of(c, comps);
        Set<String> geo = geometry(c);
        SafeMove.Outcome o = move(comps, dx, dy);
        assertEquals(others, NetSignature.of(c, sel.getComponents()), "other nets unchanged (" + o + ")");
        assertEquals(new ArrayList<String>(), WireRules.violations(c, newWires(c, geo)), "A.4 rules (" + o + ")");
        if (o != SafeMove.Outcome.REFUSED) {
            assertNotEquals(geo, geometry(c));
            proj.undoAction();
        }
        assertEquals(geo, geometry(c), "one undo restores the move and the wires");
    }

    // ---- 규칙 검사기 ----

    @Test
    void wireRulesFindEachViolation() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.wire(Location.create(100, 100), Location.create(300, 100)); // 가로
        b.wire(Location.create(200, 0), Location.create(200, 200)); // 세로: 교차점 (200,100), 끝점 없음
        Component not = b.add("Gates", "NOT Gate", 400, 300);
        b.commit();
        Circuit c = f.getMainCircuit();
        assertEquals(List.of(), WireRules.violations(c, List.of(Wire.create(Location.create(260, 20),
                Location.create(260, 100)))), "T onto the middle of a wire is fine");
        assertEquals(List.of(), WireRules.violations(c, List.of(Wire.create(Location.create(240, 160),
                Location.create(240, 280)))), "a free wire is fine");
        List<String> v = WireRules.violations(c, List.of(Wire.create(Location.create(200, 100),
                Location.create(260, 100))));
        assertTrue(v.stream().anyMatch(s -> s.startsWith("endpoint on a crossing")), v.toString());
        Location in = not.getEnd(1).getLocation();
        v = WireRules.violations(c, List.of(Wire.create(in.translate(-30, 0), in.translate(30, 0))));
        assertTrue(v.stream().anyMatch(s -> s.startsWith("passes over a port")), v.toString());
        v = WireRules.violations(c, List.of(Wire.create(Location.create(250, 100), Location.create(400, 100))));
        assertTrue(v.stream().anyMatch(s -> s.startsWith("overlaps")), v.toString());
    }

    // ---- 이동 시나리오: 다른 넷 불변, A.4, 되돌리기 한 번 ----

    /** 핀 A → NOT 게이트 G → 출력 핀. 그 사이를 다른 넷(핀 B → 출력 C)의 세로선이 끝점 없이 가로지른다. */
    Component gateAcrossAnotherNet(CircuitBuilder b) {
        Component g = b.add("Gates", "NOT Gate", 300, 200);
        Location in = g.getEnd(1).getLocation();
        b.add("Wiring", "Pin", 100, in.getY(), "label", "A");
        b.wire(Location.create(100, in.getY()), in);
        b.add("Wiring", "Pin", 360, 200, "facing", "west", "output", "true", "label", "Y");
        b.wire(g.getEnd(0).getLocation(), Location.create(360, 200));
        b.add("Wiring", "Pin", 180, 60, "facing", "south", "label", "B");
        b.wire(Location.create(180, 60), Location.create(180, 340));
        b.add("Wiring", "Pin", 180, 340, "facing", "north", "output", "true", "label", "C");
        return g;
    }

    @Test
    void movedWiresMayCrossAnotherNetButNeverJoinIt() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component g = gateAcrossAnotherNet(b);
        b.commit();
        open(f);
        checkMove(List.of(g), 0, 100); // 선이 따라오며 B–C 세로선 쪽으로
        checkMove(List.of(g), -60, 60);
        checkMove(List.of(g), 0, -120);
    }

    @Test
    void followingWiresMustNotRunOverAnotherPort() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component g = gateAcrossAnotherNet(b);
        // 게이트를 아래로 옮기면 가장 짧은 길목에 다른 NOT 게이트 D의 입력 포트가 있다
        Component d = b.add("Gates", "NOT Gate", 300, 300);
        b.add("Wiring", "Pin", 360, 300, "facing", "west", "output", "true", "label", "Z");
        b.wire(d.getEnd(0).getLocation(), Location.create(360, 300));
        b.commit();
        open(f);
        checkMove(List.of(g), 0, 100);
        checkMove(List.of(g), 0, 140);
    }

    @Test
    void wireMovedOntoAnotherNetsLineIsRefused() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        b.add("Wiring", "Pin", 100, 100, "label", "P");
        b.wire(Location.create(100, 100), Location.create(300, 100));
        b.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "Q");
        b.add("Wiring", "Pin", 150, 150, "label", "R");
        b.wire(Location.create(150, 150), Location.create(250, 150));
        b.add("Wiring", "Pin", 250, 150, "facing", "west", "output", "true", "label", "S");
        b.commit();
        open(f);
        Wire lower = null;
        for (Wire w : f.getMainCircuit().getWires()) {
            if (w.getEnd0().getY() == 150) {
                lower = w;
            }
        }
        Circuit c = f.getMainCircuit();
        Set<String> geo = geometry(c);
        Set<Set<Netlist.PortRef>> all = NetSignature.of(c, List.of());
        assertEquals(SafeMove.Outcome.REFUSED, move(List.of(lower), 0, -50), "would lie on P–Q's line");
        assertEquals(geo, geometry(c));
        assertEquals(all, NetSignature.of(c, List.of()));
        assertTrue(kr.ac.hallym.hcs.app.sim.SimControls.lastNotice(proj) != null, "one line in the status bar");
    }

    /** P ─ 가로 다리 ─ 세로 선분 ─ 가로 다리 ─ Q(ㄹ자). */
    Wire zRoute(CircuitBuilder b) {
        b.add("Wiring", "Pin", 100, 100, "label", "P");
        b.wire(Location.create(100, 100), Location.create(200, 100));
        b.wire(Location.create(200, 100), Location.create(200, 200));
        b.wire(Location.create(200, 200), Location.create(300, 200));
        b.add("Wiring", "Pin", 300, 200, "facing", "west", "output", "true", "label", "Q");
        return null;
    }

    Wire middle(Circuit c) {
        for (Wire w : c.getWires()) {
            if (w.isVertical() && w.getEnd0().getX() == 200) {
                return w;
            }
        }
        throw new AssertionError("no middle segment");
    }

    @Test
    void draggingTheMiddleSegmentStretchesBothLegs() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        zRoute(b);
        b.commit();
        open(f);
        Circuit c = f.getMainCircuit();
        Set<String> geo = geometry(c);
        Set<Set<Netlist.PortRef>> all = NetSignature.of(c, List.of());
        assertEquals(SafeMove.Outcome.MOVED, move(List.of(middle(c)), 40, 0));
        assertTrue(geometry(c).containsAll(List.of("W" + Location.create(100, 100) + Location.create(240, 100),
                "W" + Location.create(240, 100) + Location.create(240, 200),
                "W" + Location.create(240, 200) + Location.create(300, 200))), geometry(c).toString());
        assertEquals(3, c.getWires().size(), "legs follow; no extra pieces");
        assertEquals(all, NetSignature.of(c, List.of()));
        proj.undoAction();
        assertEquals(geo, geometry(c), "one undo restores segment and legs");
    }

    @Test
    void bendThatWouldLandOnAnotherNetIsNotKept() throws Exception {
        LogisimFile f = fresh();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        zRoute(b);
        // 다른 넷의 세로선이 x=240을 지난다: 선분을 옮기면 꺾임이 그 위에 생긴다
        b.add("Wiring", "Pin", 240, 20, "facing", "south", "label", "U");
        b.wire(Location.create(240, 20), Location.create(240, 160));
        b.add("Wiring", "Pin", 240, 160, "facing", "north", "output", "true", "label", "V");
        b.commit();
        open(f);
        checkMove(List.of(middle(f.getMainCircuit())), 40, 0);
    }

    // ---- 데모 회로 ----

    @Test
    void movingDemoComponentsKeepsTheNetlist() throws Exception {
        Path dir = Files.createTempDirectory(tmp, "demo");
        Files.copy(new File(System.getProperty("hcs.mipsJar")).toPath(), dir.resolve("hcs-mips.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        Path circ = dir.resolve("demo-datapath.circ");
        Files.copy(new File(System.getProperty("hcs.circDir"), "demo-datapath.circ").toPath(), circ);
        LogisimFile f = new Loader(null).openLogisimFile(circ.toFile());
        open(f);
        Circuit c = f.getMainCircuit();
        int moved = 0;
        for (String label : new String[] {"PC", "halt", "Zero"}) {
            Component x = byLabel(c, label);
            for (int[] d : new int[][] {{0, 20}, {20, 0}, {-20, -20}}) {
                Set<Set<String>> before = netlist(c, List.of(), 0, 0);
                SafeMove.Outcome o = move(List.of(x), d[0], d[1]);
                Component now = byLabel(c, label);
                if (o == SafeMove.Outcome.MOVED) {
                    Set<Set<String>> after = netlist(c, List.of(now), d[0], d[1]);
                    assertEquals(before, after, label + " moved by " + d[0] + ","
                            + d[1] + ": every net the same, its own connections kept");
                    moved++;
                } else {
                    // 선을 따라오게 할 수 없으면 원조처럼 선 없이 옮기거나 옮기지 않는다: 다른 넷은 그대로
                    String self = x.getFactory().getName() + x.getLocation() + "#";
                    boolean refused = o == SafeMove.Outcome.REFUSED;
                    Set<Set<String>> others = new HashSet<>();
                    for (Set<String> net : netlist(c, refused ? List.of() : List.of(now), refused ? 0 : d[0],
                            refused ? 0 : d[1])) {
                        Set<String> s = new TreeSet<>(net);
                        s.removeIf(k -> k.startsWith(self));
                        if (!s.isEmpty()) {
                            others.add(s);
                        }
                    }
                    Set<Set<String>> othersBefore = new HashSet<>();
                    for (Set<String> net : before) {
                        Set<String> s = new TreeSet<>(net);
                        s.removeIf(k -> k.startsWith(self));
                        if (!s.isEmpty()) {
                            othersBefore.add(s);
                        }
                    }
                    assertEquals(othersBefore, others, label + " fallback keeps the other nets");
                }
                x = now;
            }
        }
        assertTrue(moved >= 5, "most small moves keep the wires: " + moved);
        // 서브회로 인스턴스와 MIPS 부품도
        for (String factory : new String[] {"regfile", "Data Memory"}) {
            Component x = byFactory(c, factory);
            Set<Set<String>> before = netlist(c, List.of(), 0, 0);
            SafeMove.Outcome o = move(List.of(x), 0, 20);
            if (o == SafeMove.Outcome.MOVED) {
                assertEquals(before, netlist(c, List.of(byFactory(c, factory)), 0, 20), factory);
            }
        }
    }

    static Component byLabel(Circuit c, String label) {
        for (Component x : c.getNonWires()) {
            if (label.equals(kr.ac.hallym.hcs.app.model.Names.label(x))
                    && !x.getFactory().getName().equals("Tunnel")) {
                return x;
            }
        }
        throw new AssertionError(label);
    }

    static Component byFactory(Circuit c, String name) {
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals(name)) {
                return x;
            }
        }
        throw new AssertionError(name);
    }
}
