/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.edit;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.model.Kinds;

/**
 * 우클릭 메뉴가 만드는 회로 변경(#72, #73). 모두 원조 {@link CircuitMutation} 하나라 되돌리기 한 번으로 취소된다.
 * 결과는 원조 부품과 원조 속성뿐이다. GUI 없이 만들고 넷 모델로 검사한다.
 */
public final class CircuitEdits {
    private CircuitEdits() {
    }

    /** 원조 기본 라이브러리 부품의 팩토리. */
    public static ComponentFactory builtin(LogisimFile file, String library, String name) {
        Library lib = file.getLoader().getBuiltin().getLibrary(library);
        return ((AddTool) lib.getTool(name)).getFactory();
    }

    @SuppressWarnings("unchecked")
    public static void set(AttributeSet as, String name, String value) {
        Attribute<Object> a = (Attribute<Object>) as.getAttribute(name);
        if (a != null) {
            as.setValue(a, a.parse(value));
        }
    }

    @SuppressWarnings("unchecked")
    static String get(AttributeSet as, Attribute<?> a) {
        Object v = as.getValue(a);
        return v == null ? null : ((Attribute<Object>) a).toStandardString(v);
    }

    /** 포트가 부품의 어느 쪽에 있는지(바깥을 향하는 방향). */
    public static Direction side(Component c, int end) {
        Location p = c.getEnds().get(end).getLocation();
        Bounds b = c.getBounds();
        int dl = Math.abs(p.getX() - b.getX());
        int dr = Math.abs(p.getX() - (b.getX() + b.getWidth()));
        int dt = Math.abs(p.getY() - b.getY());
        int db = Math.abs(p.getY() - (b.getY() + b.getHeight()));
        int min = Math.min(Math.min(dl, dr), Math.min(dt, db));
        if (min == dl) {
            return Direction.WEST;
        }
        if (min == dr) {
            return Direction.EAST;
        }
        return min == dt ? Direction.NORTH : Direction.SOUTH;
    }

    /** 포트에 붙일 수 있는 것. */
    public enum Attach { PIN, CONSTANT, PROBE, TUNNEL }

    /**
     * 포트 위에 핀·상수·프로브·터널을 붙인다. 붙인 부품의 연결점이 포트와 같은 점이고 몸체는 부품 바깥쪽이다.
     * 폭은 포트 폭, 핀·터널 라벨은 label(보통 포트 이름).
     */
    public static CircuitMutation attach(LogisimFile file, Circuit circuit, Component c, int end, Attach what,
            String label) {
        EndData e = c.getEnds().get(end);
        Location at = e.getLocation();
        Direction out = side(c, end);
        int width = e.getWidth().getWidth();
        ComponentFactory f;
        AttributeSet as;
        switch (what) {
        case PIN:
            f = builtin(file, "Wiring", "Pin");
            as = f.createAttributeSet();
            // 핀은 facing 쪽 끝이 연결점이다: 부품의 입력 포트에는 입력 핀, 출력 포트에는 출력 핀
            as.setValue(com.cburch.logisim.instance.StdAttr.FACING, out.reverse());
            set(as, "output", Boolean.toString(e.isOutput() && !e.isInput()));
            break;
        case CONSTANT:
            f = builtin(file, "Wiring", "Constant");
            as = f.createAttributeSet();
            as.setValue(com.cburch.logisim.instance.StdAttr.FACING, out.reverse());
            set(as, "value", "0x0");
            break;
        case PROBE:
            f = builtin(file, "Wiring", "Probe");
            as = f.createAttributeSet();
            as.setValue(com.cburch.logisim.instance.StdAttr.FACING, out.reverse());
            break;
        default:
            f = builtin(file, "Wiring", "Tunnel");
            as = f.createAttributeSet();
            as.setValue(com.cburch.logisim.instance.StdAttr.FACING, out.reverse());
            break;
        }
        set(as, "width", Integer.toString(width));
        if (label != null && what != Attach.CONSTANT) {
            set(as, "label", label);
        }
        CircuitMutation m = new CircuitMutation(circuit);
        m.add(f.createComponent(at, as));
        return m;
    }

    /** 여러 부품의 같은 속성을 한 번에(속성이 없는 부품은 건너뜀). */
    public static CircuitMutation setAttribute(Circuit circuit, List<Component> comps, String attr, String value) {
        CircuitMutation m = new CircuitMutation(circuit);
        for (Component c : comps) {
            @SuppressWarnings("unchecked")
            Attribute<Object> a = (Attribute<Object>) c.getAttributeSet().getAttribute(attr);
            if (a != null) {
                m.set(c, a, a.parse(value));
            }
        }
        return m;
    }

