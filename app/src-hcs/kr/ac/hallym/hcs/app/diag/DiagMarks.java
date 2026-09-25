/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Canvas;

import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 회로도 위 진단 표시(PLAN.md 4.4 "진단 패널과 회로도 위 표시로 알린다"). 메시지가 있는 부품·터널에는 누르지 않아도
 * 빨간 테두리와 작은 빨간 점(오른쪽 위)을, 메시지가 가리키는 선에는 빨간 덧칠을 항상 그린다. Messages에서 누른 항목은
 * 더 굵게 그린다. 굵기와 점 크기는 화면 px 기준이라 배율 25~400%에서 같게 보인다. 그릴 때만 적용하고 파일은 바꾸지
 * 않는다.
 */
public final class DiagMarks {
    /** 화면 px: 테두리, 누른 항목 테두리, 점 지름, 선 덧칠, 누른 선 덧칠, 부품과 테두리 사이. */
    static final float BORDER_PX = 2f;
    static final float FOCUS_BORDER_PX = 4f;
    static final float DOT_PX = 7f;
    static final float WIRE_PX = 3f;
    static final float FOCUS_WIRE_PX = 6f;
    static final float GAP_PX = 3f;
    static final Color MARK = Tokens.ERROR;
    static final Color WIRE = new Color(Tokens.ERROR.getRed(), Tokens.ERROR.getGreen(), Tokens.ERROR.getBlue(), 110);
    static final Color FOCUS_WIRE = new Color(Tokens.ERROR.getRed(), Tokens.ERROR.getGreen(), Tokens.ERROR.getBlue(),
            150);

    private DiagMarks() {
    }

    /** CanvasPainter가 부품을 그린 뒤 부른다(회로 좌표의 Graphics, 배율이 걸려 있다). */
    public static void paint(Canvas canvas, Graphics g0, Circuit circuit) {
        if (!(g0 instanceof Graphics2D) || circuit == null || canvas.getProject() == null) {
            return;
        }
        Diagnostics diags = Diagnostics.of(canvas.getProject());
        java.util.List<Diagnostic> here = diags.in(circuit);
        if (here.isEmpty()) {
            return;
        }
        double z = canvas.getHcsZoom() == null ? 1.0 : canvas.getHcsZoom().zoomFactor();
        paint((Graphics2D) g0, here, diags.focused(), z, kr.ac.hallym.hcs.app.labels.LabelOverlay.chipRects(canvas));
    }

    /** 진단 목록을 그린다(회로 좌표의 Graphics, 배율 z). */
    static void paint(Graphics2D g0, java.util.List<Diagnostic> here, Diagnostic focus, double z) {
        paint(g0, here, focus, z, java.util.Collections.emptyList());
    }

    /** chips: 라벨 칩 자리(회로 좌표). 테두리와 점은 칩 위에 그리지 않는다(칩 글자를 가리지 않게, P-03 검토). */
    static void paint(Graphics2D g0, java.util.List<Diagnostic> here, Diagnostic focus, double z,
            java.util.List<java.awt.Rectangle> chips) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            if (!chips.isEmpty()) {
                java.awt.Rectangle clip = g.getClipBounds();
                java.awt.geom.Area a = new java.awt.geom.Area(clip != null ? clip
                        : new java.awt.Rectangle(-100000, -100000, 200000, 200000));
                for (java.awt.Rectangle r : chips) {
                    a.subtract(new java.awt.geom.Area(r));
                }
                g.setClip(a);
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 선 덧칠을 먼저(부품 테두리가 위에 오게), 누른 것을 마지막에
            for (boolean strongPass : new boolean[] {false, true}) {
                for (Diagnostic d : here) {
                    boolean strong = focus != null && focus.kind == d.kind && focus.args().equals(d.args());
                    if (strong != strongPass) {
                        continue;
                    }
                    wires(g, d, strong, z);
                    for (Component c : d.components) {
                        mark(g, c.getBounds(), strong, z);
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }

    static void wires(Graphics2D g, Diagnostic d, boolean strong, double z) {
        g.setColor(strong ? FOCUS_WIRE : WIRE);
        g.setStroke(new BasicStroke(px(strong ? FOCUS_WIRE_PX : WIRE_PX, z), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        for (Wire w : d.wires) {
            g.drawLine(w.getEnd0().getX(), w.getEnd0().getY(), w.getEnd1().getX(), w.getEnd1().getY());
        }
    }

    /** 부품 둘레 테두리와 오른쪽 위 점. */
    static void mark(Graphics2D g, Bounds b, boolean strong, double z) {
        float gap = px(GAP_PX, z) + 1;
        java.awt.geom.RoundRectangle2D box = new java.awt.geom.RoundRectangle2D.Float(b.getX() - gap,
                b.getY() - gap, b.getWidth() + 2 * gap, b.getHeight() + 2 * gap, px(6, z), px(6, z));
        g.setColor(MARK);
        g.setStroke(new BasicStroke(px(strong ? FOCUS_BORDER_PX : BORDER_PX, z)));
        g.draw(box);
        float r = px(DOT_PX, z) / 2;
        float cx = (float) (box.getMaxX());
        float cy = (float) (box.getY());
        g.setColor(Tokens.WHITE);
        g.fill(new java.awt.geom.Ellipse2D.Float(cx - r - px(1, z), cy - r - px(1, z), 2 * (r + px(1, z)),
                2 * (r + px(1, z))));
        g.setColor(MARK);
        g.fill(new java.awt.geom.Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }

    /** 화면 px을 회로 좌표로(배율로 나눈다). */
    static float px(float screen, double zoom) {
        return (float) (screen / (zoom <= 0 ? 1.0 : zoom));
    }
}
