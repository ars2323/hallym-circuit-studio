/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.splitter;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.menu.ContextMenus;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 스플리터 우클릭 항목(#105): 스플리터 "스플리터 편집…", 여러 비트 선 "비트 나누기…"와 "비트 하나 뽑기 [n]",
 * 여러 선을 고르면 "하나의 버스로 합치기". 합치기는 고른 순서(먼저 고른 선이 위 = MSB)와 각 선의 폭만 쓴다.
 */
public final class SplitterMenu implements ContextMenus.Provider {
    @Override
    public void contribute(ContextMenus.Target t, JPopupMenu menu) {
        if (t.component != null && t.component.getFactory().getName().equals("Splitter")) {
            JMenuItem edit = new JMenuItem(Messages.get("splitter.edit"));
            edit.addActionListener(e -> SplitterEditor.editExisting(t.project, t.circuit, t.component));
            menu.add(edit);
        }
        List<Wire> wires = selectedWires(t);
        if (wires.size() >= 2) {
            JMenuItem combine = new JMenuItem(Messages.get("splitter.combine", wires.size()));
            boolean widthsKnown = true;
            for (Wire w : wires) {
                widthsKnown &= width(t.circuit, w) > 0;
            }
            if (!t.selectionOrderKnown) {
                // 사각형으로 한꺼번에 고르면 순서가 없다: 도구가 순서를 정하지 않는다(1장 원칙)
                combine.setEnabled(false);
                combine.setToolTipText(Messages.get("splitter.combineNeedsOrder"));
                combine.setText(Messages.get("splitter.combineNeedsOrder"));
            } else if (!widthsKnown) {
                combine.setEnabled(false);
                combine.setToolTipText(Messages.get("splitter.combineUnknownWidth"));
            }
            combine.addActionListener(e -> combine(t.project, t.circuit, wires));
            menu.add(combine);
        } else if (t.isWire()) {
            Wire w = (Wire) t.component;
            int width = width(t.circuit, w);
            if (width > 1) {
                Location at = onWire(w, t.point);
                JMenuItem split = new JMenuItem(Messages.get("splitter.split"));
                split.addActionListener(e -> SplitterEditor.createNew(t.project, t.circuit, at, width));
                menu.add(split);
                JMenu extract = new JMenu(Messages.get("splitter.extract"));
                for (int b = width - 1; b >= 0; b--) {
                    int bit = b;
                    JMenuItem item = new JMenuItem("[" + b + "]");
                    item.addActionListener(e -> extract(t.project, t.circuit, at, width, bit));
                    extract.add(item);
                }
                menu.add(extract);
            }
        }
    }

    /** 고른 것이 모두 선일 때 고른 순서의 선들. */
    static List<Wire> selectedWires(ContextMenus.Target t) {
        List<Wire> ret = new ArrayList<>();
        for (Component c : t.selection) {
            if (!(c instanceof Wire)) {
                return new ArrayList<>();
            }
            ret.add((Wire) c);
        }
        return ret;
    }

    /** 선의 폭(원조가 계산한 그 점의 폭). 모르면 넷의 포트 폭. */
    static int width(Circuit circuit, Wire w) {
        BitWidth bw = circuit.getWidth(w.getEnd0());
        if (bw != null && bw.getWidth() > 0) {
            return bw.getWidth();
        }
        Netlist.Net net = Netlist.of(circuit).netOf(w);
        return net == null ? 0 : net.width();
    }

    /** 누른 점을 선 위 격자점으로. */
    static Location onWire(Wire w, Location p) {
        int x = Math.round(p.getX() / 10f) * 10;
        int y = Math.round(p.getY() / 10f) * 10;
        Location a = w.getEnd0();
        Location b = w.getEnd1();
        if (a.getY() == b.getY()) {
            x = Math.max(Math.min(a.getX(), b.getX()), Math.min(Math.max(a.getX(), b.getX()), x));
            return Location.create(x, a.getY());
        }
        y = Math.max(Math.min(a.getY(), b.getY()), Math.min(Math.max(a.getY(), b.getY()), y));
        return Location.create(a.getX(), y);
    }

    static void extract(Project proj, Circuit circuit, Location at, int width, int bit) {
        SplitterSpec spec = SplitterSpec.extract(width, bit);
        CircuitMutation m = SplitterEdits.create(proj.getLogisimFile(), circuit, at, Direction.EAST, spec);
        proj.doAction(m.toAction(() -> Messages.get("splitter.extractAction", bit)));
    }

    /** 고른 순서대로 한 버스로. 새 스플리터는 고른 선들 오른쪽에 두고(팔이 왼쪽), 연결은 학생이 한다. */
    static void combine(Project proj, Circuit circuit, List<Wire> wires) {
        List<Integer> widths = new ArrayList<>();
        Bounds box = null;
        for (Wire w : wires) {
            widths.add(width(circuit, w));
            box = box == null ? w.getBounds() : box.add(w.getBounds());
        }
        // 팔 이름은 학생이 편집기에서 직접 붙인다(도구가 채운 이름은 저장하지 않는다)
        SplitterSpec spec = SplitterSpec.combine(widths, null);
        if (spec.width() > 32) {
            javax.swing.JOptionPane.showMessageDialog(proj.getFrame(), Messages.get("splitter.tooWide", spec.width()));
            return;
        }
        int x = (box.getX() + box.getWidth() + 60) / 10 * 10;
        int y = (box.getY() + box.getHeight() / 2) / 10 * 10;
        Location at = Location.create(x, y);
        CircuitMutation m = SplitterEdits.create(proj.getLogisimFile(), circuit, at, Direction.WEST, spec);
        proj.doAction(m.toAction(() -> Messages.get("splitter.combineAction")));
    }
}
