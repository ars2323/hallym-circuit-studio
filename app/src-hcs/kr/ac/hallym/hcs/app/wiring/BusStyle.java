/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.wiring;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;

import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 버스 모양(E-03, PLAN.md 11.12): 여러 비트 선(버스)을 1비트 선보다 굵게, 그리고 선택하면 버스마다 비트 수를 짧은 빗금과
 * 숫자로({@code /32}). 원조 선 그리기(엔진)는 두고, 원조가 그린 버스 위에 같은 색으로 한 번 더 굵게 그린다. 앱
 * 환경설정이고 파일에는 저장하지 않는다.
 */
public final class BusStyle {
    static final String THICK = "wires.thickBuses";
    static final String WIDTHS = "wires.busWidths";
    /** 굵은 버스 폭(회로 좌표). 원조 선은 3. */
    static final float BUS_WIDTH = com.cburch.logisim.circuit.Wire.WIDTH + 2;

    /** 이번 그리기의 라벨 칩 자리(캔버스에서 그릴 때 WireMarks가 넣는다). */
    static final ThreadLocal<java.util.List<java.awt.Rectangle>> CHIPS = new ThreadLocal<>();

    private BusStyle() {
    }

    public static boolean thick() {
        return Settings.get().getBoolean(THICK, true);
    }

    public static boolean widths() {
        return Settings.get().getBoolean(WIDTHS, false);
    }

    public static void setThick(boolean on) {
        Settings.get().set(THICK, on);
        save();
    }

    public static void setWidths(boolean on) {
        Settings.get().set(WIDTHS, on);
        save();
    }

    private static void save() {
        try {
            Settings.get().save();
        } catch (java.io.IOException e) {
            // 환경설정을 못 써도 이번 실행에는 바뀐다
        }
    }

    /** 폭 2 이상인 선들과 그 폭. */
    static Map<Wire, Integer> buses(Circuit circuit) {
        Map<Wire, Integer> out = new LinkedHashMap<>();
        for (Wire w : circuit.getWires()) {
            BitWidth bw = circuit.getWidth(w.getEnd0());
            if (bw != null && bw.getWidth() >= 2) {
                out.put(w, bw.getWidth());
            }
        }
        return out;
    }

    /** 넷마다 가장 긴 버스 선(비트 수 표시 자리). */
    static Map<Wire, Integer> labelSpots(Circuit circuit, Map<Wire, Integer> buses) {
        Netlist nl = Netlist.of(circuit);
        Map<Netlist.Net, Wire> longest = new LinkedHashMap<>();
        for (Wire w : buses.keySet()) {
            Netlist.Net n = nl.netOf(w);
            Wire best = longest.get(n);
            if (best == null || w.getLength() > best.getLength()) {
                longest.put(n, w);
            }
        }
        Map<Wire, Integer> out = new LinkedHashMap<>();
        for (Wire w : longest.values()) {
            if (w.getLength() >= 30) {
                out.put(w, buses.get(w));
            }
        }
        return out;
    }

    /** 버스를 굵게(원조 선 위에 같은 색으로). WireMarks가 연결점보다 먼저 부른다. */
    static void paintThick(Graphics2D g, Circuit circuit, WireMarks.Colors colors, Set<Component> hidden,
            Map<Wire, Integer> buses) {
        g.setStroke(new BasicStroke(BUS_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Wire w : buses.keySet()) {
            if (hidden != null && hidden.contains(w)) {
                continue;
            }
            g.setColor(colors.at(w.getEnd0(), null));
            g.draw(new Line2D.Float(w.getEnd0().getX(), w.getEnd0().getY(), w.getEnd1().getX(), w.getEnd1().getY()));
        }
    }

    /**
     * 비트 수: 가운데에 짧은 빗금과 숫자. 글자는 확대 비율과 상관없이 읽히는 크기. 숫자는 선의 한쪽(세로선은 오른쪽,
     * 가로선은 위)에, 막히면 반대쪽에 두고, 양쪽 다 다른 선·부품에 닿으면 이 선은 표시하지 않는다(촘촘한 묶음).
     */
    static void paintWidths(Graphics2D g, Circuit circuit, Set<Component> hidden, Map<Wire, Integer> buses,
            double z) {
        float px = (float) Math.max(9, 9 / Math.max(z, 0.01));
        g.setFont(new Font(Tokens.UI_FONT, Font.PLAIN, 1).deriveFont(px));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.setStroke(new BasicStroke((float) Math.max(1, 1.2 / z)));
        g.setColor(Tokens.TEXT_2);
        java.util.List<java.awt.Rectangle> obstacles = new java.util.ArrayList<>();
        for (Component c : circuit.getNonWires()) {
            com.cburch.logisim.data.Bounds b = c.getBounds();
            obstacles.add(new java.awt.Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
        }
        if (CHIPS.get() != null) {
            obstacles.addAll(CHIPS.get());
        }
        for (Map.Entry<Wire, Integer> e : labelSpots(circuit, buses).entrySet()) {
            Wire w = e.getKey();
            if (hidden != null && hidden.contains(w)) {
                continue;
            }
            int mx = (w.getEnd0().getX() + w.getEnd1().getX()) / 2;
            int my = (w.getEnd0().getY() + w.getEnd1().getY()) / 2;
            String t = Integer.toString(e.getValue());
            int tw = fm.stringWidth(t);
            int th = fm.getAscent();
            java.awt.Rectangle[] sides = w.isVertical()
                    ? new java.awt.Rectangle[] {new java.awt.Rectangle(mx + 6, my - th / 2, tw, th),
                        new java.awt.Rectangle(mx - 6 - tw, my - th / 2, tw, th)}
                    : new java.awt.Rectangle[] {new java.awt.Rectangle(mx - tw / 2, my - 6 - th, tw, th),
                        new java.awt.Rectangle(mx - tw / 2, my + 6, tw, th)};
            java.awt.Rectangle spot = null;
            for (java.awt.Rectangle r : sides) {
                if (clear(circuit, w, r, obstacles)) {
                    spot = r;
                    break;
                }
            }
            if (spot == null) {
                continue;
            }
            g.draw(new Line2D.Float(mx - 4, my + 4, mx + 4, my - 4));
            g.drawString(t, spot.x, spot.y + th);
        }
    }

    /** 숫자 자리가 다른 선·부품에 닿지 않는가(제 선은 뺀다). */
    static boolean clear(Circuit circuit, Wire own, java.awt.Rectangle r, java.util.List<java.awt.Rectangle> obstacles) {
        java.awt.Rectangle grown = new java.awt.Rectangle(r.x - 2, r.y - 2, r.width + 4, r.height + 4);
        for (Wire o : circuit.getWires()) {
            if (o == own) {
                continue;
            }
            com.cburch.logisim.data.Bounds b = o.getBounds();
            if (grown.intersects(new java.awt.Rectangle(b.getX() - 1, b.getY() - 1, b.getWidth() + 2,
                    b.getHeight() + 2))) {
                return false;
            }
        }
        for (java.awt.Rectangle o : obstacles) {
            if (grown.intersects(o)) {
                return false;
            }
        }
        return true;
    }
}
