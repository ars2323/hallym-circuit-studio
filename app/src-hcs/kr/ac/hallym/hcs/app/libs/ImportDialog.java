/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import java.awt.BorderLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.sim.SimControls;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * File › Import Subcircuits…(P-05): .circ를 고르고, 그 파일의 회로를 체크해 고른 뒤, 계획(딸린 회로·새 이름·건너뛸
 * 부품)을 보이고 확인하면 가져온다.
 */
public final class ImportDialog {
    private ImportDialog() {
    }

    public static JMenuItem menuItem(Project proj) {
        JMenuItem item = new JMenuItem(Messages.get("import.menu"));
        item.setEnabled(proj != null);
        if (proj != null) {
            item.addActionListener(e -> show(proj));
        }
        return item;
    }

    /** 계획 요약 글(테스트·창). */
    public static String summary(CircuitImport.Plan p) {
        StringBuilder sb = new StringBuilder();
        for (Circuit c : p.order) {
            sb.append("  ").append(c.getName());
            if (p.renamed(c)) {
                sb.append("  →  ").append(p.names.get(c));
            }
            sb.append('\n');
        }
        if (!p.skipped.isEmpty()) {
            sb.append('\n').append(Messages.get("import.skipped", p.skipped.size())).append('\n');
            for (String s : p.skipped) {
                sb.append("  ").append(s).append('\n');
            }
        }
        return sb.toString();
    }

    public static void show(Project proj) {
        JFileChooser fc = proj.getLogisimFile().getLoader().createChooser();
        fc.setDialogTitle(Messages.get("import.menu"));
        if (fc.showOpenDialog(proj.getFrame()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        showFor(proj, fc.getSelectedFile());
    }

    /** 파일을 고른 뒤부터(스크린샷·테스트). */
    public static void showFor(Project proj, File f) {
        File mine = proj.getLogisimFile().getLoader().getMainFile();
        if (mine != null && mine.getAbsoluteFile().equals(f.getAbsoluteFile())) {
            JOptionPane.showMessageDialog(proj.getFrame(), Messages.get("import.sameFile"), Messages.get("import.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LogisimFile source;
        try {
            source = new Loader(proj.getFrame()).openLogisimFile(f);
        } catch (Exception | LinkageError e) {
            JOptionPane.showMessageDialog(proj.getFrame(), Messages.get("import.cannotOpen", f.getName()),
                    Messages.get("import.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<JCheckBox> boxes = new ArrayList<>();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (Circuit c : source.getCircuits()) {
            JCheckBox b = new JCheckBox(c.getName(), true);
            b.setOpaque(false);
            boxes.add(b);
            list.add(b);
        }
        list.add(Box.createVerticalGlue());
        JPanel p = new JPanel(new BorderLayout(0, Tokens.SPACE_2));
        JLabel hint = new JLabel(Messages.get("import.choose", f.getName()));
        hint.setForeground(Tokens.TEXT_2);
        p.add(hint, BorderLayout.NORTH);
        JScrollPane sc = new JScrollPane(list);
        sc.setPreferredSize(new java.awt.Dimension(320, 200));
        p.add(sc, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(proj.getFrame(), p, Messages.get("import.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        List<Circuit> chosen = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).isSelected()) {
                chosen.add(source.getCircuits().get(i));
            }
        }
        if (chosen.isEmpty()) {
            return;
        }
        CircuitImport.Plan plan = CircuitImport.plan(proj.getLogisimFile(), source, chosen);
        JTextArea area = new JTextArea(summary(plan));
        area.setEditable(false);
        area.setFont(new java.awt.Font(Tokens.UI_FONT, java.awt.Font.PLAIN, Tokens.FONT_UI));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new java.awt.Dimension(420, 220));
        JPanel q = new JPanel(new BorderLayout(0, Tokens.SPACE_2));
        JLabel h2 = new JLabel(Messages.get("import.planHint", plan.order.size()));
        h2.setForeground(Tokens.TEXT_2);
        q.add(h2, BorderLayout.NORTH);
        q.add(scroll, BorderLayout.CENTER);
        Object[] options = {Messages.get("import.apply"), Messages.get("autoAppearance.cancel")};
        int r = JOptionPane.showOptionDialog(proj.getFrame(), q, Messages.get("import.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (r != 0) {
            return;
        }
        proj.doAction(CircuitImport.action(proj.getLogisimFile(), plan));
        SimControls.notice(proj, Messages.get("import.done", plan.order.size(), f.getName()));
    }
}
