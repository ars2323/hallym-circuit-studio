/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.probe;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;

import kr.ac.hallym.hcs.app.edit.CircuitEdits;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 빠른 프로브(#75, PLAN.md 11.5). 선 옆 빈 격자에 프로브를 두고 짧은 선으로 잇는다. 부록 A.4의 연결 규칙 때문에
 * 짧은 선과 프로브는 다른 넷의 선(끝점·중간)·교차점·남의 포트에 닿지 않아야 한다. 그래야 어떤 넷도 합치거나
 * 끊지 않는다. 배치 탐색은 GUI 없이 테스트한다.
 */
public final class QuickProbe {
    /** 배치: 선 위 점 p, 프로브 연결점 q, 프로브 방향. */
    public static final class Placement {
        public final Location p;
        public final Location q;
        public final Direction facing;

        Placement(Location p, Location q, Direction facing) {
            this.p = p;
            this.q = q;
            this.facing = facing;
        }
    }

    /** 진법(원조 Probe의 radix 저장 값). */
    public static final String[] RADICES = {"16", "10signed", "10unsigned", "2"};

    private QuickProbe() {
    }

    /** near 가까운 곳부터 선 w를 따라 빈 자리를 찾는다. 없으면 null. */
    public static Placement find(LogisimFile file, Circuit c, Wire w, Location near) {
        Netlist nl = Netlist.of(c);
        Netlist.Net own = nl.netOf(w);
        ComponentFactory probe = CircuitEdits.builtin(file, "Wiring", "Probe");
        boolean horizontal = w.getEnd0().getY() == w.getEnd1().getY();
        Direction[] sides = horizontal ? new Direction[] {Direction.NORTH, Direction.SOUTH}
                : new Direction[] {Direction.EAST, Direction.WEST};
        for (Location p : along(w, near)) {
            if (onOtherNet(c, nl, own, p)) {
                continue;
            }
            for (int d : new int[] {20, 30, 40, 50}) {
                for (Direction side : sides) {
                    Location q = p.translate(side, d);
                    if (!segmentClear(c, nl, own, p, q)) {
                        continue;
                    }
                    for (Direction facing : new Direction[] {side.reverse(), side}) {
                        Bounds body = probeBounds(probe, q, facing);
                        if (!body.contains(p) && bodyClear(c, nl, own, body, q)) {
                            return new Placement(p, q, facing);
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 선 위 격자점들, near에 가까운 것부터. */
    static List<Location> along(Wire w, Location near) {
        List<Location> pts = new ArrayList<>();
        Location a = w.getEnd0();
        Location b = w.getEnd1();
        int len = Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
        Direction dir = a.getX() < b.getX() ? Direction.EAST : a.getX() > b.getX() ? Direction.WEST
                : a.getY() < b.getY() ? Direction.SOUTH : Direction.NORTH;
        for (int s = 0; s <= len; s += 10) {
            pts.add(a.translate(dir, s));
        }
        pts.sort((x, y) -> dist(x, near) - dist(y, near));
        return pts;
    }

    private static int dist(Location a, Location b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }

    /** 점 p에 다른 넷의 선이나 포트가 있는가. */
    static boolean onOtherNet(Circuit c, Netlist nl, Netlist.Net own, Location p) {
        for (Wire o : c.getWires()) {
            if (nl.netOf(o) != own && (o.getEnd0().equals(p) || o.getEnd1().equals(p) || o.contains(p))) {
                return true;
            }
        }
        for (Component comp : c.getNonWires()) {
            for (int i = 0; i < comp.getEnds().size(); i++) {
                EndData e = comp.getEnds().get(i);
                if (e.getLocation().equals(p) && nl.netOf(comp, i) != own) {
                    return true;
                }
            }
        }
        return false;
    }

    /** p에서 q까지(p 제외) 격자점마다 선·포트·부품이 없는가. */
    static boolean segmentClear(Circuit c, Netlist nl, Netlist.Net own, Location p, Location q) {
        Direction dir = CircuitEdits.towards(p, q);
        int len = dist(p, q);
        for (int s = 10; s <= len; s += 10) {
            Location x = p.translate(dir, s);
            for (Wire o : c.getWires()) {
                if (o.getEnd0().equals(x) || o.getEnd1().equals(x) || o.contains(x)) {
                    return false; // 교차도 피한다(교차점 위 금지)
                }
            }
            for (Component comp : c.getNonWires()) {
                for (EndData e : comp.getEnds()) {
                    if (e.getLocation().equals(x)) {
                        return false; // 남의 포트 위를 지나면 연결된다(부록 A.4)
                    }
                }
                if (comp.getBounds().contains(x)) {
                    return false;
                }
            }
        }
        return true;
    }

    static Bounds probeBounds(ComponentFactory probe, Location q, Direction facing) {
        AttributeSet as = probe.createAttributeSet();
        as.setValue(StdAttr.FACING, facing);
        return probe.getOffsetBounds(as).translate(q.getX(), q.getY());
    }

    /** 프로브 몸체 자리에 부품·선·포트가 없는가(연결점 q는 짧은 선의 끝이라 뺀다). */
    static boolean bodyClear(Circuit c, Netlist nl, Netlist.Net own, Bounds body, Location q) {
        Bounds grown = body.expand(5);
        for (Component comp : c.getNonWires()) {
            if (overlap(comp.getBounds(), grown)) {
                return false;
            }
        }
        for (Wire o : c.getWires()) {
            if (overlap(o.getBounds(), body)) {
                return false;
            }
        }
        return true;
    }

    /** 두 영역이 겹치거나 닿는가(선은 폭이 0이라 경계를 포함해 본다). */
    static boolean overlap(Bounds a, Bounds b) {
        return a.getX() <= b.getX() + b.getWidth() && b.getX() <= a.getX() + a.getWidth()
                && a.getY() <= b.getY() + b.getHeight() && b.getY() <= a.getY() + a.getHeight();
    }

    /** 넷의 이름(라벨 붙은 터널·핀), 없으면 빈 문자열. 프로브 라벨로 쓴다. */
    public static String netName(Circuit c, Netlist.Net net) {
        if (net == null) {
            return "";
        }
        for (Netlist.PortRef p : net.ports()) {
            String f = p.component.getFactory().getName();
            if (f.equals("Tunnel") || f.equals("Pin")) {
                String label = Names.label(p.component);
                if (label != null) {
                    return label;
                }
            }
        }
        return "";
    }

    /** 프로브와 짧은 선을 더하는 변경(되돌리기 한 번). */
    public static CircuitMutation place(LogisimFile file, Circuit c, Placement pl, String radix, String label) {
        ComponentFactory probe = CircuitEdits.builtin(file, "Wiring", "Probe");
        AttributeSet as = probe.createAttributeSet();
        as.setValue(StdAttr.FACING, pl.facing);
        CircuitEdits.set(as, "radix", radix);
        if (label != null && !label.isEmpty()) {
            CircuitEdits.set(as, "label", label);
        }
        CircuitMutation m = new CircuitMutation(c);
        m.add(Wire.create(pl.p, pl.q));
        m.add(probe.createComponent(pl.q, as));
        return m;
    }

    /** 이 회로의 모든 프로브(일괄 선택·삭제용). */
    public static List<Component> probes(Circuit c) {
        List<Component> ret = new ArrayList<>();
        for (Component comp : c.getNonWires()) {
            if (comp.getFactory().getName().equals("Probe") || comp.getFactory().getName().equals("Radix Probe")) {
                ret.add(comp);
            }
        }
        return ret;
    }
}
