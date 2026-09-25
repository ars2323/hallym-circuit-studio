/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;

/**
 * 자동으로 늘리거나 꺾은 선이 PLAN.md 부록 A.4 출력 규칙을 지키는지(#81).
 * <ul>
 * <li>교차점에 끝점을 두지 않는다: 새 선의 끝점이 가로선과 세로선이 함께 지나는 점(둘 다 그 점을 끝으로 하지 않음)에
 * 있으면 안 된다.</li>
 * <li>분기는 T자: 새 선 끝이 다른 선 한가운데에 닿는 것은 된다.</li>
 * <li>목적 포트가 아닌 포트 위를 지나지 않는다: 포트는 선의 끝에만 온다. 선 한가운데에 포트가 있으면 안 된다.</li>
 * <li>다른 선과 같은 직선에서 겹치지 않는다(길이가 있는 겹침).</li>
 * <li>부품 몸체 안을 지나지 않는다(연결 규칙은 아니지만 읽을 수 있게, #81 스크린샷에서 발견).</li>
 * </ul>
 */
public final class WireRules {
    private WireRules() {
    }

    /** 새 선 added가 어기는 규칙(사람이 읽는 설명). 비었으면 지킨다. 겹침은 어느 선과든 금지(가장 엄격). */
    public static List<String> violations(Circuit circuit, Collection<Wire> added) {
        return violations(circuit, added, null);
    }

    /**
     * before가 있으면 겹침은 "다른 넷"의 선과만 금지한다(부록 A.4 규칙은 다른 넷과의 겹침을 금한다). before는 옮기기
     * 전 회로의 넷: 옛 선의 넷과, 새 선의 두 끝점에 있던 넷을 비교한다. 같은 넷의 선과 겹치는 것(따라오는 선이 옛 선
     * 위로 지나가는 것)은 연결을 바꾸지 않는다.
     */
    public static List<String> violations(Circuit circuit, Collection<Wire> added, Before before) {
        List<String> out = new ArrayList<>();
        List<Wire> all = new ArrayList<>(circuit.getWires());
        List<Wire> news = new ArrayList<>();
        for (Wire w : added) {
            if (w.getLength() > 0) {
                news.add(w);
            }
        }
        for (Wire w : news) {
            for (Location p : new Location[] {w.getEnd0(), w.getEnd1()}) {
                boolean horizontal = false;
                boolean vertical = false;
                for (Wire o : all) {
                    if (o != w && !o.equals(w) && o.contains(p) && !o.endsAt(p)) {
                        horizontal |= !o.isVertical();
                        vertical |= o.isVertical();
                    }
                }
                if (horizontal && vertical) {
                    out.add("endpoint on a crossing at " + p);
                }
            }
            for (Component c : circuit.getNonWires()) {
                for (int i = 0; i < c.getEnds().size(); i++) {
                    Location q = c.getEnd(i).getLocation();
                    if (w.contains(q) && !w.endsAt(q)) {
                        out.add("passes over a port of " + c.getFactory().getName() + " at " + q);
                    }
                }
                if (runsThrough(w, c)) {
                    out.add("runs through the body of " + c.getFactory().getName() + " at " + c.getLocation());
                }
            }
            for (Wire o : all) {
                if (o != w && !o.equals(w) && overlap(w, o) > 0 && (before == null || before.otherNet(o, w))) {
                    out.add("overlaps another wire on the same line: " + w + " / " + o);
                }
            }
        }
        return out;
    }