    /**
     * 게이트 종류 바꾸기(#73, 부록 A.5). NAND·NOR·XOR·XNOR는 입력 핀이 10px씩 더 멀어서, 입력 핀 자리를 그대로
     * 두도록 부품을 옮기고, 출력 자리가 바뀌면 출력에 닿은 선을 새 출력까지 늘이거나 줄인다(그런 선이 없으면
     * 짧은 선을 더한다). 속성은 이름이 같은 것을 옮긴다.
     */
    public static CircuitMutation swapGate(Circuit circuit, Component gate, ComponentFactory newFactory) {
        AttributeSet as = newFactory.createAttributeSet();
        AttributeSet old = gate.getAttributeSet();
        for (Attribute<?> a : old.getAttributes()) {
            if (as.containsAttribute(a)) {
                String v = get(old, a);
                if (v != null) {
                    set(as, a.getName(), v);
                }
            } else if (as.getAttribute(a.getName()) != null) {
                String v = get(old, a);
                if (v != null) {
                    set(as, a.getName(), v);
                }
            }
        }
        Location loc = gate.getLocation();
        Component probe = newFactory.createComponent(loc, as);
        Location oldIn = gate.getEnds().get(1).getLocation();
        Location newIn = probe.getEnds().get(1).getLocation();
        Location moved = loc.translate(oldIn.getX() - newIn.getX(), oldIn.getY() - newIn.getY());
        Component replacement = newFactory.createComponent(moved, as);

        CircuitMutation m = new CircuitMutation(circuit);
        m.replace(gate, replacement);
        Location oldOut = gate.getEnds().get(0).getLocation();
        Location newOut = replacement.getEnds().get(0).getLocation();
        if (!oldOut.equals(newOut)) {
            boolean fixed = false;
            for (Wire w : wiresAt(circuit, oldOut)) {
                Location other = w.getEnd0().equals(oldOut) ? w.getEnd1() : w.getEnd0();
                if (onSameLine(oldOut, newOut, other) && !other.equals(newOut) && awayFrom(newOut, other, oldOut)) {
                    m.replace(w, Wire.create(newOut, other));
                    fixed = true;
                }
            }
            if (!fixed) {
                m.add(Wire.create(newOut, oldOut));
            }
        }
        return m;
    }

    static List<Wire> wiresAt(Circuit circuit, Location p) {
        List<Wire> ret = new ArrayList<>();
        for (Wire w : circuit.getWires()) {
            if (w.getEnd0().equals(p) || w.getEnd1().equals(p)) {
                ret.add(w);
            }
        }
        return ret;
    }

    private static boolean onSameLine(Location a, Location b, Location c) {
        return (a.getX() == b.getX() && b.getX() == c.getX()) || (a.getY() == b.getY() && b.getY() == c.getY());
    }

    /** other가 oldOut 쪽이 아니라 게이트 바깥쪽(newOut에서 oldOut을 지나 더 간 곳, 또는 그 방향)에 있는가. */
    private static boolean awayFrom(Location newOut, Location other, Location oldOut) {
        // 선이 새 출력에서 바깥으로 뻗으려면 other가 newOut 기준으로 게이트 반대편이어야 한다
        int dx = Integer.signum(other.getX() - newOut.getX());
        int dy = Integer.signum(other.getY() - newOut.getY());
        int ox = Integer.signum(oldOut.getX() - newOut.getX());
        int oy = Integer.signum(oldOut.getY() - newOut.getY());
        return (dx != 0 || dy != 0) && (ox == 0 && oy == 0 || dx == ox && dy == oy || dx == -ox && dy == -oy
                && Math.abs(other.getX() - newOut.getX()) + Math.abs(other.getY() - newOut.getY()) > 0);
    }

    /**
     * 선을 터널 두 개로 바꾼다(#73). 선을 지우고 양 끝에 같은 이름 터널을 둔다. 몸체는 선이 있던 쪽이다.
     * 넷은 그대로다(같은 이름 터널은 한 넷).
     */
    public static CircuitMutation wireToTunnels(LogisimFile file, Circuit circuit, Wire w, String label, int width) {
        ComponentFactory f = builtin(file, "Wiring", "Tunnel");
        CircuitMutation m = new CircuitMutation(circuit);
        m.remove(w);
        Location a = w.getEnd0();
        Location b = w.getEnd1();
        m.add(tunnel(f, a, towards(a, b).reverse(), label, width));
        m.add(tunnel(f, b, towards(b, a).reverse(), label, width));
        return m;
    }

    private static Component tunnel(ComponentFactory f, Location at, Direction facing, String label, int width) {
        AttributeSet as = f.createAttributeSet();
        as.setValue(com.cburch.logisim.instance.StdAttr.FACING, facing);
        set(as, "label", label);
        set(as, "width", Integer.toString(Math.max(1, width)));
        return f.createComponent(at, as);
    }

    /** 넷의 선만 지운다(W-04 "Delete Net Wires"). 터널·부품은 그대로다. 되돌리기 한 번. */
    public static CircuitMutation deleteNetWires(Circuit circuit, kr.ac.hallym.hcs.app.model.Netlist.Net net) {
        CircuitMutation m = new CircuitMutation(circuit);
        m.removeAll(new ArrayList<>(net.wires()));
        return m;
    }

    /** from에서 to를 향하는 방향. */
    public static Direction towards(Location from, Location to) {
        if (to.getX() > from.getX()) {
            return Direction.EAST;
        }
        if (to.getX() < from.getX()) {
            return Direction.WEST;
        }
        return to.getY() > from.getY() ? Direction.SOUTH : Direction.NORTH;
    }

    /** 이 부품이 게이트(종류 바꾸기 대상)인가. */
    public static boolean isSwappableGate(Component c) {
        String f = c.getFactory().getName();
        return f.equals("AND Gate") || f.equals("OR Gate") || f.equals("NAND Gate") || f.equals("NOR Gate")
                || f.equals("XOR Gate") || f.equals("XNOR Gate");
    }

    public static String[] swappableGates() {
        return new String[] {"AND Gate", "OR Gate", "NAND Gate", "NOR Gate", "XOR Gate", "XNOR Gate"};
    }

    /** 포트 이름(등록표). */
    public static String portLabel(Component c, int end) {
        return Kinds.portName(c, end);
    }
}
