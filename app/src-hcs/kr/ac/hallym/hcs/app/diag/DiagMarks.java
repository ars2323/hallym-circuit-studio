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
 * 회로도 위 진단 표시(PLAN.md 4.4 "진단 패널과 회로도 위 표시로 알린다"). 진단이 가리키는 부품을 옅은 빨간 테두리로,
 * Messages에서 누른 진단은 굵은 테두리와 선 강조로 그린다. 그릴 때만 적용하고 파일은 바꾸지 않는다.
 */
public final class DiagMarks {
    static final Color SOFT = new Color(Tokens.ERROR.getRed(), Tokens.ERROR.getGreen(), Tokens.ERROR.getBlue(), 120);
    static final Color STRONG = Tokens.ERROR;

    private DiagMarks() {
    }

    /** CanvasPainter가 부품을 그린 뒤 부른다(회로 좌표의 Graphics). */
    public static void paint(Canvas canvas, Graphics g0, Circuit circuit) {
        if (!(g0 instanceof Graphics2D) || circuit == null || canvas.getProject() == null) {
            return;
        }
        Diagnostics diags = Diagnostics.of(canvas.getProject());
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Diagnostic focus = diags.focused();
            for (Diagnostic d : diags.in(circuit)) {
                boolean strong = focus != null && focus.kind == d.kind && focus.args().equals(d.args());
                g.setColor(strong ? STRONG : SOFT);
                g.setStroke(new BasicStroke(strong ? 3f : 1.5f));
                for (Component c : d.components) {
                    Bounds b = c.getBounds();
                    g.drawRoundRect(b.getX() - 4, b.getY() - 4, b.getWidth() + 8, b.getHeight() + 8, 8, 8);
                }
                if (strong) {
                    g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(new Color(STRONG.getRed(), STRONG.getGreen(), STRONG.getBlue(), 90));
                    for (Wire w : d.wires) {
                        g.drawLine(w.getEnd0().getX(), w.getEnd0().getY(), w.getEnd1().getX(), w.getEnd1().getY());
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }
}
