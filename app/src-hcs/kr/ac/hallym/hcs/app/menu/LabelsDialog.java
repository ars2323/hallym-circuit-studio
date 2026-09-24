/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Names;

/** 라벨 일괄 편집(#72): 고른 부품의 라벨을 한 창에서 고치고 되돌리기 한 번으로 적용한다. */
final class LabelsDialog {
    private LabelsDialog() {
    }

    static void show(ContextMenus.Target t, List<Component> comps) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 4, 2, 4);
        g.anchor = GridBagConstraints.WEST;
        List<JTextField> fields = new ArrayList<>();
        for (int i = 0; i < comps.size(); i++) {
            Component c = comps.get(i);
            g.gridy = i;
            g.gridx = 0;
            p.add(new JLabel(Names.name(t.circuit, c)), g);
            g.gridx = 1;
            String cur = Names.label(c);
            JTextField f = new JTextField(cur == null ? "" : cur, 14);
            fields.add(f);
            p.add(f, g);
        }
        int r = JOptionPane.showConfirmDialog(t.project.getFrame(), p, Messages.get("menu.bulkLabelsTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        CircuitMutation m = new CircuitMutation(t.circuit);
        boolean any = false;
        for (int i = 0; i < comps.size(); i++) {
            Component c = comps.get(i);
            @SuppressWarnings("unchecked")
            Attribute<Object> a = (Attribute<Object>) c.getAttributeSet().getAttribute("label");
            String now = fields.get(i).getText().trim();
            String cur = Names.label(c);
            if (a != null && !now.equals(cur == null ? "" : cur)) {
                m.set(c, a, now);
                any = true;
            }
        }
        if (any) {
            t.project.doAction(m.toAction(() -> Messages.get("menu.bulkLabelsAction")));
        }
    }
}
