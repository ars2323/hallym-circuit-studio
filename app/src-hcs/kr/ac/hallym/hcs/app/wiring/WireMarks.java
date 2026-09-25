/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.WidthIncompatibilityData;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.prefs.AppPreferences;

import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 연결점과 점프(W-04, PLAN.md 11.9). 그릴 때만 적용하고 파일은 바꾸지 않는다.
 * <ul>
 * <li><b>연결점:</b> 선·포트가 셋 이상 만나는 점, 그리고 포트가 선 한가운데에 닿아 이어진 점에 큰 점을 그린다. 원조는
 * 회로 좌표 8px 점이라 25%에서 2px로 사라진다. 여기서는 화면 기준 최소 7px이다.</li>
 * <li><b>점프:</b> 가로선과 세로선이 끝점 없이 엇갈리는(이어지지 않은) 점에서 가로선을 끊고 위로 반원을 그린다.
 * 이어진 점(큰 점)과 이어지지 않은 점(반원)이 한눈에 구분된다.</li>
 * <li><b>넷 강조:</b> 우클릭 "Highlight Net"으로 고른 넷의 선 위에 옅은 강조 띠를 그린다. 회로를 고치면 지운다.</li>
 * </ul>
 * 색은 원조 선 색 규칙(값 색, 폭 오류 색, 인쇄 보기는 검정)을 그대로 따른다.
 */
public final class WireMarks {
    /** 화면 px: 연결점 지름 최소, 점프 반지름 최소. 회로 좌표로는 연결점 8, 점프 반지름 5가 최소. */
    static final float DOT_PX = 7f;
    static final float JUMP_PX = 5f;
    static final int DOT_MIN = 8;
    static final int JUMP_MIN = 5;
    static final float HIGHLIGHT_PX = 9f;
    static final Color HIGHLIGHT = new Color(Tokens.TEAL.getRed(), Tokens.TEAL.getGreen(), Tokens.TEAL.getBlue(), 90);

    private static final Map<Circuit, Set<Wire>> HIGHLIGHTED = Collections.synchronizedMap(new WeakHashMap<>());
    /** 회로 모양이 그대로면 연결점·점프를 다시 셈하지 않는다(그릴 때마다 부르므로). */
    private static final Map<Circuit, Object[]> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private WireMarks() {
    }

    // ---- 모델(GUI 없이 테스트) ----

    /** 연결점: 선 끝·포트가 셋 이상 모인 점과, 포트가 선 한가운데에 닿은 점. 위치 순. */
    public static List<Location> junctions(Circuit circuit) {
        Map<Location, Integer> count = new HashMap<>();
        for (Wire w : circuit.getWires()) {
            count.merge(w.getEnd0(), 1, Integer::sum);
            count.merge(w.getEnd1(), 1, Integer::sum);
        }
        Set<Location> ports = new HashSet<>();
        for (Component c : circuit.getNonWires()) {
            for (int i = 0; i < c.getEnds().size(); i++) {
                ports.add(c.getEnd(i).getLocation());
            }
        }
        Set<Location> out = new HashSet<>();
        for (Location p : ports) {
            count.merge(p, 1, Integer::sum);
        }
        for (Map.Entry<Location, Integer> e : count.entrySet()) {
            if (e.getValue() >= 3) {
                out.add(e.getKey());
            }
        }
        // 선 한가운데에 닿은 포트·선 끝(부록 A.4 "포트 위를 지나감"도 원조에서는 이어진다)
        List<Location> ends = new ArrayList<>(ports);
        for (Wire w : circuit.getWires()) {
            ends.add(w.getEnd0());
            ends.add(w.getEnd1());
        }
        Map<Integer, List<Wire>> rows = new HashMap<>();
        Map<Integer, List<Wire>> cols = new HashMap<>();
        for (Wire w : circuit.getWires()) {
            if (w.isVertical()) {
                cols.computeIfAbsent(w.getEnd0().getX(), k -> new ArrayList<>()).add(w);
            } else {
                rows.computeIfAbsent(w.getEnd0().getY(), k -> new ArrayList<>()).add(w);
            }
        }
        for (Location p : ends) {
            for (List<Wire> line : java.util.Arrays.asList(rows.get(p.getY()), cols.get(p.getX()))) {
                for (Wire w : line == null ? Collections.<Wire>emptyList() : line) {
                    if (w.contains(p) && !w.endsAt(p)) {
                        out.add(p);
                    }
                }
            }
        }
        List<Location> ret = new ArrayList<>(out);
        ret.sort(BY_PLACE);
        return ret;
    }

