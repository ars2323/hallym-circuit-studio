/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.tools.ToolTipMaker;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.probe.QuickProbe;

/**
 * 마우스 오버 정보(#79, PLAN.md 11.12). 부품은 전체 경로({@code main › datapath › alu › AND #3}), 라벨, 입력 수,
 * 폭, 이름 있는 넷에 닿은 포트를 보이고, 선은 경로, 폭, 넷 이름을 보인다. 이름과 경로는 공용 식별자 규칙
 * (D-030)이다. 사실만 적고 회로가 맞는지는 말하지 않는다. GUI 없이 테스트한다.
 */
public final class HoverInfo {
    /** 포트-넷 이름을 몇 개까지 보일지. */
    static final int MAX_NETS = 4;

    private HoverInfo() {
    }

    /**
     * 캔버스 툴팁: 점 p의 부품이나 선 정보(HTML). 부품에 원조 툴팁이 있으면 마지막 줄로 붙인다. 없으면 null.
     */
    public static String tip(CircuitState state, Location p, com.cburch.logisim.gui.main.Canvas canvas) {
        if (state == null) {
            return null;
        }
        List<String> l = lines(state, p);
        if (l == null) {
            return null;
        }
        Component hit = hit(state.getCircuit(), p);
        Object maker = hit == null ? null : hit.getFeature(ToolTipMaker.class);
        if (maker instanceof ToolTipMaker && canvas != null) {
            String t = ((ToolTipMaker) maker).getToolTip(new ComponentUserEvent(canvas, p.getX(), p.getY()));
            if (t != null && !t.isEmpty()) {
                l.add(t);
            }
        }
        return html(l);
    }

    /**
     * 도움말 자리(캔버스 px, S-08): 마우스 아래 부품이 있으면 그 부품(과 둘레 칩 여유) 오른쪽 위에 둔다. 오른쪽에 자리가
     * 없으면 왼쪽 위. 부품이 없으면 null(Swing 기본 자리, 마우스 아래). 원조 도움말은 마우스 바로 아래에 떠 부품과
     * 캡션 칩을 가렸다.
     */
    public static java.awt.Point location(com.cburch.logisim.gui.main.Canvas canvas, int x, int y) {
        if (canvas.getCircuit() == null) {
            return null;
        }
        Location p = canvas.hcsToCircuit(x, y);
        Component hit = hit(canvas.getCircuit(), p);
        if (hit == null) {
            return null;
        }
        com.cburch.logisim.data.Bounds b = hit.getBounds();
        java.awt.Rectangle r = canvas.hcsToScreen(new java.awt.Rectangle(b.getX(), b.getY(), b.getWidth(),
                b.getHeight()));
        java.awt.Rectangle vis = canvas.getVisibleRect();
        java.util.List<java.awt.Rectangle> chips = new java.util.ArrayList<>();
        for (java.awt.Rectangle c : LabelOverlay.chipRects(canvas)) {
            chips.add(canvas.hcsToScreen(c));
        }
        return choose(vis, r, chips);
    }

    static final int TIP_W = 200; // 도움말 폭·높이 어림(px)
    static final int TIP_H = 60;

    /**
     * 부품 r(화면 px) 둘레에서 라벨·값 칩(화면 px)을 가리지 않는 첫 자리: 오른쪽 위, 오른쪽 아래, 왼쪽 위, 왼쪽 아래,
     * 위, 아래 차례. 모두 가리면 첫 자리(Q-03 검토: 도움말이 값 칩을 덮었다). GUI 없이 테스트한다.
     */
    static java.awt.Point choose(java.awt.Rectangle vis, java.awt.Rectangle r, java.util.List<java.awt.Rectangle> chips) {
        int gap = 16; // 포트 이름·칩이 부품 옆에 붙어 있어 조금 띄운다
        int right = r.x + r.width + gap;
        int left = r.x - gap - TIP_W;
        int top = Math.max(vis.y, r.y - 8);
        int below = r.y + r.height + gap;
        int above = Math.max(vis.y, r.y - gap - TIP_H);
        int[][] tries = {{right, top}, {right, below}, {left, top}, {left, below}, {r.x, above}, {r.x, below}};
        java.awt.Point first = null;
        for (int[] t : tries) {
            java.awt.Rectangle box = new java.awt.Rectangle(t[0], t[1], TIP_W, TIP_H);
            boolean inside = box.x >= vis.x && box.x + TIP_W <= vis.x + vis.width && box.y >= vis.y;
            if (!inside) {
                continue;
            }
            if (first == null) {
                first = new java.awt.Point(t[0], t[1]);
            }
            boolean clear = true;
            for (java.awt.Rectangle c : chips) {
                if (c.intersects(box)) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return new java.awt.Point(t[0], t[1]);
            }
        }
        return first != null ? first : new java.awt.Point(right, top);
    }

    /** 점 p를 덮는 부품(선 제외). 없으면 null. */
    static Component hit(Circuit circuit, Location p) {
        for (Component c : circuit.getAllContaining(p)) {
            if (!(c instanceof Wire)) {
                return c;
            }
        }
        return null;
    }

