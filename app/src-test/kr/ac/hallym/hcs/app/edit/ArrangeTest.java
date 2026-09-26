/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** E-01·E-02: 라벨 번호, N개 복제(검사 통과·닿으면 거절), 정렬, 같은 간격, 이어진 부품은 옮기지 않음, 선택 필터. */
class ArrangeTest {
    @TempDir
    Path tmp;

    @Test
    void labelNumbers() {
        assertEquals("R1", Arrange.nextLabel("R0", 1));
        assertEquals("R31", Arrange.nextLabel("R0", 31));
        assertEquals("R08", Arrange.nextLabel("R07", 1));
        assertEquals("$t2", Arrange.nextLabel("$t0", 2));
        assertEquals("acc3", Arrange.nextLabel("acc", 3));
        assertEquals("", Arrange.nextLabel("", 1));
    }

    @Test
    void duplicateNCopiesWithNumberedLabels() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component r0 = b.add("Memory", "Register", 300, 200, "width", "32", "label", "R0");
        b.commit();
        Project proj = new Project(f);
        Circuit c = f.getMainCircuit();
        List<Component> made = new ArrayList<>();
        int spacing = Arrange.defaultSpacing(Arrays.asList(r0), Arrange.Dir.DOWN);
        CircuitMutation m = Arrange.copies(c, Arrays.asList(r0), 3, Arrange.Dir.DOWN, spacing, true, made);
        assertTrue(ArrangeActions.apply(proj, c, m, made, "dup"));
        Set<String> labels = new TreeSet<>();
        for (Component x : c.getNonWires()) {
            labels.add(Names.label(x));
        }
        assertEquals(new TreeSet<>(Arrays.asList("R0", "R1", "R2", "R3")), labels);
        assertEquals(r0.getLocation().translate(0, 3 * spacing), made.get(2).getLocation());
        proj.undoAction();
        assertEquals(1, c.getNonWires().size(), "one undo removes all copies");
    }

    @Test
    void copiesThatWouldTouchAWireAreRefused() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component k = b.add("Wiring", "Constant", 200, 100);
        // 사본의 출력 포트(200, 150)을 지나는 선
        b.wire(Location.create(150, 150), Location.create(250, 150));
        b.commit();
        Project proj = new Project(f);
        Circuit c = f.getMainCircuit();
        List<Component> made = new ArrayList<>();
        CircuitMutation m = Arrange.copies(c, Arrays.asList(k), 1, Arrange.Dir.DOWN, 50, false, made);
        assertFalse(ArrangeActions.apply(proj, c, m, made, "dup"));
        assertEquals(1, c.getNonWires().size());
    }

    @Test
    void alignAndDistributeLooseParts() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component a = b.add("Gates", "NOT Gate", 300, 100);
        Component c1 = b.add("Gates", "NOT Gate", 340, 200);
        Component c2 = b.add("Gates", "NOT Gate", 280, 330);
        b.commit();
        Project proj = new Project(f);
        Circuit c = f.getMainCircuit();
        List<Component> out = new ArrayList<>();
        CircuitMutation m = Arrange.align(c, Arrays.asList(a, c1, c2), Arrange.Align.LEFT, out);
        assertTrue(ArrangeActions.apply(proj, c, m, out, "align"));
        Set<Integer> lefts = new TreeSet<>();
        for (Component x : c.getNonWires()) {
            lefts.add(x.getBounds().getX());
        }
        assertEquals(1, lefts.size(), "one left edge");
        // 같은 간격(세로): 위아래 부품은 두고 가운데를 옮긴다
        List<Component> now = new ArrayList<>(c.getNonWires());
        out.clear();
        m = Arrange.distribute(c, now, false, out);
        assertTrue(ArrangeActions.apply(proj, c, m, out, "dist"));
        List<Integer> tops = new ArrayList<>();
        for (Component x : c.getNonWires()) {
            tops.add(x.getBounds().getY());
        }
        java.util.Collections.sort(tops);
        // 격자(10) 위에서 가장 같게: 차이는 한 칸 이내
        assertTrue(Math.abs((tops.get(1) - tops.get(0)) - (tops.get(2) - tops.get(1))) <= 10, "equal gaps " + tops);
    }

    @Test
    void connectedPartsStayAndTheFilterKeepsOneKind() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component a = b.add("Gates", "NOT Gate", 300, 100);
        Component d = b.add("Gates", "NOT Gate", 360, 200);
        b.wire(a.getEnd(0).getLocation(), a.getEnd(0).getLocation().translate(40, 0));
        b.commit();
        Circuit c = f.getMainCircuit();
        assertTrue(Arrange.connected(c, a));
        assertFalse(Arrange.connected(c, d));
        Project proj = new Project(f);
        ArrangeActions.align(proj, c, Arrays.asList(a, d), Arrange.Align.LEFT);
        assertEquals(360, c.getNonWires().stream().filter(x -> x != a && x.getFactory() == d.getFactory())
                .findFirst().get().getLocation().getX(), "nothing moved");
        assertTrue(kr.ac.hallym.hcs.app.sim.SimControls.lastNotice(proj).contains("NOT #1"),
                kr.ac.hallym.hcs.app.sim.SimControls.lastNotice(proj));
        // 선택 필터: 부품과 선을 함께 골랐을 때 한 종류만 남긴다
        List<Component> both = new ArrayList<>(c.getNonWires());
        both.addAll(c.getWires());
        assertEquals(2, ArrangeActions.keep(both, false).size(), "components only");
        assertTrue(ArrangeActions.keep(both, false).stream().noneMatch(x -> x instanceof com.cburch.logisim.circuit.Wire));
        assertEquals(1, ArrangeActions.keep(both, true).size(), "wires only");
        assertTrue(ArrangeActions.keep(both, true).get(0) instanceof com.cburch.logisim.circuit.Wire);
    }
}