    /**
     * 선이 부품 몸체 안을 지나는가(보기 좋게 그리기, 연결에는 상관없음). 몸체를 3px 줄인 사각형과 선이 겹치면 지나는
     * 것으로 본다. 포트에서 몸체 가장자리에 닿는 것은 괜찮다. 터널·핀·프로브처럼 작은 부품은 보지 않는다.
     */
    static boolean runsThrough(Wire w, Component c) {
        String f = c.getFactory().getName();
        if (f.equals("Tunnel") || f.equals("Pin") || f.equals("Probe") || f.equals("Splitter") || f.equals("Text")
                || f.equals("Constant") || f.equals("Clock")) {
            return false;
        }
        com.cburch.logisim.data.Bounds b = c.getBounds();
        int x0 = b.getX() + 3;
        int y0 = b.getY() + 3;
        int x1 = b.getX() + b.getWidth() - 3;
        int y1 = b.getY() + b.getHeight() - 3;
        if (x1 <= x0 || y1 <= y0) {
            return false;
        }
        int wx0 = Math.min(w.getEnd0().getX(), w.getEnd1().getX());
        int wx1 = Math.max(w.getEnd0().getX(), w.getEnd1().getX());
        int wy0 = Math.min(w.getEnd0().getY(), w.getEnd1().getY());
        int wy1 = Math.max(w.getEnd0().getY(), w.getEnd1().getY());
        return wx0 < x1 && wx1 > x0 && wy0 < y1 && wy1 > y0;
    }

    /** 같은 직선 위 두 선이 겹치는 길이(다른 직선이거나 방향이 다르면 0). */
    static int overlap(Wire a, Wire b) {
        if (a.isVertical() != b.isVertical() || a.getLength() == 0 || b.getLength() == 0) {
            return 0;
        }
        if (a.isVertical()) {
            if (a.getEnd0().getX() != b.getEnd0().getX()) {
                return 0;
            }
            int lo = Math.max(Math.min(a.getEnd0().getY(), a.getEnd1().getY()),
                    Math.min(b.getEnd0().getY(), b.getEnd1().getY()));
            int hi = Math.min(Math.max(a.getEnd0().getY(), a.getEnd1().getY()),
                    Math.max(b.getEnd0().getY(), b.getEnd1().getY()));
            return hi - lo;
        }
        if (a.getEnd0().getY() != b.getEnd0().getY()) {
            return 0;
        }
        int lo = Math.max(Math.min(a.getEnd0().getX(), a.getEnd1().getX()),
                Math.min(b.getEnd0().getX(), b.getEnd1().getX()));
        int hi = Math.min(Math.max(a.getEnd0().getX(), a.getEnd1().getX()),
                Math.max(b.getEnd0().getX(), b.getEnd1().getX()));
        return hi - lo;
    }

    /** 옮기기 전 회로의 넷(선별, 점별). */
    public static final class Before {
        private final java.util.Map<Wire, Integer> wireNet = new java.util.IdentityHashMap<>();
        private final java.util.Map<Location, java.util.Set<Integer>> pointNets = new java.util.HashMap<>();

        public Before(Circuit circuit) {
            kr.ac.hallym.hcs.app.model.Netlist nl = kr.ac.hallym.hcs.app.model.Netlist.of(circuit);
            for (kr.ac.hallym.hcs.app.model.Netlist.Net n : nl.nets()) {
                for (Wire w : n.wires()) {
                    wireNet.put(w, n.id());
                    point(w.getEnd0(), n.id());
                    point(w.getEnd1(), n.id());
                }
                for (kr.ac.hallym.hcs.app.model.Netlist.PortRef p : n.ports()) {
                    point(p.location(), n.id());
                }
            }
        }

        private void point(Location at, int net) {
            pointNets.computeIfAbsent(at, k -> new java.util.HashSet<>()).add(net);
        }

        /** 옛 선 o가 새 선 w의 넷(w의 끝점에 있던 넷)이 아닌 넷이었는가. o가 새 선이면 false. */
        boolean otherNet(Wire o, Wire w) {
            Integer on = wireNet.get(o);
            if (on == null) {
                return false;
            }
            java.util.Set<Integer> mine = new java.util.HashSet<>();
            mine.addAll(pointNets.getOrDefault(w.getEnd0(), java.util.Collections.emptySet()));
            mine.addAll(pointNets.getOrDefault(w.getEnd1(), java.util.Collections.emptySet()));
            return !mine.contains(on);
        }
    }
}
