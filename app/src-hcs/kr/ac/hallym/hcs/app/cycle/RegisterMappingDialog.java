/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.theme.Tokens;

/** 레지스터 대응 창(C-05): $0~$31마다 레지스터 부품을 고른다. 처음 값은 지금 대응(수동 대응 위의 자동 대응). */
public final class RegisterMappingDialog {
    private RegisterMappingDialog() {
    }

    /** 고를 수 있는 것: 없음, 또는 회로 안 레지스터 부품(라벨, 없으면 이름과 자리). */
    static final class Choice {
        final Component register;
        final String text;

        Choice(Component register, String text) {
            this.register = register;
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    static List<Choice> choices(Circuit rf) {
        List<Choice> out = new ArrayList<>();
        out.add(new Choice(null, Messages.get("regfile.none")));
        for (Component r : RegisterFile.registers(rf)) {
            String label = Names.label(r);
            String text = (label != null ? label : Names.title(rf, r)) + "  " + Names.at(r.getLocation());
            out.add(new Choice(r, text));
        }
        return out;
    }

    public static void show(Project proj, Circuit rf) {
        Map<Integer, Component> now = RegisterFile.mapping(proj.getLogisimFile(), rf);
        List<Choice> choices = choices(rf);
        List<JComboBox<Choice>> boxes = new ArrayList<>();
        JPanel grid = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.insets = new java.awt.Insets(2, 2, 2, 8);
        gc.anchor = java.awt.GridBagConstraints.WEST;
        for (int n = 0; n < 32; n++) {
            gc.gridy = n;
            gc.gridx = 0;
            gc.weightx = 0;
            gc.fill = java.awt.GridBagConstraints.NONE;
            grid.add(new JLabel(MipsText.REG[n] + "  (R" + n + ")"), gc);
            JComboBox<Choice> box = new JComboBox<>(choices.toArray(new Choice[0]));
            Component cur = now.get(n);
            for (Choice c : choices) {
                if (c.register == cur) {
                    box.setSelectedItem(c);
                }
            }
            boxes.add(box);
            gc.gridx = 1;
            gc.weightx = 1;
            gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            grid.add(box, gc);
        }
        JPanel p = new JPanel(new BorderLayout(0, 8));
        JLabel help = new JLabel("<html><div style='width:380px'>" + Messages.get("regfile.mappingHelp")
                + "</div></html>");
        help.setForeground(Tokens.TEXT_2);
        p.add(help, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(grid);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setPreferredSize(new java.awt.Dimension(460, 420));
        p.add(sp, BorderLayout.CENTER);
        int r = JOptionPane.showConfirmDialog(proj.getFrame(), p, Messages.get("regfile.mappingTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        Map<Integer, Component> chosen = new HashMap<>();
        for (int n = 0; n < 32; n++) {
            chosen.put(n, ((Choice) boxes.get(n).getSelectedItem()).register);
        }
        proj.doAction(RegisterFile.mapAction(proj.getLogisimFile(), rf, chosen));
    }
}
