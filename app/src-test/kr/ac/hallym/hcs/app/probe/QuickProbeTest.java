/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
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

import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #75: 빠른 프로브는 어떤 넷도 합치거나 끊지 않고, 교차점·남의 선·포트를 피한다. */
class QuickProbeTest {
    @TempDir
    Path tmp;

    LogisimFile file;
    CircuitBuilder b;

    void start() throws Exception {
        file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        b = new CircuitBuilder(file, file.getMainCircuit());
    }

    Wire line(int x0, int y0, int x1, int y1) {
        b.wire(Location.create(x0, y0), Location.create(x1, y1));
        return null;
    }

    static Wire wireAt(Circuit c, Location a, Location z) {
        for (Wire w : c.getWires()) {
            if (w.getEnd0().equals(a) && w.getEnd1().equals(z)) {
                return w;
            }
        }
        return null;
    }

    /** 프로브를 뺀 넷 모양(포트 이름 집합들). */
    static Set<String> signature(Circuit c) {
        Set<String> ret = new TreeSet<>();
        for (Netlist.Net n : Netlist.of(c).nets()) {
            Set<String> ports = new TreeSet<>();
            for (Netlist.PortRef p : n.ports()) {
                if (!p.component.getFactory().getName().equals("Probe")) {
                    ports.add(Names.port(c, p.component, p.end) + "@" + p.location());
                }
            }
            if (!ports.isEmpty()) {
                ret.add(ports.toString());
            }
        }
        return ret;
    }

    /** 원조는 선 중간에 닿은 짧은 선 때문에 원래 선을 둘로 나누므로, 넷은 source(핀)로 찾는다. */
    Component placeAndCheck(Wire w, Location near, Component source) {
        Circuit c = file.getMainCircuit();
        Set<String> before = signature(c);
        QuickProbe.Placement pl = QuickProbe.find(file, c, w, near);
        assertNotNull(pl, "a spot must be found");
        QuickProbe.place(file, c, pl, "16", "sig").execute();
        assertEquals(before, signature(c), "no net merged or cut");
        Component probe = QuickProbe.probes(c).get(0);
        Netlist nl = Netlist.of(c);
        assertSame(nl.netOf(source, 0), nl.netOf(probe, 0), "the probe reads the wire's net");
        return probe;
    }

    @Test
    void probeJoinsOnlyItsWire() throws Exception {
        start();
        Component a = b.add("Wiring", "Pin", 100, 200, "width", "8", "label", "a");
        b.add("Wiring", "Pin", 400, 200, "width", "8", "output", "true", "facing", "west", "label", "y");
        line(100, 200, 400, 200);
        b.commit();
        Circuit c = file.getMainCircuit();
        Wire w = wireAt(c, Location.create(100, 200), Location.create(400, 200));
        assertEquals("a", QuickProbe.netName(c, Netlist.of(c).netOf(w)));
        Component probe = placeAndCheck(w, Location.create(250, 200), a);
        assertEquals("sig", Names.label(probe));
    }

    @Test
    void avoidsANeighbouringWireAndACrossing() throws Exception {
        start();
        Component a = b.add("Wiring", "Pin", 100, 200, "label", "a");
        b.add("Wiring", "Pin", 100, 180, "label", "other");
        line(100, 200, 400, 200);
        line(100, 180, 400, 180); // 다른 넷이 바로 위(20px)에
        b.add("Wiring", "Pin", 250, 100, "label", "cross", "facing", "south");
        line(250, 100, 250, 300); // 다른 넷이 x=250에서 교차(끝점 없음)
        b.commit();
        Circuit c = file.getMainCircuit();
        Wire w = wireAt(c, Location.create(100, 200), Location.create(400, 200));
        QuickProbe.Placement pl = QuickProbe.find(file, c, w, Location.create(250, 200));
        assertNotNull(pl);
        assertFalse(pl.p.equals(Location.create(250, 200)), "not on the crossing");
        assertTrue(pl.q.getY() > 200, "goes below, away from the other wire above");
        placeAndCheck(w, Location.create(250, 200), a);
    }

    @Test
    void givesUpWhenThereIsNoRoom() throws Exception {
        start();
        b.add("Wiring", "Pin", 100, 200, "label", "a");
        line(100, 200, 200, 200);
        int n = 0;
        for (int d : new int[] {20, 30, 40, 50}) {
            for (int sgn : new int[] {-1, 1}) {
                b.add("Wiring", "Pin", 90, 200 + sgn * d, "label", "o" + n++);
                line(90, 200 + sgn * d, 210, 200 + sgn * d);
            }
        }
        b.commit();
        Circuit c = file.getMainCircuit();
        Wire w = wireAt(c, Location.create(100, 200), Location.create(200, 200));
        assertNull(QuickProbe.find(file, c, w, Location.create(150, 200)));
    }
}