    /** 점프: 가로선과 세로선이 둘 다 한가운데로 지나고, 그 점에 선 끝·포트가 없는 점. 위치 순. */
    public static List<Location> crossings(Circuit circuit) {
        List<Wire> hs = new ArrayList<>();
        List<Wire> vs = new ArrayList<>();
        Set<Location> taken = new HashSet<>();
        for (Wire w : circuit.getWires()) {
            if (w.getLength() == 0) {
                continue;
            }
            (w.isVertical() ? vs : hs).add(w);
            taken.add(w.getEnd0());
            taken.add(w.getEnd1());
        }
        for (Component c : circuit.getNonWires()) {
            for (int i = 0; i < c.getEnds().size(); i++) {
                taken.add(c.getEnd(i).getLocation());
            }
        }
        Set<Location> out = new HashSet<>();
        for (Wire h : hs) {
            int y = h.getEnd0().getY();
            int x0 = Math.min(h.getEnd0().getX(), h.getEnd1().getX());
            int x1 = Math.max(h.getEnd0().getX(), h.getEnd1().getX());
            for (Wire v : vs) {
                int x = v.getEnd0().getX();
                int y0 = Math.min(v.getEnd0().getY(), v.getEnd1().getY());
                int y1 = Math.max(v.getEnd0().getY(), v.getEnd1().getY());
                if (x > x0 && x < x1 && y > y0 && y < y1) {
                    Location p = Location.create(x, y);
                    if (!taken.contains(p)) {
                        out.add(p);
                    }
                }
            }
        }
        List<Location> ret = new ArrayList<>(out);
        ret.sort(BY_PLACE);
        return ret;
    }

    static final Comparator<Location> BY_PLACE = Comparator.comparingInt(Location::getY)
            .thenComparingInt(Location::getX);

    // ---- 넷 강조 ----

    /** 이 선의 넷(선택 "Select Whole Net"과 같은 넷)을 강조한다. 다시 부르면 바꾼다. */
    public static void highlight(Circuit circuit, Netlist.Net net) {
        if (net == null) {
            HIGHLIGHTED.remove(circuit);
        } else {
            HIGHLIGHTED.put(circuit, Collections.unmodifiableSet(new HashSet<>(net.wires())));
        }
    }

    public static void clearHighlight(Circuit circuit) {
        HIGHLIGHTED.remove(circuit);
    }

    /** 강조한 넷의 선. 그 선이 하나라도 회로에서 사라졌으면(넷을 고쳤으면) 강조를 지운다. */
    public static Set<Wire> highlighted(Circuit circuit) {
        Set<Wire> s = HIGHLIGHTED.get(circuit);
        if (s == null) {
            return Collections.emptySet();
        }
        if (!circuit.getWires().containsAll(s)) {
            HIGHLIGHTED.remove(circuit);
            return Collections.emptySet();
        }
        return s;
    }

    // ---- 그리기 ----

    /** CanvasPainter가 회로를 그린 뒤, 라벨 칩보다 먼저 부른다(회로 좌표의 Graphics). */
    public static void paint(Canvas canvas, Graphics g0, Circuit circuit, CircuitState state, Set<Component> hidden) {
        if (!(g0 instanceof Graphics2D) || circuit == null) {
            return;
        }
        double z = canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
        boolean showState = !AppPreferences.PRINTER_VIEW.getBoolean();
        paint((Graphics2D) g0, circuit, showState ? state : null, hidden, z);
    }

