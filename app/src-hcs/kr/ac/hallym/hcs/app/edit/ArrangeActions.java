/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.edit;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.sim.SimControls;
import kr.ac.hallym.hcs.app.wiring.WireGuard;

/** 배치 편집의 메뉴 동작(E-01·E-02): 우클릭 메뉴가 부른다. 검사를 거쳐 한 동작으로 적용하고 결과를 고른다. */
public final class ArrangeActions {
    private ArrangeActions() {
    }

    /** 적용하고 결과를 고른다. 검사에 걸리면 알리고 false. */
    static boolean apply(Project proj, Circuit circuit, CircuitMutation m, List<Component> result, String name) {
        if (!WireGuard.run(proj, circuit, m, Collections.<Location>emptyList(), () -> name)) {
            return false;
        }
        Selection sel = proj.getSelection();
        if (sel != null) {
            proj.doAction(SelectionActions.dropAll(sel));
            sel.addAll(result);
        }
        return true;
    }

    /** N개 복제 창. 확인하면 사본을 더한다. */
    public static void duplicateN(Project proj, Circuit circuit, List<Component> comps) {
        SpinnerNumberModel count = new SpinnerNumberModel(3, 1, Arrange.MAX_COPIES, 1);
        JComboBox<String> dir = new JComboBox<>();
        for (Arrange.Dir d : Arrange.Dir.values()) {
            dir.addItem(Messages.get("dir." + d.name().toLowerCase()));
        }
        dir.setSelectedIndex(Arrange.Dir.DOWN.ordinal());
        SpinnerNumberModel spacing = new SpinnerNumberModel(Arrange.defaultSpacing(comps, Arrange.Dir.DOWN), 10, 2000,
                10);
        dir.addActionListener(e -> spacing.setValue(Arrange.defaultSpacing(comps,
                Arrange.Dir.values()[dir.getSelectedIndex()])));
        String first = null;
        for (Component c : comps) {
            String l = Arrange.label(c);
            if (l != null && !l.isEmpty()) {
                first = l;
                break;
            }
        }
        JCheckBox number = new JCheckBox(Messages.get("replicate.number"), first != null);
        number.setEnabled(first != null);
        JLabel example = new JLabel(first == null ? " "
                : Messages.get("replicate.example", first, Arrange.nextLabel(first, 1), Arrange.nextLabel(first, 2)));
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 8);
        g.anchor = GridBagConstraints.WEST;
        Object[][] rows = {{"replicate.count", new JSpinner(count)}, {"replicate.direction", dir},
            {"replicate.spacing", new JSpinner(spacing)}};
        for (int i = 0; i < rows.length; i++) {
            g.gridx = 0;
            g.gridy = i;
            p.add(new JLabel(Messages.get((String) rows[i][0])), g);
            g.gridx = 1;
            p.add((java.awt.Component) rows[i][1], g);
        }
        g.gridx = 0;
        g.gridy = rows.length;
        g.gridwidth = 2;
        p.add(number, g);
        g.gridy++;
        p.add(example, g);
        int r = JOptionPane.showConfirmDialog(proj.getFrame(), p, Messages.get("replicate.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        int n = (Integer) count.getValue();
        List<Component> made = new ArrayList<>();
        CircuitMutation m = Arrange.copies(circuit, comps, n, Arrange.Dir.values()[dir.getSelectedIndex()],
                (Integer) spacing.getValue(), number.isSelected(), made);
        apply(proj, circuit, m, made, Messages.get("replicate.action", n));
    }

    /** 이어진 부품이 있으면 알리고 true(옮기지 않는다). */
    static boolean refuseConnected(Project proj, Circuit circuit, List<Component> comps) {
        List<Component> tied = Arrange.connectedOnes(circuit, comps);
        if (tied.isEmpty()) {
            return false;
        }
        List<String> names = new ArrayList<>();
        for (Component c : tied) {
            if (names.size() == 4) {
                names.add("…");
                break;
            }
            names.add(Names.name(circuit, c));
        }
        SimControls.notice(proj, Messages.get("arrange.connected", String.join(", ", names)));
        return true;
    }

    public static void align(Project proj, Circuit circuit, List<Component> comps, Arrange.Align a) {
        if (refuseConnected(proj, circuit, comps)) {
            return;
        }
        List<Component> out = new ArrayList<>();
        CircuitMutation m = Arrange.align(circuit, comps, a, out);
        if (m == null) {
            SimControls.notice(proj, Messages.get("arrange.nothing"));
            return;
        }
        apply(proj, circuit, m, out, Messages.get("arrange.alignAction"));
    }

    public static void distribute(Project proj, Circuit circuit, List<Component> comps, boolean horizontal) {
        if (refuseConnected(proj, circuit, comps)) {
            return;
        }
        List<Component> out = new ArrayList<>();
        CircuitMutation m = Arrange.distribute(circuit, comps, horizontal, out);
        if (m == null) {
            SimControls.notice(proj, Messages.get("arrange.nothing"));
            return;
        }
        apply(proj, circuit, m, out, Messages.get("arrange.distributeAction"));
    }

    /** 선택 필터: 부품만 또는 선만 남긴다(E-02). */
    public static void filter(Project proj, List<Component> selection, boolean wires) {
        Selection sel = proj.getSelection();
        if (sel == null) {
            return;
        }
        List<Component> keep = new ArrayList<>();
        for (Component c : selection) {
            if (c instanceof Wire == wires) {
                keep.add(c);
            }
        }
        proj.doAction(SelectionActions.dropAll(sel));
        sel.addAll(keep);
    }

    /** 여러 개를 골랐을 때의 메뉴 항목들. */
    public static List<JMenuItem> multiItems(Project proj, Circuit circuit, List<Component> comps,
            List<Component> selection) {
        List<JMenuItem> out = new ArrayList<>();
        out.add(item("menu.duplicateN", () -> duplicateN(proj, circuit, comps)));
        JMenu align = new JMenu(Messages.get("menu.align"));
        for (Arrange.Align a : Arrange.Align.values()) {
            align.add(item("align." + a.name().toLowerCase(), () -> align(proj, circuit, comps, a)));
        }
        out.add(align);
        if (comps.size() >= 3) {
            JMenu dist = new JMenu(Messages.get("menu.distribute"));
            dist.add(item("distribute.h", () -> distribute(proj, circuit, comps, true)));
            dist.add(item("distribute.v", () -> distribute(proj, circuit, comps, false)));
            out.add(dist);
        }
        boolean hasWires = false;
        for (Component c : selection) {
            hasWires |= c instanceof Wire;
        }
        if (hasWires) {
            out.add(item("menu.onlyComponents", () -> filter(proj, selection, false)));
            out.add(item("menu.onlyWires", () -> filter(proj, selection, true)));
        }
        return out;
    }

    static JMenuItem item(String key, Runnable r) {
        JMenuItem it = new JMenuItem(Messages.get(key));
        it.addActionListener(e -> r.run());
        return it;
    }
}
