/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.groups;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 그룹 색 보기(E-04): "Colors: Groups"일 때 그룹이 있는 넷의 선 양옆에 그룹 색의 얇은 테두리를 그린다. 선 자체(값 색)는
 * 비워 둔다. 파일에는 그룹만 저장하고 보기 설정은 앱 환경설정이다.
 */
public final class GroupOverlay {
    /** 테두리 바깥 폭과 비워 둘 선 폭(회로 좌표). */
    static final float OUTER = 9f;
    static final float HOLE = com.cburch.logisim.circuit.Wire.WIDTH + 3;

    private GroupOverlay() {
    }

    /** WireMarks가 부른다. */
    public static void paint(Canvas canvas, Graphics g0, Circuit circuit, Set<Component> hidden) {
        if (!SignalGroups.showGroups() || !(g0 instanceof Graphics2D) || canvas.getProject() == null) {
            return;
        }
        Map<Netlist.Net, SignalGroups.Group> groups = SignalGroups.of(canvas.getProject().getLogisimFile(), circuit);
        if (groups.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            BasicStroke outer = new BasicStroke(OUTER, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
            BasicStroke inner = new BasicStroke(HOLE, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND);
            for (Map.Entry<Netlist.Net, SignalGroups.Group> e : groups.entrySet()) {
                Area rails = new Area();
                Area hole = new Area();
                for (Wire w : e.getKey().wires()) {
                    if (hidden != null && hidden.contains(w)) {
                        continue;
                    }
                    Line2D line = new Line2D.Double(w.getEnd0().getX(), w.getEnd0().getY(), w.getEnd1().getX(),
                            w.getEnd1().getY());
                    rails.add(new Area(outer.createStrokedShape(line)));
                    hole.add(new Area(inner.createStrokedShape(line)));
                }
                rails.subtract(hole);
                g.setColor(e.getValue().color);
                g.fill(rails);
            }
        } finally {
            g.dispose();
        }
    }

    /** 상태 표시줄 단추: Colors: Values ↔ Colors: Groups. */
    public static JButton button(Project proj) {
        JButton b = new JButton(label());
        b.setFocusable(false);
        b.setToolTipText(Messages.get("group.modeTip"));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setForeground(Tokens.TEXT_2);
        b.addActionListener(e -> {
            SignalGroups.setShowGroups(!SignalGroups.showGroups());
            b.setText(label());
            proj.repaintCanvas();
        });
        return b;
    }

    static String label() {
        return Messages.get(SignalGroups.showGroups() ? "group.modeGroups" : "group.modeValues");
    }
}
