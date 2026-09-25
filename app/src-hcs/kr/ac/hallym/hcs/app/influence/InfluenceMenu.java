/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.influence;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.menu.ContextMenus;
import kr.ac.hallym.hcs.app.model.Influence;

/**
 * 우클릭 "Influence" 묶음(P-01): Show Influence (Forward / Backward / Both), Path Between Selected(부품 둘을
 * 골랐을 때), Through Registers, Clear Influence. 키는 I(앞), Shift+I(뒤), [ ](좁히기·넓히기), Esc(지우기).
 */
public final class InfluenceMenu implements ContextMenus.Provider {
    @Override
    public void contribute(ContextMenus.Target t, JPopupMenu menu) {
        InfluenceOverlay o = InfluenceOverlay.of(t.project);
        List<Component> starts = starts(t);
        JMenu m = new JMenu(Messages.get("influence.menu"));
        if (!starts.isEmpty()) {
            for (Influence.Mode mode : Influence.Mode.values()) {
                JMenuItem it = new JMenuItem(Messages.get("influence.show." + mode.name()));
                it.addActionListener(e -> o.show(t.circuit, starts, mode));
                m.add(it);
            }
        }
        List<Component> parts = new ArrayList<>();
        for (Component c : t.selection) {
            if (!(c instanceof Wire)) {
                parts.add(c);
            }
        }
        if (parts.size() == 2) {
            JMenuItem it = new JMenuItem(Messages.get("influence.between"));
            it.addActionListener(e -> o.between(t.circuit, parts.get(0), parts.get(1)));
            m.add(it);
        }
        JCheckBoxMenuItem through = new JCheckBoxMenuItem(Messages.get("influence.throughRegisters"), o.through());
        through.addActionListener(e -> o.setThrough(through.isSelected()));
        m.add(through);
        if (o.active()) {
            JMenuItem clear = new JMenuItem(Messages.get("influence.clear"));
            clear.addActionListener(e -> o.clear());
            m.add(clear);
        }
        if (m.getItemCount() > 1 || o.active()) {
            menu.add(m);
        }
    }

    /** 누른 부품·선. 누른 것이 선택 안에 있으면 선택 전체. */
    static List<Component> starts(ContextMenus.Target t) {
        List<Component> ret = new ArrayList<>();
        if (t.component == null) {
            return ret;
        }
        if (t.selection.contains(t.component) && t.selection.size() > 1) {
            ret.addAll(t.selection);
        } else {
            ret.add(t.component);
        }
        return ret;
    }
}
