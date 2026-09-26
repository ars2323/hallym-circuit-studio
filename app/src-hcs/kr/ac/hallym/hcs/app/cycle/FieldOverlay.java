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
 * 명령어 필드 색 덧그림(C-07): Instruction 탭이 보이는 동안 이름 붙은 스플리터 팔에서 나가는 선 위에 필드 색 띠를
 * 반투명으로 겹쳐 그린다. 선의 값 색은 그 아래 그대로 보인다. 파일에 저장하지 않는다.
 */
public final class FieldOverlay {
    /** 띠 폭(화면 px). */
    static final float BAND_PX = 7f;
    static final float ALPHA = 0.45f;

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
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ALPHA));
            g.setStroke(new BasicStroke((float) (BAND_PX / z), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Map.Entry<String, Set<Wire>> e : paths.entrySet()) {
                g.setColor(FieldPaths.color(e.getKey()));
                for (Wire w : e.getValue()) {
                    g.drawLine(w.getEnd0().getX(), w.getEnd0().getY(), w.getEnd1().getX(), w.getEnd1().getY());
                }
            }
        } finally {
            g.dispose();
        }
    }
}
