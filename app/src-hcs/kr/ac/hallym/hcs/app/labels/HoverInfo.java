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
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;

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

    /** 캔버스 툴팁: 점 p의 부품이나 선 정보(HTML). 없으면 null. */
    public static String tip(CircuitState state, Location p) {
        if (state == null) {
            return null;
        }
        List<String> l = lines(state, p);
        return l == null ? null : html(l);
    }

    /** 점 p에 있는 부품이나 선의 정보(글자 줄들). 없으면 null. */
    public static List<String> lines(CircuitState state, Location p) {
        Circuit circuit = state.getCircuit();
        Component hit = null;
        Wire wire = null;
        for (Component c : circuit.getAllContaining(p)) {
            if (c instanceof Wire) {
                wire = wire == null ? (Wire) c : wire;
            } else if (hit == null) {
                hit = c;
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
