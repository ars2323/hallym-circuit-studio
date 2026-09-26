/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.flow.ActivePath;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 활성 경로(C-08, PLAN.md 11.12): 사이클 뷰가 보이는 동안, 고른 사이클의 MUX 선택 값을 보고 실제로 고른 데이터 입력의
 * 넷(그 입력까지 오는 선) 둘레에 진한 남색 띠를 그린다(필드 색과 같은 방식: 선의 값 색을 가리지 않는다). 선택 값이 정해지지 않은 MUX는 칠하지 않는다. 고르지 않은 입력은
 * 흐리게 하지 않는다: 그 넷이 다른 곳에서는 쓰일 수 있다. 값은 캔버스의 회로 상태(기록 엔진이 고른 사이클로 바꿔
 * 끼운 것)에서 읽기만 한다. 켜고 끄기는 앱 환경설정, 파일에는 저장하지 않는다.
 */
public final class ActivePathOverlay {
    static final String KEY = "cycle.activePath";
    static final float ALPHA = 0.4f;
    static final float HALO_ALPHA = 0.7f;

    private static final Set<Project> SHOWN = Collections.newSetFromMap(new WeakHashMap<>());
    /** 회로마다 넷 목록(부품·선이 그대로면 다시 쓴다). */
    private static final Map<Circuit, Object[]> NETS = new WeakHashMap<>();

    private ActivePathOverlay() {
    }

    public static boolean enabled() {
        return Settings.get().getBoolean(KEY, true);
    }

    public static void setEnabled(boolean on) {
        Settings.get().set(KEY, Boolean.toString(on));
        try {
            Settings.get().save();
        } catch (java.io.IOException e) {
            // 환경설정을 못 써도 표시는 바뀐다
        }
    }

    /** 사이클 뷰가 이 프로젝트에 대해 덧그림을 원하는가. 바뀌면 캔버스를 다시 그린다. */
    static void setShown(Project proj, boolean on) {
        boolean changed;
        synchronized (SHOWN) {
            changed = on ? SHOWN.add(proj) : SHOWN.remove(proj);
        }
        if (changed) {
            proj.repaintCanvas();
        }
    }

    static boolean isShown(Project proj) {
        synchronized (SHOWN) {
            return SHOWN.contains(proj);
        }
    }

    /**
     * MUX마다 선택 값이 정해졌으면, 고른 데이터 입력까지 오는 가지의 선분들(V-04): 그 넷을 내는 포트에서 그 입력 포트까지의
     * 가장 짧은 선 경로만. 같은 넷의 다른 가지(다른 부품으로 가는 선)는 칠하지 않는다. 내는 포트를 모르면(스플리터·터널만
     * 있는 넷) 넷 전체. GUI 없이 테스트한다.
     */
    static List<Location[]> selected(Circuit circ, CircuitState state) {
        List<Location[]> out = new ArrayList<>();
        if (circ == null || state == null) {
            return out;
        }
        Netlist nl = null;
        for (Component c : circ.getNonWires()) {
            if (!c.getFactory().getName().equals("Multiplexer")) {
                continue;
            }
            int n = c.getEnds().size();
            int k = ActivePath.dataCount(c, n); // 포트: 데이터 0..k-1, 선택 k, (enable), 출력 마지막
            if (k >= n) {
                continue;
            }
            Value sel = state.getValue(c.getEnd(k).getLocation());
            if (sel == null || !sel.isFullyDefined()) {
                continue;
            }
            int i = sel.toIntValue();
            if (i < 0 || i >= k) {
                continue;
            }
            if (nl == null) {
                nl = netlist(circ);
            }
            Netlist.Net net = nl.netOf(c, i);
            if (net == null) {
                continue;
            }
            Location to = c.getEnd(i).getLocation();
            Location from = null;
            if (!net.drivers().isEmpty()) {
                from = net.drivers().get(0).location();
            } else {
                for (Netlist.PortRef p : net.ports()) {
                    if (p.component != c && !p.data().isInput()) {
                        from = p.location();
                        break;
                    }
                }
            }
            List<Location[]> branch = from == null ? Collections.<Location[]>emptyList() : Netlist.branch(net, from, to);
            if (branch.isEmpty()) {
                for (Wire w : net.wires()) {
                    out.add(new Location[] {w.getEnd0(), w.getEnd1()});
                }
            } else {
                out.addAll(branch);
            }
        }
        return out;
    }

    private static Netlist netlist(Circuit circ) {
        long sig = 17;
        for (Component c : circ.getNonWires()) {
            sig = sig * 31 + System.identityHashCode(c);
        }
        for (Wire w : circ.getWires()) {
            sig = sig * 31 + w.hashCode();
        }
        synchronized (NETS) {
            Object[] hit = NETS.get(circ);
            if (hit != null && (Long) hit[0] == sig) {
                return (Netlist) hit[1];
            }
            Netlist nl = Netlist.of(circ);
            NETS.put(circ, new Object[] {sig, nl});
            return nl;
        }
    }

    /** CanvasPainter가 부품을 그린 뒤 부른다(회로 좌표, 배율이 걸린 Graphics). */
    public static void paint(Canvas canvas, Graphics g0, Circuit circ, CircuitState state) {
        Project proj = canvas.getProject();
        if (!(g0 instanceof Graphics2D) || proj == null || !isShown(proj)) {
            return;
        }
        List<Location[]> wires = selected(circ, state);
        if (wires.isEmpty()) {
            return;
        }
        double z = canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 필드 색(C-07)과 같은 방식: 선 둘레만 칠해 선의 값 색을 가리지 않고, 부품 몸체는 칠하지 않는다
            boolean halo = z >= FieldOverlay.HALO_ZOOM;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, halo ? HALO_ALPHA : ALPHA));
            g.setColor(Tokens.NAVY);
            BasicStroke outer = new BasicStroke(FieldOverlay.BAND, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
            BasicStroke inner = new BasicStroke(FieldOverlay.HOLE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            java.awt.geom.Area area = new java.awt.geom.Area();
            java.awt.geom.Area lines = new java.awt.geom.Area();
            java.util.Map<com.cburch.logisim.data.Location, Integer> ends = new java.util.HashMap<>();
            for (Location[] w : wires) {
                java.awt.geom.Line2D line = new java.awt.geom.Line2D.Double(w[0].getX(), w[0].getY(), w[1].getX(),
                        w[1].getY());
                area.add(new java.awt.geom.Area(outer.createStrokedShape(line)));
                lines.add(new java.awt.geom.Area(inner.createStrokedShape(line)));
                ends.merge(w[0], 1, Integer::sum);
                ends.merge(w[1], 1, Integer::sum);
            }
            float band = FieldOverlay.BAND;
            for (java.util.Map.Entry<com.cburch.logisim.data.Location, Integer> e : ends.entrySet()) {
                if (e.getValue() >= 2) {
                    area.add(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(e.getKey().getX() - band / 2,
                            e.getKey().getY() - band / 2, band, band)));
                }
            }
            if (halo) {
                area.subtract(lines);
            }
            java.awt.Rectangle box = area.getBounds();
            for (Component x : circ.getNonWires()) {
                com.cburch.logisim.data.Bounds b = x.getBounds();
                java.awt.Rectangle r = new java.awt.Rectangle(b.getX() - 1, b.getY() - 1, b.getWidth() + 2,
                        b.getHeight() + 2);
                if (r.intersects(box)) {
                    area.subtract(new java.awt.geom.Area(r));
                }
            }
            g.fill(area);
        } finally {
            g.dispose();
        }
    }
}
