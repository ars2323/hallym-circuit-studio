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
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;

/**
 * 명령어 필드 색 덧그림(C-07): Instruction 탭이 보이는 동안 이름 붙은 스플리터 팔에서 나가는 선 둘레에 필드 색 띠를
 * 그린다. 50% 이상에서는 선 자체를 비워 두어 선의 값 색(0·1·E·X)이 그대로 보인다. 파일에 저장하지 않는다.
 */
public final class FieldOverlay {
    /** 띠 폭(회로 좌표): 격자 간격. 나란한 선의 띠끼리 겹치지 않는다. */
    static final float BAND = 10f;
    /** 비워 두는 선 폭(회로 좌표): 원조 선(굵은 표시 5)보다 조금 넓게. */
    static final float HOLE = com.cburch.logisim.circuit.Wire.WIDTH + 3;
    /** 이 배율 이상에서 선을 비우고 둘레만 칠한다. */
    static final double HALO_ZOOM = 0.5;
    static final float ALPHA = 0.45f;
    static final float HALO_ALPHA = 0.75f;

    private static final Map<Project, State> ALL = new WeakHashMap<>();

    static final class State {
        final int word;
        Circuit circuit;
        Map<String, Set<Wire>> paths;

        State(int word) {
            this.word = word;
        }
    }

    private FieldOverlay() {
    }

    /** 이 명령어의 필드 색을 보인다. word가 null이면 없앤다. */
    public static void show(Project proj, Integer word) {
        synchronized (ALL) {
            State old = ALL.get(proj);
            if (word == null) {
                ALL.remove(proj);
            } else if (old == null || old.word != word) {
                ALL.put(proj, new State(word));
            } else {
                return;
            }
        }
        proj.repaintCanvas();
    }

    /** 회로가 바뀌었을 때: 경로를 다시 찾게 한다. */
    public static void invalidate(Project proj) {
        synchronized (ALL) {
            State s = ALL.get(proj);
            if (s != null) {
                s.paths = null;
            }
        }
    }

    /** 지금 보이는 필드 경로(테스트). 없으면 null. */
    static Map<String, Set<Wire>> shown(Project proj, Circuit circ) {
        State s;
        synchronized (ALL) {
            s = ALL.get(proj);
        }
        if (s == null) {
            return null;
        }
        if (s.circuit != circ || s.paths == null) {
            s.circuit = circ;
            s.paths = FieldPaths.of(proj.getLogisimFile(), circ, FieldPaths.fieldsOf(s.word));
        }
        return s.paths;
    }

    /** CanvasPainter가 부품을 그린 뒤 부른다(회로 좌표, 배율이 걸린 Graphics). */
    public static void paint(Canvas canvas, Graphics g0, Circuit circ) {
        if (!(g0 instanceof Graphics2D) || canvas.getProject() == null) {
            return;
        }
        Map<String, Set<Wire>> paths = shown(canvas.getProject(), circ);
        if (paths == null || paths.isEmpty()) {
            return;
        }
        double z = canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 띠는 격자 간격(10) 폭: 나란한 선의 띠가 겹치지 않는다. 50% 이상에서는 선 자체(값 색)를 비워 둘레만
            // 칠한다(선의 값 색 0·1·E·X를 가리지 않게). 그보다 작으면 선이 너무 가늘어 띠를 선 위에 그대로 칠한다
            boolean halo = z >= HALO_ZOOM;
            float band = BAND;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, halo ? HALO_ALPHA : ALPHA));
            // 끝은 자르고(부품 포트 글자에 닿지 않게) 선이 만나는 꺾임·갈림에만 둥근 마디를 채운다
            BasicStroke outer = new BasicStroke(band, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
            BasicStroke inner = new BasicStroke(HOLE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            for (Map.Entry<String, Set<Wire>> e : paths.entrySet()) {
                g.setColor(FieldPaths.color(e.getKey()));
                Map<com.cburch.logisim.data.Location, Integer> ends = new java.util.HashMap<>();
                java.awt.geom.Area area = new java.awt.geom.Area();
                java.awt.geom.Area wires = new java.awt.geom.Area();
                for (Wire w : e.getValue()) {
                    java.awt.geom.Line2D line = new java.awt.geom.Line2D.Double(w.getEnd0().getX(), w.getEnd0().getY(),
                            w.getEnd1().getX(), w.getEnd1().getY());
                    area.add(new java.awt.geom.Area(outer.createStrokedShape(line)));
                    wires.add(new java.awt.geom.Area(inner.createStrokedShape(line)));
                    ends.merge(w.getEnd0(), 1, Integer::sum);
                    ends.merge(w.getEnd1(), 1, Integer::sum);
                }
                for (Map.Entry<com.cburch.logisim.data.Location, Integer> p : ends.entrySet()) {
                    if (p.getValue() >= 2) {
                        area.add(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(p.getKey().getX() - band / 2,
                                p.getKey().getY() - band / 2, band, band)));
                    }
                }
                if (halo) {
                    area.subtract(wires);
                }
                // 부품 몸체(테두리 포함)는 칠하지 않는다: 띠 끝이 포트에서 부품 테두리에 걸치지 않게(C-07 검토)
                java.awt.Rectangle box = area.getBounds();
                for (com.cburch.logisim.comp.Component x : circ.getNonWires()) {
                    com.cburch.logisim.data.Bounds b = x.getBounds();
                    java.awt.Rectangle r = new java.awt.Rectangle(b.getX() - 1, b.getY() - 1, b.getWidth() + 2,
                            b.getHeight() + 2);
                    if (r.intersects(box)) {
                        area.subtract(new java.awt.geom.Area(r));
                    }
                }
                g.fill(area); // 한 번에 채워 겹친 곳이 진해지지 않게
            }
        } finally {
            g.dispose();
        }
    }
}
