/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #72, #73: 우클릭 메뉴의 회로 변경이 연결을 지키는지 넷 모델로 검사한다. */
class CircuitEditsTest {
    @TempDir
    Path tmp;

    static Component find(Circuit c, String factory) {
        for (Component comp : c.getNonWires()) {
            if (comp.getFactory().getName().equals(factory)) {
                return comp;
            }
        }
        return null;
    }

    static Component tunnel(Circuit c, String label) {
        for (Component comp : c.getNonWires()) {
            if (comp.getFactory().getName().equals("Tunnel") && label.equals(Names.label(comp))) {
                return comp;
            }
        }
        return null;
    }

    /** 게이트 종류 바꾸기: 모든 종류 쌍 × 입력 수 × 크기 × 방향에서 입력·출력 연결이 그대로다. */
    @Test
    void swappingGateKindKeepsEveryConnection() throws Exception {
        String[] kinds = CircuitEdits.swappableGates();
        int cases = 0;
        for (String from : kinds) {
            for (String to : kinds) {
                if (from.equals(to)) {
                    continue;
                }
                for (int inputs : new int[] {2, 3, 5}) {
                    for (String size : new String[] {"30", "50", "70"}) {
                        for (Direction facing : new Direction[] {Direction.EAST, Direction.WEST, Direction.NORTH,
                                Direction.SOUTH}) {
                            check(from, to, inputs, size, facing);
                            cases++;
                        }
                    }
                }
            }
        }
        assertEquals(30 * 3 * 3 * 4, cases);
    }

    void check(String from, String to, int inputs, String size, Direction facing) throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component gate = b.add("Gates", from, 400, 400, "inputs", Integer.toString(inputs), "size", size,
                "facing", facing.toString());
        for (int i = 1; i < gate.getEnds().size(); i++) {
            b.tunnel(gate, i, "in" + i);
        }
        Location out = gate.getEnds().get(0).getLocation();
        Location far = out.translate(facing, 60);
        b.wire(out, far);
        b.add("Wiring", "Tunnel", far.getX(), far.getY(), "label", "out", "facing", facing.reverse().toString());
        b.commit();
        Circuit main = file.getMainCircuit();

        CircuitEdits.swapGate(main, gate, CircuitEdits.builtin(file, "Gates", to)).execute();
        Component now = find(main, to);
        String where = from + "→" + to + " n=" + inputs + " size=" + size + " " + facing;
        assertNotNull(now, where);
        Netlist nl = Netlist.of(main);
        for (int i = 1; i < now.getEnds().size(); i++) {
            assertSame(nl.netOf(tunnel(main, "in" + i), 0), nl.netOf(now, i), where + " input " + i);
        }
        assertSame(nl.netOf(tunnel(main, "out"), 0), nl.netOf(now, 0), where + " output");
    }

    @Test
    void attachedPartsSitOnThePort() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component reg = b.add("Memory", "Register", 400, 300, "width", "8", "label", "PC");
        b.commit();
        Circuit main = file.getMainCircuit();
        for (CircuitEdits.Attach what : CircuitEdits.Attach.values()) {
            int end = what == CircuitEdits.Attach.CONSTANT ? 1 : 0; // 상수는 입력 D에, 나머지는 출력 Q에
            CircuitEdits.attach(file, main, reg, end, what, CircuitEdits.portLabel(reg, end)).execute();
        }
        Netlist nl = Netlist.of(main);
        Component pin = find(main, "Pin");
        assertSame(nl.netOf(reg, 0), nl.netOf(pin, 0));
        assertEquals("Q", Names.label(pin));
        assertEquals(8, pin.getEnds().get(0).getWidth().getWidth());
        assertTrue(pin.getEnds().get(0).isInput(), "an output pin reads the register's Q");
        assertSame(nl.netOf(reg, 1), nl.netOf(find(main, "Constant"), 0));
        assertSame(nl.netOf(reg, 0), nl.netOf(find(main, "Probe"), 0));
        assertSame(nl.netOf(reg, 0), nl.netOf(find(main, "Tunnel"), 0));
        assertEquals(Direction.EAST, CircuitEdits.side(reg, 0));
        assertEquals(Direction.WEST, CircuitEdits.side(reg, 1));
    }

    static Set<String> netSignature(Circuit c) {
        Set<String> ret = new TreeSet<>();
        Netlist nl = Netlist.of(c);
        for (Netlist.Net n : nl.nets()) {
            Set<String> ports = new TreeSet<>();
            for (Netlist.PortRef p : n.ports()) {
                if (!p.component.getFactory().getName().equals("Tunnel")) {
                    ports.add(Names.port(c, p.component, p.end));
                }
            }
            if (!ports.isEmpty()) {
                ret.add(ports.toString());
            }
        }
        return ret;
    }

    @Test
    void wireToTunnelsKeepsTheNet() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        b.add("Wiring", "Pin", 100, 200, "label", "a");
        b.add("Wiring", "Pin", 300, 200, "output", "true", "facing", "west", "label", "y");
        b.wire(Location.create(100, 200), Location.create(300, 200));
        b.commit();
        Circuit main = file.getMainCircuit();
        Set<String> before = netSignature(main);
        Wire w = main.getWires().iterator().next();
        CircuitEdits.wireToTunnels(file, main, w, "sig", 1).execute();
        assertTrue(main.getWires().isEmpty());
        assertEquals(before, netSignature(main));
        assertEquals(2, countTunnels(main));
    }

    static int countTunnels(Circuit c) {
        int n = 0;
        for (Component comp : c.getNonWires()) {
            if (comp.getFactory().getName().equals("Tunnel")) {
                n++;
            }
        }
        return n;
    }

    @Test
    void attributeChangesApplyToEveryChosenComponent() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        List<Component> gates = new ArrayList<>();
        gates.add(b.add("Gates", "AND Gate", 200, 200));
        gates.add(b.add("Gates", "OR Gate", 200, 400));
        gates.add(b.add("Wiring", "Tunnel", 500, 500, "label", "t"));
        b.commit();
        Circuit main = file.getMainCircuit();
        CircuitEdits.setAttribute(main, gates, "width", "8").execute();
        Map<String, Integer> widths = new HashMap<>();
        for (Component c : main.getNonWires()) {
            widths.put(c.getFactory().getName(), c.getEnds().get(0).getWidth().getWidth());
        }
        assertEquals(8, (int) widths.get("AND Gate"));
        assertEquals(8, (int) widths.get("OR Gate"));
        assertEquals(8, (int) widths.get("Tunnel"));
    }
}
