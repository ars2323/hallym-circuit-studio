/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.probe;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.menu.ContextMenus;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 빠른 프로브 메뉴와 단축키(#75): 선 우클릭 "프로브 붙이기"(16진·10진 부호·10진·2진), 선 위에서 P(16진, 1비트는 2진),
 * 빈 곳 "모든 프로브 선택/삭제". 부호는 붙인 프로브의 진법 속성으로 바꾼다.
 */
public final class ProbeMenu implements ContextMenus.Provider {
    @Override
    public void contribute(ContextMenus.Target t, JPopupMenu menu) {
        if (t.isWire() && t.selection.size() <= 1) {
            Wire w = (Wire) t.component;
            JMenu attach = new JMenu(Messages.get("probe.attach"));
            for (String radix : QuickProbe.RADICES) {
                JMenuItem it = new JMenuItem(Messages.get("probe.radix." + radix));
                it.addActionListener(e -> attach(t.project, t.circuit, w, t.point, radix));
                attach.add(it);
            }
            menu.add(attach);
        } else if (t.component == null) {
            List<Component> probes = QuickProbe.probes(t.circuit);
            if (!probes.isEmpty()) {
                JMenuItem sel = new JMenuItem(Messages.get("probe.selectAll", probes.size()));
                sel.addActionListener(e -> {
                    t.project.doAction(SelectionActions.dropAll(t.project.getSelection()));
                    t.project.getSelection().addAll(probes);
                });
                menu.add(sel);
                JMenuItem del = new JMenuItem(Messages.get("probe.deleteAll", probes.size()));
                del.addActionListener(e -> {
                    CircuitMutation m = QuickProbe.removeAll(t.circuit, probes);
                    t.project.doAction(m.toAction(() -> Messages.get("probe.deleteAllAction")));
                });
                menu.add(del);
            }
        }
    }

    static void attach(Project proj, Circuit c, Wire w, Location near, String radix) {
        QuickProbe.Placement pl = QuickProbe.find(proj.getLogisimFile(), c, w, near);
        if (pl == null) {
            JOptionPane.showMessageDialog(proj.getFrame(), Messages.get("probe.noRoom"));
            return;
        }
        String label = QuickProbe.netName(c, Netlist.of(c).netOf(w));
        proj.doAction(QuickProbe.place(proj.getLogisimFile(), c, pl, radix, label)
                .toAction(() -> Messages.get("probe.attachAction")));
    }

    /** 캔버스에서 P: 포인터 아래 선에 프로브. */
    public static void installKey(Canvas canvas) {
        Location[] last = {null};
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                last[0] = Location.create(e.getX(), e.getY()); // 캔버스가 이미 논리 좌표로 바꿔 준다
            }
        });
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_P || e.getModifiersEx() != 0 || last[0] == null
                        || canvas.getProject().getTool() instanceof com.cburch.logisim.tools.TextTool) {
                    return;
                }
                Circuit c = canvas.getCircuit();
                for (Component comp : c.getAllContaining(last[0])) {
                    if (comp instanceof Wire) {
                        Netlist.Net net = Netlist.of(c).netOf((Wire) comp);
                        String radix = net != null && net.width() == 1 ? "2" : "16";
                        attach(canvas.getProject(), c, (Wire) comp, last[0], radix);
                        e.consume();
                        return;
                    }
                }
            }
        });
    }
}
