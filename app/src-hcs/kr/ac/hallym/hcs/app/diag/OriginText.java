/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.model.OriginTrace;

/** E·X 출처(D-01)를 말하는 문장과 강조할 자리. 학생이 붙인 이름으로, 사실과 위치까지만(PLAN.md 4.4). */
public final class OriginText {
    private OriginText() {
    }

    /** 같은 원인인지 가르는 열쇠: 까닭, 경로, 부품 또는 넷. */
    static String key(OriginTrace.Origin o) {
        StringBuilder sb = new StringBuilder(o.cause.name()).append('|');
        for (Component c : o.node.instances) {
            sb.append(System.identityHashCode(c)).append('/');
        }
        // 넷은 검사마다 다시 만들므로 객체가 아니라 회로와 가장 작은 자리로 가린다
        sb.append(o.component != null ? "c" + System.identityHashCode(o.component)
                : "n" + System.identityHashCode(o.node.circuit) + "@" + firstLocation(o.node.net));
        return sb.toString();
    }

    private static Location firstLocation(Netlist.Net n) {
        Location best = null;
        for (Netlist.PortRef p : n.ports()) {
            if (best == null || p.location().compareTo(best) < 0) {
                best = p.location();
            }
        }
        for (Wire w : n.wires()) {
            for (Location l : new Location[] {w.getEnd0(), w.getEnd1()}) {
                if (best == null || l.compareTo(best) < 0) {
                    best = l;
                }
            }
        }
        return best;
    }

    private static String where(Circuit top, OriginTrace.Origin o) {
        return InstancePaths.describe(top, o.node.instances);
    }

    private static String name(Circuit top, OriginTrace.Origin o, Component c) {
        return Names.path(where(top, o), Names.name(o.node.circuit, c));
    }

    /** 원인 넷을 가리키는 이름: 그 넷을 읽는 첫 포트(없으면 첫 포트). */
    private static String netName(Circuit top, OriginTrace.Origin o) {
        List<Netlist.PortRef> ports = new ArrayList<>(o.node.net.readers());
        if (ports.isEmpty()) {
            ports.addAll(o.node.net.ports());
        }
        ports.sort(java.util.Comparator.comparing(Netlist.PortRef::location));
        if (ports.isEmpty()) {
            return where(top, o);
        }
        Netlist.PortRef p = ports.get(0);
        return Names.path(where(top, o), Names.port(o.node.circuit, p.component, p.end));
    }

    /** 넷 하나를 가리키는 이름: 값을 내는 첫 포트, 없으면 읽는 첫 포트(학생이 붙인 이름으로). */
    static String netLabel(Circuit top, kr.ac.hallym.hcs.app.model.Trace.Node n) {
        List<Netlist.PortRef> ports = new ArrayList<>(n.net.drivers());
        if (ports.isEmpty()) {
            ports.addAll(n.net.readers());
        }
        if (ports.isEmpty()) {
            ports.addAll(n.net.ports());
        }
        String where = InstancePaths.describe(top, n.instances);
        if (ports.isEmpty()) {
            return where;
        }
        ports.sort(java.util.Comparator.comparing(Netlist.PortRef::location));
        Netlist.PortRef p = ports.get(0);
        return Names.path(where, Names.port(n.circuit, p.component, p.end));
    }

    static String valueName(Value v) {
        return hasError(v) ? "E" : "X";
    }

    /** 원인 한 곳의 문장(사실과 위치까지만, PLAN.md 4.4). */
    public static String cause(Circuit top, OriginTrace.Origin o) {
        switch (o.cause) {
        case COMPONENT:
        case STORED:
            return Messages.get("diag.cause.COMPONENT", name(top, o, o.component), valueName(o.value));
        case UNDRIVEN:
            return Messages.get("diag.cause.UNDRIVEN", netName(top, o));
        case CONFLICT:
            return Messages.get("diag.cause.CONFLICT", where(top, o), name(top, o, o.drivers.get(0)),
                    name(top, o, o.drivers.get(1)));
        case ALL_OFF: {
            List<String> ds = new ArrayList<>();
            for (Component d : o.drivers) {
                ds.add(Names.name(o.node.circuit, d));
            }
            return Messages.get("diag.cause.ALL_OFF", netName(top, o), String.join(", ", ds));
        }
        case INPUT_PIN:
            return Messages.get("diag.cause.INPUT_PIN", name(top, o, o.component));
        default:
            return Messages.get("diag.cause.LOOP", o.component == null ? where(top, o) : name(top, o, o.component));
        }
    }

    /**
     * 강조할 부품: 원인 부품 또는 구동자들. 구동자 없는 선이면 그 선에 닿은 부품들(떠 있는 입력을 가진 부품, 짝 없는
     * 터널)이다. 정적 진단과 같은 자리인지도 이것으로 가린다.
     */
    static List<Component> components(OriginTrace.Origin o) {
        List<Component> out = new ArrayList<>();
        if (o.component != null) {
            out.add(o.component);
        }
        out.addAll(o.drivers);
        if (out.isEmpty()) {
            for (Netlist.PortRef p : o.node.net.ports()) {
                if (!out.contains(p.component)) {
                    out.add(p.component);
                }
            }
        }
        return out;
    }

    static List<Wire> wires(OriginTrace.Origin o) {
        return o.component == null ? new ArrayList<>(o.node.net.wires()) : Collections.<Wire>emptyList();
    }

    static Location location(OriginTrace.Origin o) {
        if (o.component != null) {
            return o.component.getLocation();
        }
        if (!o.drivers.isEmpty()) {
            return o.drivers.get(0).getLocation();
        }
        return o.node.net.ports().isEmpty() ? o.node.net.wires().get(0).getEnd0() : o.node.net.ports().get(0).location();
    }

    static boolean hasError(Value v) {
        if (v == null) {
            return false;
        }
        for (Value b : v.getAll()) {
            if (b == Value.ERROR) {
                return true;
            }
        }
        return false;
    }
}