    /** 점 p에 있는 부품이나 선의 정보(글자 줄들). 없으면 null. */
    public static List<String> lines(CircuitState state, Location p) {
        Circuit circuit = state.getCircuit();
        Component hit = hit(circuit, p);
        Wire wire = null;
        for (Component c : circuit.getAllContaining(p)) {
            if (c instanceof Wire && wire == null) {
                wire = (Wire) c;
            }
        }
        if (hit != null) {
            return component(state, hit);
        }
        return wire == null ? null : wire(state, wire);
    }

    /** 부품 정보. */
    public static List<String> component(CircuitState state, Component c) {
        Circuit circuit = state.getCircuit();
        List<String> ret = new ArrayList<>();
        ret.add(Names.componentPath(state, c));
        List<String> facts = new ArrayList<>();
        String label = Names.label(c);
        if (label != null) {
            facts.add(Messages.get("hover.label", label));
        }
        Object inputs = value(c, "inputs");
        if (inputs != null && Kinds.of(c).category() == Kinds.Category.GATE) {
            facts.add(Messages.get("hover.inputs", inputs));
        }
        Object width = value(c, "width");
        if (width == null) {
            width = value(c, "dataWidth");
        }
        if (width instanceof BitWidth) {
            facts.add(Messages.get("hover.width", ((BitWidth) width).getWidth()));
        }
        if (!facts.isEmpty()) {
            ret.add(String.join(" · ", facts));
        }
        // 기본 모양 서브회로: 상자가 작아 포트 이름이 잘 안 보이므로 포트 이름 목록(S-08)
        if (c.getFactory() instanceof com.cburch.logisim.circuit.SubcircuitFactory
                && ((com.cburch.logisim.circuit.SubcircuitFactory) c.getFactory()).getSubcircuit().getAppearance()
                        .isDefaultAppearance()) {
            List<String> in = new ArrayList<>();
            List<String> out = new ArrayList<>();
            for (int i = 0; i < c.getEnds().size(); i++) {
                (c.getEnd(i).isOutput() ? out : in).add(Kinds.portName(c, i));
            }
            ret.add(Messages.get("hover.ports", String.join(", ", in), String.join(", ", out)));
        }
        Netlist nl = Netlist.of(circuit);
        List<String> nets = new ArrayList<>();
        for (int i = 0; i < c.getEnds().size() && nets.size() < MAX_NETS; i++) {
            String n = QuickProbe.netName(circuit, nl.netOf(c, i));
            String port = Kinds.portName(c, i);
            if (!n.isEmpty() && !n.equals(label) && !n.equals(port)) {
                nets.add(port + " = " + n);
            }
        }
        if (!nets.isEmpty()) {
            ret.add(Messages.get("hover.nets", String.join(", ", nets)));
        }
        return ret;
    }

    /** 선 정보. */
    public static List<String> wire(CircuitState state, Wire w) {
        Circuit circuit = state.getCircuit();
        List<String> ret = new ArrayList<>();
        ret.add(Names.path(Names.path(Names.circuitPath(state)), Messages.get("hover.wire")));
        List<String> facts = new ArrayList<>();
        BitWidth bw = circuit.getWidth(w.getEnd0());
        if (bw != null && bw.getWidth() > 0) {
            facts.add(Messages.get("hover.width", bw.getWidth()));
        }
        String n = QuickProbe.netName(circuit, Netlist.of(circuit).netOf(w));
        if (!n.isEmpty()) {
            facts.add(Messages.get("hover.net", n));
        }
        if (!facts.isEmpty()) {
            ret.add(String.join(" · ", facts));
        }
        // E-03: 선 색의 뜻
        boolean widthError = false;
        java.util.Set<com.cburch.logisim.circuit.WidthIncompatibilityData> bad = circuit.getWidthIncompatibilityData();
        if (bad != null) {
            for (com.cburch.logisim.circuit.WidthIncompatibilityData d : bad) {
                for (int i = 0; i < d.size(); i++) {
                    widthError |= w.contains(d.getPoint(i));
                }
            }
        }
        String meaning = kr.ac.hallym.hcs.app.wiring.WireLegend.meaningKey(state.getValue(w.getEnd0()), widthError);
        if (meaning != null) {
            ret.add(Messages.get("hover.color", Messages.get(meaning)));
        }
        return ret;
    }

    /** 툴팁 HTML(첫 줄 굵게). */
    public static String html(List<String> lines) {
        StringBuilder sb = new StringBuilder("<html>");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("<br>");
            }
            String s = esc(lines.get(i));
            sb.append(i == 0 ? "<b>" + s + "</b>" : s);
        }
        return sb.append("</html>").toString();
    }

    private static Object value(Component c, String name) {
        Attribute<?> a = c.getAttributeSet().getAttribute(name);
        return a == null ? null : c.getAttributeSet().getValue(a);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
