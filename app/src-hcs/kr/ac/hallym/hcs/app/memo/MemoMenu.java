/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.memo;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collection;
import java.util.Collections;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.labels.TunnelColors;
import kr.ac.hallym.hcs.app.menu.ContextMenus;

/**
 * 우클릭 메뉴의 영역 메모 항목(E-08): 빈 자리에서 Add Area Memo…(고른 부품이 있으면 그 둘레, 없으면 그 자리에 기본
 * 크기), 메모 안에서 Edit Area Memo…, Fit Area Memo to Selection, Delete Area Memo. 창에서 글·색·자리·크기를 고친다.
 */
public final class MemoMenu implements ContextMenus.Provider {
    @Override
    public void contribute(ContextMenus.Target t, JPopupMenu menu) {
        LogisimFile file = t.project.getLogisimFile();
        AreaMemos.Memo here = AreaMemos.at(file, t.circuit, t.point);
        if (t.component == null && here == null) {
            JMenuItem add = new JMenuItem(Messages.get("memo.add"));
            add.addActionListener(e -> add(t.project, t.circuit, t.selection, t.point));
            menu.add(add);
        } else if (t.component == null) {
            final AreaMemos.Memo m = here;
            JMenuItem edit = new JMenuItem(Messages.get("memo.edit"));
            edit.addActionListener(e -> {
                AreaMemos.Memo after = dialog(t.project, m);
                if (after != null && !after.equals(m)) {
                    t.project.doAction(AreaMemos.action(file, t.circuit, m, after));
                }
            });
            menu.add(edit);
            if (!t.selection.isEmpty()) {
                JMenuItem fit = new JMenuItem(Messages.get("memo.fit"));
                fit.addActionListener(e -> t.project.doAction(AreaMemos.action(file, t.circuit, m,
                        new AreaMemos.Memo(AreaMemos.around(t.selection, t.point), m.color, m.text))));
                menu.add(fit);
            }
            JMenuItem del = new JMenuItem(Messages.get("memo.delete"));
            del.addActionListener(e -> t.project.doAction(AreaMemos.action(file, t.circuit, m, null)));
            menu.add(del);
        }
    }

    /** Add Area Memo…: 창에서 확인하면 더한다. */
    public static void add(Project proj, Circuit circuit, Collection<Component> selection,
            com.cburch.logisim.data.Location at) {
        Bounds b = AreaMemos.around(selection == null ? Collections.<Component>emptyList() : selection, at);
        int color = AreaMemos.of(proj.getLogisimFile(), circuit).size() % TunnelColors.PALETTE.length;
        AreaMemos.Memo m = dialog(proj, new AreaMemos.Memo(b, color, ""));
        if (m != null) {
            proj.doAction(AreaMemos.action(proj.getLogisimFile(), circuit, null, m));
        }
    }

    /** 글·색·자리·크기 창. 취소하면 null. */
    static AreaMemos.Memo dialog(Project proj, AreaMemos.Memo start) {
        JTextField text = new JTextField(start.text, 18);
        JComboBox<String> color = new JComboBox<>();
        for (int i = 0; i < TunnelColors.PALETTE.length; i++) {
            color.addItem(Messages.get("tunnel.color." + i));
        }
        color.setSelectedIndex(start.color);
        SpinnerNumberModel x = new SpinnerNumberModel(start.bounds.getX(), -100000, 100000, 10);
        SpinnerNumberModel y = new SpinnerNumberModel(start.bounds.getY(), -100000, 100000, 10);
        SpinnerNumberModel w = new SpinnerNumberModel(start.bounds.getWidth(), 20, 100000, 10);
        SpinnerNumberModel h = new SpinnerNumberModel(start.bounds.getHeight(), 20, 100000, 10);
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 8);
        g.anchor = GridBagConstraints.WEST;
        Object[][] rows = {{"memo.text", text}, {"memo.color", color}, {"memo.x", new JSpinner(x)},
            {"memo.y", new JSpinner(y)}, {"memo.width", new JSpinner(w)}, {"memo.height", new JSpinner(h)}};
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
        JLabel hint = new JLabel(Messages.get("memo.hint"));
        hint.setForeground(kr.ac.hallym.hcs.app.theme.Tokens.TEXT_2);
        p.add(hint, g);
        int r = JOptionPane.showConfirmDialog(proj.getFrame(), p, Messages.get("memo.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return null;
        }
        return new AreaMemos.Memo(Bounds.create(x.getNumber().intValue(), y.getNumber().intValue(),
                w.getNumber().intValue(), h.getNumber().intValue()), color.getSelectedIndex(), text.getText().trim());
    }
}