    /** state가 null이면 인쇄 보기처럼 검정으로 그린다. */
    public static void paint(Graphics2D g0, Circuit circuit, CircuitState state, Set<Component> hidden, double z) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Colors colors = new Colors(circuit, state);
            Set<Wire> hl = highlighted(circuit);
            if (!hl.isEmpty()) {
                g.setColor(HIGHLIGHT);
                g.setStroke(new BasicStroke(Math.max(7f, px(HIGHLIGHT_PX, z)), BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                for (Wire w : hl) {
                    if (circuit.getWires().contains(w) && (hidden == null || !hidden.contains(w))) {
                        g.draw(new Line2D.Float(w.getEnd0().getX(), w.getEnd0().getY(), w.getEnd1().getX(),
                                w.getEnd1().getY()));
                    }
                }
            }
            if (hidden == null || hidden.isEmpty()) {
                List<List<Location>> marks = cached(circuit);
                float r = Math.max(JUMP_MIN, px(JUMP_PX, z));
                for (Location p : marks.get(1)) {
                    jump(g, p, r, colors.at(p, true), colors.at(p, false));
                }
                float d = Math.max(DOT_MIN, px(DOT_PX, z));
                for (Location p : marks.get(0)) {
                    g.setColor(colors.at(p, null));
                    g.fill(new Ellipse2D.Float(p.getX() - d / 2, p.getY() - d / 2, d, d));
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** [연결점, 점프]. 선과 포트 자리의 모양이 같으면 지난 값. */
    static List<List<Location>> cached(Circuit circuit) {
        List<Object> key = new ArrayList<>();
        for (Wire w : circuit.getWires()) {
            key.add(w);
        }
        for (Component c : circuit.getNonWires()) {
            for (int i = 0; i < c.getEnds().size(); i++) {
                key.add(c.getEnd(i).getLocation());
            }
        }
        Object[] hit = CACHE.get(circuit);
        if (hit != null && hit[0].equals(key)) {
            @SuppressWarnings("unchecked")
            List<List<Location>> v = (List<List<Location>>) hit[1];
            return v;
        }
        List<List<Location>> v = List.of(junctions(circuit), crossings(circuit));
        CACHE.put(circuit, new Object[] {key, v});
        return v;
    }

    /** 가로선을 끊고(바탕색), 세로선을 다시 긋고, 가로선 색으로 위쪽 반원. */
    static void jump(Graphics2D g, Location p, float r, Color horizontal, Color vertical) {
        float x = p.getX();
        float y = p.getY();
        g.setColor(Color.WHITE);
        g.fill(new java.awt.geom.Rectangle2D.Float(x - r - 0.5f, y - Wire.WIDTH, 2 * r + 1, 2 * Wire.WIDTH));
        g.setStroke(new BasicStroke(Wire.WIDTH));
        g.setColor(vertical);
        g.draw(new Line2D.Float(x, y - r - 1, x, y + r + 1));
        g.setColor(horizontal);
        g.setStroke(new BasicStroke(Wire.WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Float(x - r, y - r, 2 * r, 2 * r, 0, 180, Arc2D.OPEN));
    }

    /** 원조 CircuitWires.draw의 선 색: 폭이 안 맞는 넷은 폭 오류 색, 그 밖은 값 색, 인쇄 보기는 검정. */
    static final class Colors {
        private final Circuit circuit;
        private final CircuitState state;
        private Set<Location> widthError;

        Colors(Circuit circuit, CircuitState state) {
            this.circuit = circuit;
            this.state = state;
        }

        /** horizontal이 null이면 그 점, true/false면 그 점을 지나는 가로/세로선의 색. */
        Color at(Location p, Boolean horizontal) {
            Location at = p;
            if (horizontal != null) {
                for (Wire w : circuit.getWires()) {
                    if (w.isVertical() != horizontal && w.contains(p) && w.getLength() > 0) {
                        at = w.getEnd0();
                        break;
                    }
                }
            }
            if (errors().contains(at)) {
                return Value.WIDTH_ERROR_COLOR;
            }
            if (state == null) {
                return Color.BLACK;
            }
            return state.getValue(at).getColor();
        }

        private Set<Location> errors() {
            if (widthError == null) {
                widthError = new HashSet<>();
                Set<WidthIncompatibilityData> data = circuit.getWidthIncompatibilityData();
                if (data != null && !data.isEmpty()) {
                    Netlist nl = Netlist.of(circuit);
                    for (WidthIncompatibilityData d : data) {
                        for (int i = 0; i < d.size(); i++) {
                            for (Netlist.Net n : nl.nets()) {
                                if (touches(n, d.getPoint(i))) {
                                    for (Wire w : n.wires()) {
                                        widthError.add(w.getEnd0());
                                        widthError.add(w.getEnd1());
                                    }
                                    for (Netlist.PortRef r : n.ports()) {
                                        widthError.add(r.location());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return widthError;
        }

        private static boolean touches(Netlist.Net n, Location p) {
            for (Wire w : n.wires()) {
                if (w.contains(p)) {
                    return true;
                }
            }
            for (Netlist.PortRef r : n.ports()) {
                if (r.location().equals(p)) {
                    return true;
                }
            }
            return false;
        }
    }

    static float px(float screen, double zoom) {
        return (float) (screen / (zoom <= 0 ? 1.0 : zoom));
    }
}
