/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.side;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JViewport;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.labels.TunnelColorStore;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 왼쪽 아래 Minimap 탭(S-11, PLAN.md 11.14): 지금 회로 전체를 작게(부품 상자, 선, 터널 색) 그리고 캔버스에 보이는
 * 영역을 파란 네모로 보인다. 누르거나 끌면 그 자리가 캔버스 가운데로 온다. 좌표 변환은 {@link Fit}(GUI 없이 잰다).
 */
public final class Minimap extends JComponent {
    private static final long serialVersionUID = 1L;
    static final int MARGIN = 8;

    /** 회로 영역을 칸 크기에 맞추는 변환(가운데 맞춤, 비율 유지). */
    static final class Fit {
        final double scale;
        final double ox;
        final double oy;

        Fit(Bounds b, int w, int h) {
            double bw = Math.max(1, b.getWidth());
            double bh = Math.max(1, b.getHeight());
            double s = Math.min((w - 2.0 * MARGIN) / bw, (h - 2.0 * MARGIN) / bh);
            scale = Math.max(1e-6, s);
            ox = (w - bw * scale) / 2 - b.getX() * scale;
            oy = (h - bh * scale) / 2 - b.getY() * scale;
        }

        double x(double cx) {
            return ox + cx * scale;
        }

        double y(double cy) {
            return oy + cy * scale;
        }

        Location toCircuit(int mx, int my) {
            return Location.create((int) Math.round((mx - ox) / scale), (int) Math.round((my - oy) / scale));
        }
    }

    private final Project proj;
    private final Canvas canvas;
    private Circuit watched;
    private final ProjectListener projectListener = this::projectChanged;
    private final CircuitListener circuitListener = this::circuitChanged;

    public Minimap(Project proj, Canvas canvas) {
        this.proj = proj;
        this.canvas = canvas;
        setOpaque(true);
        setBackground(Tokens.WHITE);
        setToolTipText(Messages.get("side.minimapTip"));
        proj.addProjectListener(projectListener);
        watch(proj.getCurrentCircuit());
        if (canvas.getParent() instanceof JViewport) {
            ((JViewport) canvas.getParent()).addChangeListener(e -> repaint());
        }
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                centerAt(e.getX(), e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                centerAt(e.getX(), e.getY());
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    private void projectChanged(ProjectEvent e) {
        if (e.getAction() == ProjectEvent.ACTION_SET_CURRENT) {
            watch(proj.getCurrentCircuit());
            repaint();
        }
    }

    private void circuitChanged(CircuitEvent e) {
        repaint();
    }

    private void watch(Circuit c) {
        if (watched != null) {
            watched.removeCircuitListener(circuitListener);
        }
        watched = c;
        if (c != null) {
            c.addCircuitListener(circuitListener);
        }
    }

    Fit fit() {
        Circuit c = proj.getCurrentCircuit();
        Bounds b = c == null ? Bounds.EMPTY_BOUNDS : c.getBounds();
        if (b == Bounds.EMPTY_BOUNDS || b.getWidth() <= 0) {
            b = Bounds.create(0, 0, 200, 150);
        }
        return new Fit(b.expand(20), Math.max(1, getWidth()), Math.max(1, getHeight()));
    }

    /** 캔버스에 보이는 영역(회로 좌표). */
    Rectangle viewInCircuit() {
        Rectangle v = canvas.getVisibleRect();
        Location a = canvas.hcsToCircuit(v.x, v.y);
        Location b = canvas.hcsToCircuit(v.x + v.width, v.y + v.height);
        return new Rectangle(a.getX(), a.getY(), b.getX() - a.getX(), b.getY() - a.getY());
    }

    /** 미니맵의 점(mx, my)이 캔버스 가운데에 오게 스크롤한다. */
    void centerAt(int mx, int my) {
        Location p = fit().toCircuit(mx, my);
        Rectangle v = canvas.getVisibleRect();
        Rectangle at = canvas.hcsToScreen(new Rectangle(p.getX(), p.getY(), 0, 0));
        canvas.scrollRectToVisible(new Rectangle(at.x - v.width / 2, at.y - v.height / 2, v.width, v.height));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            Circuit c = proj.getCurrentCircuit();
            if (c == null) {
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Fit f = fit();
            g.setColor(Tokens.TEXT_MUTED);
            g.setStroke(new BasicStroke(1f));
            for (Wire w : c.getWires()) {
                g.drawLine((int) f.x(w.getEnd0().getX()), (int) f.y(w.getEnd0().getY()),
                        (int) f.x(w.getEnd1().getX()), (int) f.y(w.getEnd1().getY()));
            }
            for (com.cburch.logisim.comp.Component x : c.getNonWires()) {
                Bounds b = x.getBounds();
                int rx = (int) f.x(b.getX());
                int ry = (int) f.y(b.getY());
                int rw = Math.max(2, (int) Math.round(b.getWidth() * f.scale));
                int rh = Math.max(2, (int) Math.round(b.getHeight() * f.scale));
                String t = TunnelColorStore.name(x);
                Color col = t == null ? null : TunnelColorStore.display(proj.getLogisimFile(), c, t);
                g.setColor(col != null ? col : Tokens.TEXT);
                if (col != null) {
                    g.fillRect(rx, ry, rw, rh);
                } else {
                    g.drawRect(rx, ry, rw, rh);
                }
            }
            Rectangle v = viewInCircuit();
            g.setColor(Tokens.BLUE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect((int) f.x(v.x), (int) f.y(v.y), (int) Math.round(v.width * f.scale),
                    (int) Math.round(v.height * f.scale));
        } finally {
            g.dispose();
        }
    }
}
