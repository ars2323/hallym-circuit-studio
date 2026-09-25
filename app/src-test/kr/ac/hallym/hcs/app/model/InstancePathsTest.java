/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** P-02: 인스턴스 경로, 실행 중 상태로 가기, 핀 변경으로 끊긴 연결과 되살리기. */
class InstancePathsTest {
    @TempDir
    Path tmp;

    LogisimFile file;
    Circuit blk;
    Circuit mid;
    Component inst1;
    Component inst2;
    Component midInst;

    /** blk: 입력 a, b → AND → 출력 y. mid 안에 blk 하나, main 안에 blk 둘과 mid 하나. 포트는 선으로 잇는다. */
    void build() throws Exception {
        file = CircuitBuilder.newFile(new Loader(null), Files.createTempDirectory(tmp, "f").toFile());
        blk = new Circuit("blk");
        file.addCircuit(blk);
        CircuitBuilder sb = new CircuitBuilder(file, blk);
        sb.add("Wiring", "Pin", 100, 100, "label", "a");
        sb.add("Wiring", "Pin", 100, 200, "label", "b");
        Component and = sb.add("Gates", "AND Gate", 300, 150, "inputs", "2");
        sb.wire(Location.create(100, 100), Location.create(200, 100));
        sb.wire(Location.create(200, 100), Location.create(200, and.getEnd(1).getLocation().getY()));
        sb.wire(Location.create(200, and.getEnd(1).getLocation().getY()), and.getEnd(1).getLocation());
        sb.wire(Location.create(100, 200), Location.create(220, 200));
        sb.wire(Location.create(220, 200), Location.create(220, and.getEnd(2).getLocation().getY()));
        sb.wire(Location.create(220, and.getEnd(2).getLocation().getY()), and.getEnd(2).getLocation());
        sb.add("Wiring", "Pin", 400, 150, "facing", "west", "output", "true", "label", "y");
        sb.wire(and.getEnd(0).getLocation(), Location.create(400, 150));
        sb.commit();
        mid = new Circuit("mid");
        file.addCircuit(mid);
        CircuitBuilder mb = new CircuitBuilder(file, mid);
        midInst = mb.addSubcircuit(blk, 300, 200);
        mb.commit();
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        inst1 = b.addSubcircuit(blk, 300, 100);
        inst2 = b.addSubcircuit(blk, 300, 400);
        b.addSubcircuit(mid, 600, 300);
        b.commit();
        // main의 inst1 포트마다 선을 하나씩(왼쪽 입력은 왼쪽으로, 오른쪽 출력은 오른쪽으로)
        CircuitMutation m = new CircuitMutation(file.getMainCircuit());
        for (int i = 0; i < inst1.getEnds().size(); i++) {
            Location p = inst1.getEnd(i).getLocation();
            boolean out = inst1.getEnd(i).isOutput();
            m.add(com.cburch.logisim.circuit.Wire.create(p, p.translate(out ? 40 : -40, 0)));
        }
        m.execute();
    }

    @Test
    void pathsFromMainAreDeterministic() throws Exception {
        build();
        Circuit main = file.getMainCircuit();
        List<List<Component>> p = InstancePaths.paths(main, blk);
        assertEquals(3, p.size(), "two in main, one inside mid");
        // 위치 순(y, x): inst1(y 100), mid(y 300) 안의 blk, inst2(y 400)
        assertEquals(List.of(inst1), p.get(0));
        assertEquals(2, p.get(1).size());
        assertSame(midInst, p.get(1).get(1));
        assertEquals(List.of(inst2), p.get(2));
        assertEquals("main › blk", InstancePaths.describe(main, p.get(0)));
        assertEquals("main › mid › blk", InstancePaths.describe(main, p.get(1)));
        assertEquals(p.toString(), InstancePaths.paths(main, blk).toString());
        assertTrue(InstancePaths.paths(main, main).isEmpty());
    }

    @Test
    void goingToAnInstanceGivesTheRunningSubstate() throws Exception {
        build();
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        Circuit main = file.getMainCircuit();
        CircuitState top = proj.getCircuitState(main);
        CircuitState s = InstancePaths.stateFor(top, InstancePaths.paths(main, blk).get(1));
        assertNotNull(s);
        assertSame(blk, s.getCircuit());
        assertSame(mid, s.getParentState().getCircuit());
        assertSame(top, s.getParentState().getParentState(), "hangs under main's running state");
        // 탐색기에서 연 것(자기만의 상태)은 부모가 없다
        assertNull(proj.getCircuitState(blk).getParentState());
    }

    @Test
    void previewCountsTheConnectionsOfTheSelectedPin() throws Exception {
        build();
        Component a = null;
        for (Component c : blk.getNonWires()) {
            if ("a".equals(Names.label(c))) {
                a = c;
            }
        }
        List<InstancePaths.PortUse> affected = InstancePaths.affected(file, blk, List.of(a));
        assertEquals(1, affected.size(), "only inst1's a-port has a wire");
        assertEquals(1, InstancePaths.instances(affected));
    }

    @Test
    void addingAPinBreaksShiftedPortsAndReconnectingKeepsThem() throws Exception {
        build();
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        List<InstancePaths.PortUse> before = InstancePaths.snapshot(file, blk);
        long connectedBefore = before.stream().filter(u -> u.connected).count();
        assertEquals(3, connectedBefore, "inst1: a, b, y");
        // 핀 c를 더한다: 기본 모양의 상자가 커지며 포트 자리가 바뀐다
        CircuitBuilder sb = new CircuitBuilder(file, blk);
        sb.add("Wiring", "Pin", 100, 300, "label", "c");
        sb.commit();
        List<InstancePaths.Broken> broken = InstancePaths.broken(before, InstancePaths.snapshot(file, blk));
        assertTrue(!broken.isEmpty(), "some ports of inst1 moved away from their wires");
        for (InstancePaths.Broken x : broken) {
            assertNotNull(x.now, "no pin was deleted");
            assertTrue(InstancePaths.wireEndsAt(x.before.parent, x.before.at), "the old wire end is still there");
        }
        // 되살리기: 새 포트 자리에서 옛 선 끝까지 잇는다(WireGuard 검사)
        int kept = kr.ac.hallym.hcs.app.instance.InstanceBanner.reconnect(proj, broken);
        assertEquals(broken.size(), kept);
        List<InstancePaths.PortUse> after = InstancePaths.snapshot(file, blk);
        for (InstancePaths.PortUse u : after) {
            if (u.instance == inst1 && !"c".equals(Names.label(u.pin))) {
                assertTrue(u.connected, "reconnected: " + Names.label(u.pin));
            }
        }
        assertTrue(InstancePaths.broken(before, after).isEmpty());
    }

    @Test
    void deletingAPinIsReportedButNotReconnected() throws Exception {
        build();
        List<InstancePaths.PortUse> before = InstancePaths.snapshot(file, blk);
        Component b = null;
        for (Component c : blk.getNonWires()) {
            if ("b".equals(Names.label(c))) {
                b = c;
            }
        }
        CircuitMutation m = new CircuitMutation(blk);
        m.remove(b);
        m.execute();
        List<InstancePaths.Broken> broken = InstancePaths.broken(before, InstancePaths.snapshot(file, blk));
        assertTrue(broken.stream().anyMatch(x -> x.now == null && "b".equals(Names.label(x.before.pin))),
                "the b connection is gone");
    }
}
