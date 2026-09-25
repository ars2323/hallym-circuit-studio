/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.sim;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

import javax.swing.Icon;

import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 툴바 아이콘(#77). 글꼴에 없는 기호가 네모로 보이지 않도록 선과 다각형으로 직접 그린다. 기본 navy, 16px.
 */
final class BarIcons implements Icon {
    static final int SIZE = 16;
    private final String name;

    BarIcons(String name) {
        this.name = name;
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c != null && !c.isEnabled() ? Tokens.GRAY : Tokens.NAVY);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
        case "new":
            g.drawRect(3, 1, 10, 14);
            g.drawLine(6, 8, 10, 8);
            g.drawLine(8, 6, 8, 10);
            break;
        case "open":
            g.drawRect(1, 5, 14, 9);
            g.drawLine(1, 5, 5, 2);
            g.drawLine(5, 2, 9, 2);
            break;
        case "save":
            g.drawRect(2, 2, 12, 12);
            g.drawRect(5, 2, 6, 4);
            g.drawRect(5, 9, 6, 5);
            break;
        case "undo":
            g.draw(new Arc2D.Double(3, 4, 10, 9, 90, -250, Arc2D.OPEN));
            g.fillPolygon(new Polygon(new int[] {1, 7, 6}, new int[] {5, 2, 8}, 3));
            break;
        case "redo":
            g.draw(new Arc2D.Double(3, 4, 10, 9, 90, 250, Arc2D.OPEN));
            g.fillPolygon(new Polygon(new int[] {15, 9, 10}, new int[] {5, 2, 8}, 3));
            break;
        case "text":
            g.drawLine(3, 3, 13, 3);
            g.drawLine(8, 3, 8, 14);
            g.drawLine(6, 14, 10, 14);
            break;
        case "select":
            g.fillPolygon(new Polygon(new int[] {3, 3, 7, 9, 11, 9, 13}, new int[] {1, 14, 10, 15, 14, 9, 9}, 7));
            break;
        case "poke":
            g.drawRoundRect(5, 6, 7, 8, 3, 3);
            g.drawLine(7, 6, 7, 1);
            g.drawLine(9, 6, 9, 3);
            break;
        case "wire":
            g.drawLine(1, 12, 7, 12);
            g.drawLine(7, 12, 7, 4);
            g.drawLine(7, 4, 15, 4);
            g.fillOval(5, 10, 4, 4);
            break;
        case "pin":
            g.drawRect(2, 4, 8, 8);
            g.drawLine(10, 8, 15, 8);
            break;
        case "tunnel":
            g.drawPolygon(new Polygon(new int[] {1, 5, 15, 15, 5}, new int[] {8, 4, 4, 12, 12}, 5));
            break;
        case "probe":
            g.drawOval(2, 3, 10, 10);
            g.drawLine(12, 8, 15, 8);
            break;
        case "run":
            g.fillPolygon(new Polygon(new int[] {4, 4, 13}, new int[] {2, 14, 8}, 3));
            break;
        case "cycle":
            g.fillPolygon(new Polygon(new int[] {2, 2, 10}, new int[] {2, 14, 8}, 3));
            g.fillRect(11, 2, 3, 12);
            break;
        case "cycles":
            g.fillPolygon(new Polygon(new int[] {1, 1, 8}, new int[] {2, 14, 8}, 3));
            g.fillPolygon(new Polygon(new int[] {8, 8, 15}, new int[] {2, 14, 8}, 3));
            break;
        case "reset":
            g.draw(new Arc2D.Double(2, 2, 12, 12, 60, 290, Arc2D.OPEN));
            g.fillPolygon(new Polygon(new int[] {15, 10, 14}, new int[] {1, 3, 7}, 3));
            break;
        case "program":
            g.drawRect(2, 1, 12, 14);
            g.drawLine(5, 5, 11, 5);
            g.drawLine(5, 8, 11, 8);
            g.drawLine(5, 11, 9, 11);
            break;
        default:
            g.drawRect(2, 2, 12, 12);
            break;
        }
        g.dispose();
    }
}
