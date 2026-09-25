/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;

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
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 우클릭 메뉴의 순서와 머리 줄(검토 반영 1). 맨 위에 대상 요약 한 줄, 그다음 대상별 동작, 공통(복제·속성 패널 등),
 * 맨 아래 삭제. 항목의 묶음은 {@link #GROUP} 속성으로 정한다(없으면 대상별). GUI 없이 테스트한다.
 */
public final class MenuLayout {
    /** 메뉴 항목의 묶음을 적는 클라이언트 속성. 값: {@link #COMMON}, {@link #DELETE}. 없으면 대상별. */
    public static final String GROUP = "hcs.menuGroup";
    public static final String COMMON = "common";
    public static final String DELETE = "delete";
    /** 머리 줄 표시. */
    static final String HEADER = "hcs.menuHeader";

    private MenuLayout() {
    }

    /** 항목에 묶음을 단다. */
    public static <T extends JComponent> T group(T item, String group) {
        item.putClientProperty(GROUP, group);
        return item;
    }

    static String groupOf(java.awt.Component c) {
        Object g = c instanceof JComponent ? ((JComponent) c).getClientProperty(GROUP) : null;
        return g == null ? "" : g.toString();
    }

    /**
     * 메뉴를 다시 짠다. parts는 묶음(원조 부품 항목, 우리 제공자별 항목)이고, 각 묶음 안 순서는 지킨다. 구분선은
     * 묶음 사이와 대상별·공통·삭제 사이에 하나씩 둔다.
     */
    static JPopupMenu arrange(String summary, List<List<java.awt.Component>> parts) {
        List<List<java.awt.Component>> specific = new ArrayList<>();
        List<java.awt.Component> common = new ArrayList<>();
        List<java.awt.Component> delete = new ArrayList<>();
        for (List<java.awt.Component> part : parts) {
            List<java.awt.Component> mine = new ArrayList<>();
            for (java.awt.Component c : part) {
                if (c instanceof JPopupMenu.Separator) {
                    continue;
                }
                String g = groupOf(c);
                if (g.equals(COMMON)) {
                    common.add(c);
                } else if (g.equals(DELETE)) {
                    delete.add(c);
                } else {
                    mine.add(c);
                }
            }
            if (!mine.isEmpty()) {
                specific.add(mine);
            }
        }
        JPopupMenu m = new JPopupMenu();
        if (summary != null && !summary.isEmpty()) {
            JLabel head = new JLabel(summary);
            head.setFont(head.getFont().deriveFont(Font.BOLD));
            head.setForeground(Tokens.TEXT);
            head.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            head.putClientProperty(HEADER, Boolean.TRUE);
            m.add(head);
        }
        for (List<java.awt.Component> s : specific) {
            separate(m);
            for (java.awt.Component c : s) {
                m.add(c);
            }
        }
        if (!common.isEmpty()) {
            separate(m);
            for (java.awt.Component c : common) {
                m.add(c);
            }
        }
        if (!delete.isEmpty()) {
            separate(m);
            for (java.awt.Component c : delete) {
                m.add(c);
            }
        }
        return m;
    }

    private static void separate(JPopupMenu m) {
        if (m.getComponentCount() > 0) {
            m.addSeparator();
        }
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
        int port = EditMenus.portAt(c, p);
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

    private static Object value(Component c, String name) {
        Attribute<?> a = c.getAttributeSet().getAttribute(name);
        return a == null ? null : c.getAttributeSet().getValue(a);
    }
}
