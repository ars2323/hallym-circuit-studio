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

    /** 선 덧칠. 테두리처럼 화면 좌표에서 픽셀에 맞춰(S-13) 3px·6px이 그대로 보이게. */
    static void wires(Graphics2D g, Diagnostic d, boolean strong, double z) {
        if (d.wires.isEmpty()) {
            return;
        }
        java.awt.geom.AffineTransform t = g.getTransform();
        double ds = Math.abs(t.getScaleX()) / (z <= 0 ? 1.0 : z);
        float w = strong ? FOCUS_WIRE_PX : WIRE_PX;
        double half = (Math.round(w) % 2 == 1) ? 0.5 : 0;
        Graphics2D s = (Graphics2D) g.create();
        try {
            s.setTransform(java.awt.geom.AffineTransform.getScaleInstance(ds, ds));
            s.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            s.setColor(strong ? FOCUS_WIRE : WIRE);
            s.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Point2D a = new java.awt.geom.Point2D.Double();
            java.awt.geom.Point2D b = new java.awt.geom.Point2D.Double();
            for (Wire wire : d.wires) {
                t.transform(new java.awt.geom.Point2D.Double(wire.getEnd0().getX(), wire.getEnd0().getY()), a);
                t.transform(new java.awt.geom.Point2D.Double(wire.getEnd1().getX(), wire.getEnd1().getY()), b);
                s.draw(new java.awt.geom.Line2D.Double(Math.round(a.getX() / ds) + half, Math.round(a.getY() / ds)
                        + half, Math.round(b.getX() / ds) + half, Math.round(b.getY() / ds) + half));
            }
        } finally {
            s.dispose();
        }
    }

    /**
     * 부품 둘레 테두리와 오른쪽 위 점. 테두리는 화면 좌표에서 픽셀에 맞춰 그린다(S-13): 배율을 곱한 좌표에 원조 Java2D가
     * 기본으로 반 픽셀 보정을 하면 2px 선이 세 픽셀에 걸쳐 흐려져 꽉 찬 픽셀은 1px만 남는다. 그래서 가장자리를 정수
     * 픽셀(홀수 굵기는 픽셀 가운데)에 두고 보정 없이(STROKE_PURE) 그려 누른 항목 4px·누르지 않은 항목 2px이 그대로
     * 보이게 한다.
     */
    static void mark(Graphics2D g, Bounds b, boolean strong, double z) {
        float gap = px(GAP_PX, z) + 1;
        java.awt.geom.AffineTransform t = g.getTransform();
        // 화면(논리) 좌표: 기기 배율(HiDPI)은 남기고 회로 배율과 이동만 푼다
        double ds = Math.abs(t.getScaleX()) / (z <= 0 ? 1.0 : z);
        java.awt.geom.Rectangle2D dev = t.createTransformedShape(new java.awt.geom.Rectangle2D.Double(b.getX() - gap,
                b.getY() - gap, b.getWidth() + 2 * gap, b.getHeight() + 2 * gap)).getBounds2D();
        double x0 = Math.round(dev.getX() / ds);
        double y0 = Math.round(dev.getY() / ds);
        double x1 = Math.round(dev.getMaxX() / ds);
        double y1 = Math.round(dev.getMaxY() / ds);
        float w = strong ? FOCUS_BORDER_PX : BORDER_PX;
        double half = (Math.round(w) % 2 == 1) ? 0.5 : 0; // 홀수 굵기는 픽셀 가운데
        Graphics2D s = (Graphics2D) g.create();
        try {
            s.setTransform(java.awt.geom.AffineTransform.getScaleInstance(ds, ds));
            s.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            s.setColor(MARK);
            s.setStroke(new BasicStroke(w));
            s.draw(new java.awt.geom.RoundRectangle2D.Double(x0 + half, y0 + half, x1 - x0, y1 - y0, 6, 6));
            float r = DOT_PX / 2;
            float cx = (float) x1;
            float cy = (float) y0;
            s.setColor(Tokens.WHITE);
            s.fill(new java.awt.geom.Ellipse2D.Float(cx - r - 1, cy - r - 1, 2 * (r + 1), 2 * (r + 1)));
            s.setColor(MARK);
            s.fill(new java.awt.geom.Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        } finally {
            s.dispose();
        }
    }

    /** 화면 px을 회로 좌표로(배율로 나눈다). */
    static float px(float screen, double zoom) {
        return (float) (screen / (zoom <= 0 ? 1.0 : zoom));
    }
}
