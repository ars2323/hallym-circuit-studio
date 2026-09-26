/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.submit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.diag.Diagnostics;
import kr.ac.hallym.hcs.app.sim.SimControls;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * File › Create Submission…(E-06): 저장부터 하고, 점검 결과와 묶을 파일을 보인 뒤, 확인하면 zip을 고를 곳에 쓴다.
 * 점검에 걸려도 막지 않는다(학생이 보고 판단한다).
 */
public final class SubmissionDialog {
    private SubmissionDialog() {
    }

    /** 점검과 파일 목록 글(테스트·창). */
    static String summary(Submission s) {
        StringBuilder sb = new StringBuilder();
        for (Submission.Check c : s.checks) {
            sb.append(c.ok ? "✓ " : "! ").append(c.text).append('\n');
        }
        sb.append('\n').append(Messages.get("submit.files", s.files.size())).append('\n');
        for (Map.Entry<String, java.io.File> e : s.files.entrySet()) {
            sb.append("  ").append(e.getKey()).append('\n');
        }
        return sb.toString();
    }

    /** 파일 메뉴 항목. */
    public static javax.swing.JMenuItem menuItem(Project proj) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(Messages.get("submit.menu"));
        item.setEnabled(proj != null);
        if (proj != null) {
            item.addActionListener(e -> show(proj));
        }
        return item;
    }

    public static void show(Project proj) {
        if (proj.getLogisimFile().getLoader().getMainFile() == null || proj.isFileDirty()) {
            // 저장한 파일을 묶는다: 먼저 저장(원조 저장 동작, 처음이면 파일 이름을 묻는다)
            if (!ProjectActions.doSave(proj)) {
                return;
            }
        }
        Submission s = Submission.plan(proj.getLogisimFile(), Diagnostics.of(proj).list().size(),
                proj.isFileDirty());
        showPlan(proj, s);
    }

    /** 점검 결과를 보이고, 확인하면 zip을 쓴다(저장은 이미 한 뒤). */
    public static void showPlan(Project proj, Submission s) {
        JTextArea area = new JTextArea(summary(s));
        area.setEditable(false);
        area.setFont(new java.awt.Font(Tokens.UI_FONT, java.awt.Font.PLAIN, Tokens.FONT_UI));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(520, 280));
        JPanel p = new JPanel(new BorderLayout(0, 8));
        JLabel hint = new JLabel(Messages.get("submit.hint"));
        hint.setForeground(Tokens.TEXT_2);
        p.add(hint, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        Object[] options = {Messages.get("submit.create"), Messages.get("submit.cancel")};
        int r = JOptionPane.showOptionDialog(proj.getFrame(), p, Messages.get("submit.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (r != 0) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(Submission.suggestedZip(proj.getLogisimFile()));
        if (fc.showSaveDialog(proj.getFrame()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File zip = fc.getSelectedFile();
        if (!zip.getName().toLowerCase().endsWith(".zip")) {
            zip = new File(zip.getParentFile(), zip.getName() + ".zip");
        }
        try {
            s.write(zip);
            SimControls.notice(proj, Messages.get("submit.done", zip.getName(), s.files.size()));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(proj.getFrame(), Messages.get("submit.failed", ex.getMessage()),
                    Messages.get("submit.title"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
