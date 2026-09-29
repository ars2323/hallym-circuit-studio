/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.probe.QuickProbe;

/**
 * 우클릭 메뉴의 머리 줄(검토 반영 1): 대상 요약 한 줄. 엔진의 {@code menu.facts}가 쓴다. v1 Swing 메뉴를 다시 짜던
 * 부분은 화면 코드와 함께 지웠다(N-27, D-163, 옛 코드는 태그 {@code swing-final}). GUI 없이 테스트한다.
 */
public final class MenuLayout {
    private MenuLayout() {
    }

    /**
     * 대상 요약 한 줄. 예: {@code AND #1 · 입력 in1 · 1비트}, {@code 넷 aluResult · 32비트}, {@code PC(레지스터) · 32비트},
     * {@code 부품 3개}, {@code 빈 곳 · main}.
     */
    public static String summary(Circuit circuit, Component c, Location p, int selected) {
        if (selected >= 2) {
            return Messages.get("menu.sum.many", selected);
        }
        if (c == null) {
            return Messages.get("menu.sum.empty", circuit.getName());
        }
        if (c instanceof Wire) {
            BitWidth w = circuit.getWidth(((Wire) c).getEnd0());
            String net = QuickProbe.netName(circuit, Netlist.of(circuit).netOf((Wire) c));
            String head = net.isEmpty() ? Messages.get("menu.sum.wire") : Messages.get("menu.sum.net", net);
            return w == null || w.getWidth() <= 0 ? head : head + " · " + Messages.get("menu.sum.bits", w.getWidth());
        }
        String name = Names.name(circuit, c);
        if (Names.label(c) != null) {
            name = name + "(" + c.getFactory().getDisplayName() + ")";
        }
        int port = portAt(c, p);
        if (port >= 0) {
            EndData e = c.getEnds().get(port);
            String dir = e.isInput() && e.isOutput() ? "menu.sum.io" : e.isInput() ? "menu.sum.in" : "menu.sum.out";
            return name + " · " + Messages.get(dir, Kinds.portName(c, port)) + " · "
                    + Messages.get("menu.sum.bits", e.getWidth().getWidth());
        }
        List<String> parts = new ArrayList<>();
        parts.add(name);
        Object inputs = value(c, "inputs");
        if (inputs != null && Kinds.of(c).category() == Kinds.Category.GATE) {
            parts.add(Messages.get("menu.sum.inputs", inputs));
        }
        Object width = value(c, "width");
        if (width == null) {
            width = value(c, "dataWidth");
        }
        if (width instanceof BitWidth) {
            parts.add(Messages.get("menu.sum.bits", ((BitWidth) width).getWidth()));
        }
        return String.join(" · ", parts);
    }

    /** p에서 5px 안의 포트 번호. 없으면 -1(v1 EditMenus.portAt). */
    static int portAt(Component c, Location p) {
        for (int i = 0; i < c.getEnds().size(); i++) {
            Location e = c.getEnds().get(i).getLocation();
            if (Math.abs(e.getX() - p.getX()) <= 5 && Math.abs(e.getY() - p.getY()) <= 5) {
                return i;
            }
        }
        return -1;
    }

    private static Object value(Component c, String name) {
        Attribute<?> a = c.getAttributeSet().getAttribute(name);
        return a == null ? null : c.getAttributeSet().getValue(a);
    }
}
